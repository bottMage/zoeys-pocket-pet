"""Author the first true-3D cat prototype. Run inside Blender 4.3+.

blender -b --factory-startup -t 4 --python tools/build-cat-3d.py -- \
    --output artwork/cat3d --render-still

The Android app deliberately does not load this prototype yet. No source PNGs
are sliced, mapped to billboards, or deformed. The principal skin is a connected
closed volume, weighted to one anatomical armature. Eye/ear/mouth geometry is
bound to that same armature. Export contains actual mesh, joints, and animation.
"""

import argparse
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector


parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--render-still", action="store_true")
parser.add_argument("--render-animation", action="store_true")
args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else [])
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)

FPS, CYCLE, STRIDE, STANCE = 60, 72, .72, .66
TAU = math.tau
skin_parts, features = [], []


def linear(rgb):
    return tuple(c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in rgb)


def material(name, rgb, roughness=.55, metallic=0):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*linear(rgb), 1)
    m.use_nodes = True
    shader = m.node_tree.nodes.get("Principled BSDF")
    shader.inputs["Base Color"].default_value = m.diffuse_color
    shader.inputs["Roughness"].default_value = roughness
    shader.inputs["Metallic"].default_value = metallic
    return m


fur = material("Lavender and cream fur", (.68, .43, .89), .68)
fur.node_tree.nodes.get("Principled BSDF").inputs["Sheen Weight"].default_value = .22
color_node = fur.node_tree.nodes.new("ShaderNodeVertexColor")
color_node.layer_name = "FurColor"
fur.node_tree.links.new(color_node.outputs["Color"], fur.node_tree.nodes.get("Principled BSDF").inputs["Base Color"])
cream = material("Warm cream", (1, .89, .71), .68)
pink = material("Rose ear and nose", (.96, .55, .65), .5)
white = material("Eye ivory", (1, .975, .94), .26)
iris = material("Violet iris", (.53, .15, .76), .25)
iris_light = material("Iris lilac", (.83, .42, .95), .27)
dark = material("Deep plum", (.105, .040, .145), .3)
glint = material("Catchlight", (1, 1, 1), .15)


def smooth(obj):
    for poly in obj.data.polygons:
        poly.use_smooth = True
    return obj


def ellipsoid(name, center, radii, collection=None, mat=None):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=20, location=center)
    obj = bpy.context.object
    obj.name = name
    obj.scale = radii
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    smooth(obj)
    if mat:
        obj.data.materials.append(mat)
    if collection is not None:
        collection.append(obj)
    return obj


def capsule(name, a, b, radii):
    a, b = Vector(a), Vector(b)
    obj = ellipsoid(name, (a + b) / 2, (radii[0], radii[1], (b - a).length / 2 + radii[2]), skin_parts)
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = (b - a).to_track_quat("Z", "Y")
    return obj


def tuft(name, base, tip, radius):
    base, tip = Vector(base), Vector(tip)
    bpy.ops.mesh.primitive_cone_add(vertices=20, radius1=radius, radius2=.007,
                                   depth=(tip - base).length, location=(base + tip) / 2)
    obj = bpy.context.object
    obj.name = name
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = (tip - base).to_track_quat("Z", "Y")
    skin_parts.append(obj)
    return obj


def ribbon_curve(name, points, radius, mat=None, collection=None):
    curve = bpy.data.curves.new(name, "CURVE")
    curve.dimensions = "3D"
    curve.resolution_u = 12
    curve.bevel_depth = radius
    curve.bevel_resolution = 4
    curve.use_fill_caps = True
    spline = curve.splines.new("BEZIER")
    spline.bezier_points.add(len(points) - 1)
    for point, location in zip(spline.bezier_points, points):
        point.co = location
        point.handle_left_type = point.handle_right_type = "AUTO"
    obj = bpy.data.objects.new(name, curve)
    bpy.context.collection.objects.link(obj)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.convert(target="MESH")
    obj = bpy.context.object
    obj.select_set(False)
    smooth(obj)
    if mat:
        obj.data.materials.append(mat)
    if collection is not None:
        collection.append(obj)
    return obj


