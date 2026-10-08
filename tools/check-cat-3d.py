"""Validate the saved 3D rig and the export; run inside Blender, not Android.

blender -b artwork/cat3d/cat-prototype.blend --python tools/check-cat-3d.py
"""

import json
import math
import struct
from pathlib import Path

import bpy
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

rig = bpy.data.objects["Cat_Rig"]
skin = bpy.data.objects["Cat_Continuous_Skin"]
scene = bpy.context.scene
asset_folder = Path(bpy.data.filepath).parent
names = ("rear_L", "front_L", "rear_R", "front_R")
cycle_frames = int(rig.get("gait_cycle_frames", 72))
clip_frames = int(rig.get("clip_frames", 72))
stride = float(rig.get("gait_stride", .72))
stance = float(rig.get("gait_stance", .66))
clip_cycles = int(rig.get("clip_cycles", 1))
phase_map = json.loads(rig.get("gait_phases", '{"rear_L":0,"front_L":0.25,"rear_R":0.5,"front_R":0.75}'))
phases = [phase_map[name] for name in names]
z_contact = {"front_L": .11, "front_R": .11, "rear_L": .11, "rear_R": .11}
max_ground_error = 0
max_paw_slide = 0
last_support = {}
max_step_jump = 0
previous_points = {}
positions = {}
contact_vertices = {}
vertex_last_support = {}
max_skin_paw_slide = 0
min_skin_paw_z, max_skin_paw_z = 100, -100
previous_joint_rotations = {}
max_joint_rotation_step = 0
framing_min, framing_max = Vector((10, 10)), Vector((-10, -10))
root_x_values, head_y_values = [], []
for prefix in names:
    group = skin.vertex_groups[prefix + "_paw"].index
    candidates = [vertex for vertex in skin.data.vertices
                  if any(item.group == group and item.weight > .999 for item in vertex.groups)]
    assert candidates, f"No rigid supporting pad vertices for {prefix}"
    contact_vertices[prefix] = min(candidates, key=lambda vertex: vertex.co.z).index

for frame in range(1, clip_frames + 2):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    evaluated_skin = skin.evaluated_get(bpy.context.evaluated_depsgraph_get())
    u = -rig.location.y / stride
    root_x_values.append(rig.pose.bones["root"].location.x)
    head_y_values.append(rig.pose.bones["head"].rotation_euler.y)
    for bone in rig.pose.bones:
        rotation = bone.matrix.to_quaternion()
        if bone.name in previous_joint_rotations:
            angle = previous_joint_rotations[bone.name].rotation_difference(rotation).angle
            max_joint_rotation_step = max(max_joint_rotation_step, min(angle, math.tau - angle))
        previous_joint_rotations[bone.name] = rotation
    if frame % 12 == 1 or frame == clip_frames + 1:
        for obj in bpy.data.objects:
            if obj.type != "MESH" or not obj.modifiers.get("One full-body skeleton"):
                continue
            evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
            for corner in evaluated.bound_box:
                projected = world_to_camera_view(scene, scene.camera, evaluated.matrix_world @ Vector(corner))
                for axis in (0, 1):
                    framing_min[axis] = min(framing_min[axis], projected[axis])
                    framing_max[axis] = max(framing_max[axis], projected[axis])
    positions[frame] = {}
    for prefix, phase in zip(names, phases):
        p = (u + phase) % 1
        paw = rig.matrix_world @ rig.pose.bones[prefix + "_paw"].tail
        positions[frame][prefix] = paw.copy()
        if prefix in previous_points:
            max_step_jump = max(max_step_jump, (paw - previous_points[prefix]).length)
        previous_points[prefix] = paw.copy()
        if p < stance:
            max_ground_error = max(max_ground_error, abs(paw.z - z_contact[prefix]))
            if prefix in last_support:
                max_paw_slide = max(max_paw_slide, (paw - last_support[prefix]).length)
            last_support[prefix] = paw.copy()
            skin_pad = evaluated_skin.matrix_world @ evaluated_skin.data.vertices[contact_vertices[prefix]].co
            min_skin_paw_z = min(min_skin_paw_z, skin_pad.z)
            max_skin_paw_z = max(max_skin_paw_z, skin_pad.z)
            if prefix in vertex_last_support:
                max_skin_paw_slide = max(max_skin_paw_slide, (skin_pad - vertex_last_support[prefix]).length)
            vertex_last_support[prefix] = skin_pad.copy()
        else:
            last_support.pop(prefix, None)
            vertex_last_support.pop(prefix, None)
        assert all(math.isfinite(c) for c in paw), "Non-finite animated paw position"