def ear(name, side, inner=False):
    # A closed, gently rounded triangular ear, not a cone/sphere pasted on top.
    front = -1.00 if not inner else -1.046
    coords = [(.16, front, 2.18), (.66, front + .16, 2.29), (.58, front + .18, 2.94)]
    if inner:
        coords = [(.25, front, 2.32), (.56, front + .12, 2.39), (.56, front + .16, 2.76)]
    coords = [(x * side, y, z) for x, y, z in coords]
    back = [(x, y + (.19 if not inner else .014), z) for x, y, z in coords]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(coords + back, [], [(0, 2, 1), (3, 4, 5), (0, 1, 4, 3), (1, 2, 5, 4), (2, 0, 3, 5)])
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    bpy.context.view_layer.objects.active = obj
    bevel = obj.modifiers.new("Rounded ear rim", "BEVEL")
    bevel.width = .055 if not inner else .025
    bevel.segments = 3
    bpy.ops.object.modifier_apply(modifier=bevel.name)
    smooth(obj)
    (features if inner else skin_parts).append(obj)
    if inner:
        obj.data.materials.append(pink)
    return obj


# Sculpt the entire external skin as overlapping volumes, then fuse and smooth.
ellipsoid("Ribcage", (0, -.16, 1.18), (.43, .68, .47), skin_parts)
ellipsoid("Haunch", (0, .48, 1.15), (.47, .42, .48), skin_parts)
ellipsoid("Shoulder chest", (0, -.48, 1.24), (.40, .35, .48), skin_parts)
ellipsoid("Neck", (0, -.66, 1.55), (.34, .35, .48), skin_parts)
ellipsoid("Head", (0, -.86, 2.02), (.64, .51, .58), skin_parts)
ellipsoid("Lower cheek", (0, -.98, 1.76), (.58, .40, .29), skin_parts)
ellipsoid("Muzzle left", (-.16, -1.275, 1.73), (.25, .18, .19), skin_parts)
ellipsoid("Muzzle right", (.16, -1.275, 1.73), (.25, .18, .19), skin_parts)
ellipsoid("Chin", (0, -1.20, 1.59), (.28, .20, .13), skin_parts)
ellipsoid("Soft chest bib", (0, -.82, 1.28), (.32, .21, .37), skin_parts)
for side in (-1, 1):
    ear("Outer ear", side)
    ear("Inner ear", side, True)
    for level in range(3):
        tuft("Swept cheek fur", (side * .44, -.92, 1.72 + .11 * level),
             (side * (.70 + .015 * level), -.85, 1.60 + .11 * level), .13)
    tuft("Chest fur", (side * .14, -.88, 1.31), (side * .27, -.91, 1.05), .14)
    ellipsoid("Shoulder", (side * .30, -.49, 1.10), (.23, .29, .33), skin_parts)
    capsule("Front upper", (side * .30, -.49, 1.12), (side * .31, -.36, .64), (.16, .17, .12))
    capsule("Front lower", (side * .31, -.36, .64), (side * .31, -.70, .21), (.13, .13, .10))
    ellipsoid("Front paw", (side * .31, -.81, .115), (.20, .25, .115), skin_parts)
    ellipsoid("Rear thigh", (side * .31, .43, .94), (.28, .31, .40), skin_parts)
    capsule("Rear thigh lower", (side * .31, .45, 1.07), (side * .31, .21, .68), (.20, .21, .13))
    capsule("Rear shin", (side * .31, .21, .68), (side * .31, .59, .28), (.13, .14, .11))
    capsule("Rear hock", (side * .31, .59, .28), (side * .31, .46, .13), (.12, .13, .07))
    ellipsoid("Rear paw", (side * .31, .43, .115), (.20, .24, .115), skin_parts)
    for paw_y in (-.84, .40):
        for toe in (-1, 0, 1):
            ellipsoid("Rounded toe", (side * .31 + toe * .085, paw_y - .13, .095), (.068, .105, .080), skin_parts)
tuft("Forelock curl", (.02, -.91, 2.43), (.19, -.91, 2.68), .17)
tuft("Forelock side", (-.15, -.92, 2.42), (-.05, -.99, 2.60), .13)

tail_points = [(0, .66, 1.25), (.035, 1.01, 1.35), (.075, 1.38, 1.65),
               (.105, 1.56, 2.05), (.105, 1.52, 2.42), (.06, 1.26, 2.67), (0, .98, 2.60)]
tail_obj = ribbon_curve("Fluffy curled tail", tail_points, .19, collection=skin_parts)
ellipsoid("Tail cream tip", tail_points[-1], (.22, .21, .20), skin_parts)

bpy.ops.object.select_all(action="DESELECT")
for obj in skin_parts:
    obj.select_set(True)
bpy.context.view_layer.objects.active = skin_parts[0]
bpy.ops.object.join()
skin = bpy.context.object
skin.name = "Cat_Continuous_Skin"
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
remesh = skin.modifiers.new("Fuse full body into a continuous surface", "REMESH")
remesh.mode = "VOXEL"
remesh.voxel_size = .027
remesh.use_smooth_shade = True
bpy.ops.object.modifier_apply(modifier=remesh.name)
relax = skin.modifiers.new("Relax sculpted surface", "SMOOTH")
relax.factor = .55
relax.iterations = 5
bpy.ops.object.modifier_apply(modifier=relax.name)
decimate = skin.modifiers.new("Game mesh budget", "DECIMATE")
decimate.ratio = min(1, 31000 / max(1, len(skin.data.polygons) * 2))
bpy.ops.object.modifier_apply(modifier=decimate.name)
smooth(skin)
skin.data.materials.clear()
skin.data.materials.append(fur)


def segment_distance(p, a, b):
    a, b = Vector(a), Vector(b)
    delta = b - a
    t = max(0, min(1, (p - a).dot(delta) / delta.length_squared))
    return (p - a - delta * t).length, t


def nearest_tail(p):
    distances = [(segment_distance(p, a, b)[0], i, segment_distance(p, a, b)[1])
                 for i, (a, b) in enumerate(zip(tail_points, tail_points[1:]))]
    return min(distances)


def blend(a, b, t):
    return tuple(x * (1 - t) + y * t for x, y in zip(a, b))


def smoothstep(a, b, value):
    t = max(0, min(1, (value - a) / (b - a)))
    return t * t * (3 - 2 * t)


# Vertex colour is anchored in rest space and follows the skinning, not a billboard.
colors = skin.data.color_attributes.new(name="FurColor", type="FLOAT_COLOR", domain="POINT")
for vertex in skin.data.vertices:
    p = vertex.co
    x, y, z = p
    rgb = (.68, .43, .89)
    if z < 1.63 and y > -.58:
        rgb = blend(rgb, (.52, .30, .74), .65 * (1 - smoothstep(.08, .23, abs(math.sin((y + .3) * 11 + z * 2.2)))))
    muzzle_mask = (1 - smoothstep(1.83, 1.94, z)) * (1 - smoothstep(-1.21, -1.09, y))
    bib_mask = (1 - smoothstep(.23, .35, abs(x))) * (1 - smoothstep(-.80, -.69, y)) * (1 - smoothstep(1.45, 1.61, z))
    belly_mask = (1 - smoothstep(.88, 1.00, z)) * (1 - smoothstep(.22, .34, abs(x)))
    paw_mask = 1 - smoothstep(.13, .25, z)
    rgb = blend(rgb, (1, .90, .74), max(muzzle_mask, bib_mask, belly_mask * .92))
    rgb = blend(rgb, (.92, .76, 1), paw_mask)
    if y > .78 and z > 1.19:
        distance, index, t = nearest_tail(p)
        if distance < .30:
            along = index + t
            band = smoothstep(-.2, .25, math.cos(along * math.pi * 1.72))
            rgb = blend((.70, .48, .87), (1, .90, .74), band)
            if along > 5.45 or (z > 2.50 and y < 1.36):
                rgb = (1, .90, .74)
    colors.data[vertex.index].color = (*linear(rgb), 1)

# Facial geometry sits on the head and deforms with the same skeleton.
for side in (-1, 1):
    cx = side * .285
    ellipsoid("Eye soft liner", (cx, -1.241, 2.035), (.253, .078, .316), features, dark)
    ellipsoid("Eye white", (cx, -1.265, 2.035), (.234, .078, .288), features, white)
    ellipsoid("Violet iris", (cx - side * .018, -1.327, 2.035), (.166, .031, .234), features, iris)
    ellipsoid("Lilac iris lower", (cx - side * .018, -1.343, 1.957), (.128, .021, .133), features, iris_light)
    ellipsoid("Oval pupil", (cx - side * .024, -1.366, 2.053), (.080, .014, .160), features, dark)
    ellipsoid("Large eye catchlight", (cx - .050, -1.389, 2.158), (.055, .012, .073), features, glint)
    ellipsoid("Small eye catchlight", (cx + .059, -1.379, 1.965), (.025, .012, .030), features, glint)