loop_error = max((positions[clip_frames + 1][name] - positions[1][name] + Vector((0, stride * clip_cycles, 0))).length for name in names)
assert max_ground_error < .007, f"Supporting paw left ground: {max_ground_error}"
assert max_paw_slide < .007, f"Planted paw slid in world space: {max_paw_slide}"
assert max_step_jump < 5 * stride / cycle_frames, f"Discontinuous paw movement: {max_step_jump}"
assert max_joint_rotation_step < .25, f"Joint snaps between frames: {max_joint_rotation_step}"
assert loop_error < .007, f"Walk clip does not loop after root-motion extraction: {loop_error}"
assert max_skin_paw_slide < .007, f"Rendered pad vertices slide despite valid bone contact: {max_skin_paw_slide}"
assert -.005 < min_skin_paw_z and max_skin_paw_z < .04, f"Rendered pads miss ground: {min_skin_paw_z}, {max_skin_paw_z}"
assert all(len(vertex.groups) <= 4 for vertex in skin.data.vertices), "Export/preview weight mismatch"
assert all(abs(sum(group.weight for group in vertex.groups) - 1) < .0001 for vertex in skin.data.vertices), "Unnormalized weights"
assert all(0 < value < 1 for value in (*framing_min, *framing_max)), f"Character clips out of preview: {framing_min}, {framing_max}"
if rig.get("prototype_revision", 1) >= 2:
    head_group = skin.vertex_groups["head"].index
    tip_vertices = [vertex for vertex in skin.data.vertices if vertex.co.y > .55 and vertex.co.z > 2.48]
    assert tip_vertices, "Tail tip not found"
    assert all(not any(item.group == head_group and item.weight > .001 for item in vertex.groups)
               for vertex in tip_vertices), "Curled tail tip accidentally bound to the head"

raw = (asset_folder / "cat-prototype.glb").read_bytes()
magic, version, length = struct.unpack_from("<III", raw)
assert magic == 0x46546C67 and version == 2 and length == len(raw), "Invalid GLB"
json_length, json_type = struct.unpack_from("<II", raw, 12)
assert json_type == 0x4E4F534A
gltf = json.loads(raw[20:20 + json_length])
assert len(gltf.get("skins", [])) >= 1, "Export is not skinned"
assert gltf.get("animations"), "Export has no actual animation"
assert not gltf.get("images"), "Prototype unexpectedly depends on image cutouts"
animated_nodes = {channel["target"]["node"] for animation in gltf["animations"] for channel in animation["channels"]}
node_names = {gltf["nodes"][node].get("name", "") for node in animated_nodes}
assert {"pelvis", "spine", "chest", "neck", "head"} <= node_names, "Torso is not animated"
assert all(f"tail_{index}" in node_names for index in range(6)), "Tail missing from full-body clip"
assert {"front_L_scapula", "front_R_scapula"} <= node_names, "Shoulders missing from full-body clip"
body_node = next(node for node in gltf["nodes"] if node.get("name") == "Cat_Continuous_Skin")
assert all("COLOR_0" in primitive["attributes"] for primitive in gltf["meshes"][body_node["mesh"]]["primitives"]), "Fur colours lost in export"
body_material = gltf["materials"][gltf["meshes"][body_node["mesh"]]["primitives"][0]["material"]]
assert body_material.get("pbrMetallicRoughness", {}).get("baseColorFactor", [1, 1, 1, 1]) == [1, 1, 1, 1], "Vertex colour multiplied by a second fur tint"

report = json.loads((asset_folder / "validation.json").read_text())
report.update({"max_support_bone_ground_error": round(max_ground_error, 6),
               "max_support_bone_slide_per_frame": round(max_paw_slide, 6),
               "max_paw_travel_per_frame": round(max_step_jump, 6),
               "root_compensated_loop_error": round(loop_error, 6),
               "max_rendered_pad_slide_per_frame": round(max_skin_paw_slide, 6),
               "supporting_pad_height_range": [round(min_skin_paw_z, 6), round(max_skin_paw_z, 6)],
               "max_joint_rotation_step_degrees": round(math.degrees(max_joint_rotation_step), 4),
               "body_lateral_motion_span": round(max(root_x_values) - min(root_x_values), 5),
               "head_look_span_degrees": round(math.degrees(max(head_y_values) - min(head_y_values)), 4),
               "preview_character_bounds": [list(framing_min), list(framing_max)],
               "export_has_skin": True, "export_has_full_body_animation": True,
               "export_image_textures": len(gltf.get("images", [])),
               "note": "Bone-contact/structure checks, not a claim of user-approved visual quality or device performance."})
(asset_folder / "validation.json").write_text(json.dumps(report, indent=2) + "\n")
print("PASS_CAT_3D", json.dumps(report), flush=True)