# Soft triangular pink nose, rather than a spherical button.
mesh = bpy.data.meshes.new("Nose mesh")
mesh.from_pydata([(-.078, -1.443, 1.799), (.078, -1.443, 1.799), (0, -1.468, 1.719),
                 (0, -1.386, 1.768)], [], [(0, 2, 1), (0, 1, 3), (1, 2, 3), (2, 0, 3)])
mesh.update()
nose = bpy.data.objects.new("Pink triangular nose", mesh)
bpy.context.collection.objects.link(nose)
nose.data.materials.append(pink)
features.append(nose)
bpy.context.view_layer.objects.active = nose
bevel = nose.modifiers.new("Soft nose edges", "BEVEL")
bevel.width, bevel.segments = .018, 3
bpy.ops.object.modifier_apply(modifier=bevel.name)
smooth(nose)
ribbon_curve("Mouth center", [(0, -1.466, 1.736), (0, -1.453, 1.681)], .009, dark, features)
for side in (-1, 1):
    ribbon_curve("Cat smile", [(0, -1.453, 1.681), (side * .077, -1.455, 1.646),
                               (side * .158, -1.427, 1.674)], .008, dark, features)
    for stripe in range(2):
        ribbon_curve("Whisker freckle", [(side * .34, -1.342, 1.736 - stripe * .065),
                                         (side * .45, -1.276, 1.740 - stripe * .072)], .006, dark, features)

# A single anatomical skeleton: pelvis -> spine -> chest -> neck -> head.
bpy.ops.object.select_all(action="DESELECT")
armature = bpy.data.armatures.new("Cat anatomical skeleton")
rig = bpy.data.objects.new("Cat_Rig", armature)
bpy.context.collection.objects.link(rig)
bpy.context.view_layer.objects.active = rig
rig.select_set(True)
bpy.ops.object.mode_set(mode="EDIT")
bone_segments = {}


def bone(name, a, b, parent=None, deform=True):
    value = armature.edit_bones.new(name)
    value.head, value.tail = a, b
    value.use_deform = deform
    if parent:
        value.parent = armature.edit_bones[parent]
    bone_segments[name] = (Vector(a), Vector(b))
    return value


bone("root", (0, 0, .10), (0, 0, .35))
bone("pelvis", (0, .48, 1.16), (0, .14, 1.20), "root")
bone("spine", (0, .14, 1.20), (0, -.20, 1.24), "pelvis")
bone("chest", (0, -.20, 1.24), (0, -.51, 1.30), "spine")
bone("neck", (0, -.51, 1.30), (0, -.78, 1.70), "chest")
bone("head", (0, -.78, 1.70), (0, -.87, 2.21), "neck")
for index, (a, b) in enumerate(zip(tail_points, tail_points[1:])):
    bone(f"tail_{index}", a, b, "pelvis" if index == 0 else f"tail_{index - 1}")

leg_specs = {}
for side, suffix in ((-1, "L"), (1, "R")):
    for kind in ("front", "rear"):
        if kind == "front":
            hip, knee, ankle, toe = (side * .31, -.49, 1.17), (side * .31, -.36, .64), (side * .31, -.70, .21), (side * .31, -.85, .11)
        else:
            hip, knee, ankle, toe = (side * .31, .45, 1.15), (side * .31, .21, .68), (side * .31, .59, .28), (side * .31, .43, .11)
        prefix = f"{kind}_{suffix}"
        if kind == "front":
            bone(prefix + "_scapula", (side * .24, -.33, 1.33), hip, "chest")
        bone(prefix + "_upper", hip, knee, prefix + "_scapula" if kind == "front" else "pelvis")
        bone(prefix + "_lower", knee, ankle, prefix + "_upper")
        bone(prefix + "_paw", ankle, toe, prefix + "_lower")
        leg_specs[prefix] = {"hip": Vector(hip), "knee": Vector(knee), "ankle": Vector(ankle), "toe": Vector(toe),
                             "phase": {"rear_L": 0, "front_L": .25, "rear_R": .5, "front_R": .75}[prefix]}
bpy.ops.object.mode_set(mode="OBJECT")


def bind(obj, weights):
    groups = {name: obj.vertex_groups.new(name=name) for name in armature.bones.keys() if armature.bones[name].use_deform}
    for vertex in obj.data.vertices:
        influence = weights(vertex.co)
        # Match the four-influence GPU skinning representation in exported glTF.
        influence = dict(sorted(((name, value) for name, value in influence.items() if value > .0001),
                                key=lambda item: item[1], reverse=True)[:4])
        total = sum(influence.values())
        for name, weight in influence.items():
            groups[name].add([vertex.index], weight / total, "REPLACE")
    mod = obj.modifiers.new("One full-body skeleton", "ARMATURE")
    mod.object = rig
    obj.parent = rig


def near_weights(p, names, exponent=4):
    distances = [(segment_distance(p, *bone_segments[name])[0], name) for name in names]
    best = sorted(distances)[:3]
    return {name: 1 / max(.07, distance) ** exponent for distance, name in best}


def skin_weights(p):
    x, y, z = p
    if y > .83 and z > 1.17:
        distance, index, _ = nearest_tail(p)
        if distance < .31:
            return near_weights(p, [f"tail_{i}" for i in range(6)])
    body_bones = ["pelvis", "spine", "chest", "neck", "head"]
    if y < -.10 and z < 1.60 and abs(x) > .14:
        body_bones.append("front_L_scapula" if x < 0 else "front_R_scapula")
    body = near_weights(p, body_bones)
    if z > 1.75 or (y < -1.04 and z > 1.46):
        return {"head": 1}
    if z < 1.26 and abs(x) > .10:
        kind = "front" if y < -.23 else "rear"
        suffix = "L" if x < 0 else "R"
        prefix = f"{kind}_{suffix}"
        leg = near_weights(p, [prefix + "_upper", prefix + "_lower", prefix + "_paw"])
        ankle_z = leg_specs[prefix]["ankle"].z
        paw = 1 - smoothstep(ankle_z - .035, ankle_z + .09, z)
        leg_total = sum(leg.values())
        leg = {name: value / leg_total * (1 - paw) for name, value in leg.items()}
        leg[prefix + "_paw"] = leg.get(prefix + "_paw", 0) + paw
        region = 1 - smoothstep(-.37, -.18, y) if kind == "front" else smoothstep(-.03, .22, y)
        leg_share = (1 - smoothstep(.68, 1.26, z)) * smoothstep(.10, .27, abs(x)) * region
        body_share = 1 - leg_share
        body_total = sum(body.values())
        result = {name: value * (1 - body_share) for name, value in leg.items()}
        for name, value in body.items():
            result[name] = result.get(name, 0) + value / body_total * body_share
        return result
    return body


bind(skin, skin_weights)
for obj in features:
    # Facial details are real geometry, rigidly attached to the head bone.
    world = obj.matrix_world.copy()
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    bind(obj, lambda p: {"head": 1})

# IK targets are world-space paw contacts. The two leg chains respond to the
# moving pelvis/chest, rather than independently swinging cutout legs.
targets = {}
for prefix, spec in leg_specs.items():
    target = bpy.data.objects.new(prefix + "_contact", None)
    bpy.context.collection.objects.link(target)
    target.location = spec["ankle"]
    target.rotation_mode = "QUATERNION"
    target.rotation_quaternion = armature.bones[prefix + "_paw"].matrix_local.to_quaternion()
    ik = rig.pose.bones[prefix + "_lower"].constraints.new("IK")
    ik.target = target
    ik.chain_count = 2
    ik.use_stretch = False
    ik.iterations = 64
    # Resting chain is non-collinear; using its rest bend avoids pole-angle snaps.
    orient = rig.pose.bones[prefix + "_paw"].constraints.new("COPY_ROTATION")
    orient.target = target
    orient.target_space = orient.owner_space = "WORLD"
    targets[prefix] = target


def foot_path(phase):
    p = phase % 1
    span = STRIDE * STANCE
    if p < STANCE:
        return STRIDE * (p - STANCE / 2), 0
    t = (p - STANCE) / (1 - STANCE)
    hermite = 3 * t * t - 2 * t * t * t
    offset = span / 2 - span * hermite + STRIDE * (1 - STANCE) * t * (1 - t) * (1 - 2 * t)
    return offset, .135 * math.sin(math.pi * t) ** 2


# Bake an authored coupled cycle at 60 Hz. Body motion is deliberately subtle:
# quadrupeds carry the torso between supports, not bounce the whole animal.
for frame in range(1, CYCLE + 2):
    u = (frame - 1) / CYCLE
    phase = TAU * u
    root_distance = -STRIDE * u
    rig.location = (0, root_distance, 0)
    rig.keyframe_insert("location", frame=frame)
    for name in ("root", "pelvis", "spine", "chest", "neck", "head"):
        pb = rig.pose.bones[name]
        pb.rotation_mode = "XYZ"
        pb.rotation_euler = (0, 0, 0)
        pb.location = (0, 0, 0)
    rig.pose.bones["root"].location = (.022 * math.sin(phase), .013 * math.cos(phase * 2), 0)
    # Bone local axes differ from world axes; these rotations are small and
    # anatomically distributed across pelvis/spine/chest, not one torso hinge.
    rig.pose.bones["pelvis"].rotation_euler = (.030 * math.sin(phase + .4), .035 * math.sin(phase), .024 * math.cos(phase))
    rig.pose.bones["spine"].rotation_euler = (-.022 * math.sin(phase + .4), -.018 * math.sin(phase), -.018 * math.cos(phase))
    rig.pose.bones["chest"].rotation_euler = (.016 * math.sin(phase - .4), -.025 * math.sin(phase), -.025 * math.cos(phase))
    rig.pose.bones["neck"].rotation_euler.x = .013 * math.sin(phase * 2 + .3)
    rig.pose.bones["head"].rotation_euler.x = -.012 * math.sin(phase * 2 + .3)
    for name in ("root", "pelvis", "spine", "chest", "neck", "head"):
        rig.pose.bones[name].keyframe_insert("rotation_euler", frame=frame)
        rig.pose.bones[name].keyframe_insert("location", frame=frame)
    for index in range(6):
        pb = rig.pose.bones[f"tail_{index}"]
        pb.rotation_mode = "XYZ"
        pb.rotation_euler = (.012 * math.sin(phase - index * .45), .022 * math.sin(phase - index * .5), .014 * math.sin(phase - index * .40))
        pb.keyframe_insert("rotation_euler", frame=frame)
    for prefix, spec in leg_specs.items():
        if prefix.startswith("front"):
            scapula = rig.pose.bones[prefix + "_scapula"]
            scapula.rotation_mode = "XYZ"
            scapula.rotation_euler = (.065 * math.sin(phase + spec["phase"] * TAU), 0,
                                      .018 * math.sin(phase + spec["phase"] * TAU))
            scapula.keyframe_insert("rotation_euler", frame=frame)
        offset, lift = foot_path(u + spec["phase"])
        target = targets[prefix]
        target.location = spec["ankle"] + Vector((0, root_distance + offset, lift))
        target.keyframe_insert("location", frame=frame)

# Bake constraints to deform bones, then remove controller objects from the
# exported character. In the app the animation requires only normal GPU skinning.
bpy.ops.object.select_all(action="DESELECT")
rig.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode="POSE")
bpy.ops.pose.select_all(action="SELECT")
bpy.ops.nla.bake(frame_start=1, frame_end=CYCLE + 1, step=1, only_selected=False,
                 visual_keying=True, clear_constraints=True, clear_parents=False,
                 use_current_action=True, bake_types={"POSE"})
bpy.ops.object.mode_set(mode="OBJECT")
rig.animation_data.action.name = "Walk_full_body_root_motion"
for fcurve in rig.animation_data.action.fcurves:
    fcurve.extrapolation = "LINEAR"
    for key in fcurve.keyframe_points:
        key.interpolation = "LINEAR"
    repeat = fcurve.modifiers.new("CYCLES")
    if fcurve.data_path == "location" and fcurve.array_index == 1:
        repeat.mode_before = repeat.mode_after = "REPEAT_OFFSET"
    else:
        repeat.mode_before = repeat.mode_after = "REPEAT"
for obj in list(bpy.data.objects):
    if obj.type == "EMPTY":
        bpy.data.objects.remove(obj, do_unlink=True)

scene = bpy.context.scene
scene.render.fps = FPS
scene.frame_start, scene.frame_end = 1, CYCLE + 1
scene.frame_set(1)
bpy.ops.object.select_all(action="DESELECT")
rig.select_set(True)
skin.select_set(True)
for obj in features:
    obj.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.export_scene.gltf(filepath=str(out / "cat-prototype.glb"), export_format="GLB",
                          use_selection=True, export_animations=True,
                          export_frame_range=True, export_force_sampling=True,
                          export_animation_mode="ACTIVE_ACTIONS", export_skins=True,
                          export_morph=False, export_yup=True)

# Presentation: a soft studio/ground scene. Camera follows root motion, so the
# paws can be judged against fixed floor markings rather than a treadmill.
floor_material = material("Soft mint ground", (.70, .82, .77), .90)
bpy.ops.mesh.primitive_plane_add(size=200)
floor = bpy.context.object
floor.name = "Preview_ground_not_part_of_character"
floor.data.materials.append(floor_material)
marker_material = material("Ground contact markers", (.53, .67, .60), 1)
for y in range(-7, 5):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, y * .60, .001))
    marker = bpy.context.object
    marker.name = "Fixed ground marker"
    marker.scale = (5, .009, .001)
    marker.data.materials.append(marker_material)

world = bpy.data.worlds.new("Lavender studio")
scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (*linear((.72, .73, .84)), 1)
world.node_tree.nodes["Background"].inputs["Strength"].default_value = .45


def area_light(name, location, power, size, rgb):
    data = bpy.data.lights.new(name, "AREA")
    obj = bpy.data.objects.new(name, data)
    bpy.context.collection.objects.link(obj)
    obj.location = location
    obj.rotation_euler = (Vector((0, 0, 1.2)) - obj.location).to_track_quat("-Z", "Y").to_euler()
    data.energy, data.shape, data.size, data.color = power, "DISK", size, rgb
    return obj


key = area_light("Large soft key", (-3, -4, 6), 750, 5, (1, .91, .84))
fill = area_light("Cool fill", (4, -1, 4), 480, 4, (.85, .87, 1))
rim = area_light("Tail rim", (0, 4, 5), 650, 3, (1, .85, .92))
camera_data = bpy.data.cameras.new("Preview camera")
camera = bpy.data.objects.new("Preview camera", camera_data)
bpy.context.collection.objects.link(camera)
camera.location = (4.3, -6.6, 3.05)
look_at = Vector((0, .22, 1.46))
camera.rotation_euler = (look_at - camera.location).to_track_quat("-Z", "Y").to_euler()
camera_data.type, camera_data.ortho_scale = "ORTHO", 4.4
scene.camera = camera
camera.parent = rig
for light in (key, fill, rim):
    light.parent = rig
scene.render.engine = "BLENDER_EEVEE_NEXT"
scene.eevee.taa_render_samples = 24
scene.render.resolution_x, scene.render.resolution_y = 960, 720
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = "PNG"
scene.render.film_transparent = False
scene.view_settings.view_transform = "AgX"
scene.view_settings.look = "AgX - Medium High Contrast"
scene.frame_end = 360
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(out / "cat-prototype.blend"))

# These are structural checks, not a claim that the user will approve the look.
adjacency = [[] for _ in skin.data.vertices]
for edge in skin.data.edges:
    a, b = edge.vertices
    adjacency[a].append(b)
    adjacency[b].append(a)
remaining = set(range(len(adjacency)))
components = []
while remaining:
    stack = [remaining.pop()]
    count = 1
    while stack:
        for linked in adjacency[stack.pop()]:
            if linked in remaining:
                remaining.remove(linked)
                stack.append(linked)
                count += 1
    components.append(count)
assert len(components) == 1, f"External skin disconnected: {components}"
assert all(vertex.groups for vertex in skin.data.vertices), "Unweighted skin vertices"
report = {"prototype_only": True, "fps": FPS, "cycle_seconds": CYCLE / FPS,
          "stride_world_units": STRIDE, "skin_connected_components": len(components),
          "skin_vertices": len(skin.data.vertices), "skin_triangles": sum(len(p.vertices) - 2 for p in skin.data.polygons),
          "skeleton_bones": len(armature.bones), "animation": rig.animation_data.action.name}
(out / "validation.json").write_text(json.dumps(report, indent=2) + "\n")
print("CAT_PROTOTYPE", json.dumps(report), flush=True)
if args.render_still:
    scene.frame_set(12)
    scene.render.filepath = str(out / "cat-preview.png")
    bpy.ops.render.render(write_still=True)
if args.render_animation:
    scene.render.filepath = str(out / "preview-frames" / "frame-")
    bpy.ops.render.render(animation=True)
