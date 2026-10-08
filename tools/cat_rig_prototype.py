import bpy
import math
from pathlib import Path

from mathutils import Vector


ROOT = Path('/workspace/zoeys-pocket-pet')
SOURCE = ROOT / 'app/src/main/res/drawable-nodpi/walk_cat_0.png'
OUTPUT = Path('/tmp/zoey-cat-rig')
OUTPUT.mkdir(parents=True, exist_ok=True)


def clear_scene():
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    for datablocks in (bpy.data.meshes, bpy.data.curves, bpy.data.materials, bpy.data.cameras, bpy.data.lights):
        for block in list(datablocks):
            if block.users == 0:
                datablocks.remove(block)


def make_textured_mesh():
    verts = []
    faces = []
    uvs = []
    steps = 32
    for row in range(steps + 1):
        y = -1.0 + 2.0 * row / steps
        v = row / steps
        for col in range(steps + 1):
            x = -1.0 + 2.0 * col / steps
            u = col / steps
            verts.append((x, y, 0.0))
            uvs.append((u, v))
    for row in range(steps):
        for col in range(steps):
            a = row * (steps + 1) + col
            b = a + 1
            c = a + steps + 2
            d = a + steps + 1
            faces.append((a, b, c, d))

    mesh = bpy.data.meshes.new('CatRigMesh')
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    uv_layer = mesh.uv_layers.new(name='UVMap')
    for poly in mesh.polygons:
        for loop_index in poly.loop_indices:
            vertex_index = mesh.loops[loop_index].vertex_index
            uv_layer.data[loop_index].uv = uvs[vertex_index]

    image = bpy.data.images.load(str(SOURCE), check_existing=True)
    material = bpy.data.materials.new('CatArtwork')
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()
    output = nodes.new('ShaderNodeOutputMaterial')
    shader = nodes.new('ShaderNodeBsdfPrincipled')
    texture = nodes.new('ShaderNodeTexImage')
    texture.image = image
    texture.interpolation = 'Linear'
    shader.inputs['Roughness'].default_value = 1.0
    links.new(texture.outputs['Color'], shader.inputs['Base Color'])
    links.new(texture.outputs['Alpha'], shader.inputs['Alpha'])
    links.new(shader.outputs['BSDF'], output.inputs['Surface'])
    material.surface_render_method = 'DITHERED'
    mesh.materials.append(material)

    obj = bpy.data.objects.new('CatArtwork', mesh)
    bpy.context.collection.objects.link(obj)
    return obj


def make_rig(mesh_obj):
    armature_data = bpy.data.armatures.new('CatRig')
    armature_obj = bpy.data.objects.new('CatRig', armature_data)
    bpy.context.collection.objects.link(armature_obj)
    bpy.context.view_layer.objects.active = armature_obj
    armature_obj.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')

    def bone(name, head, tail, parent=None):
        b = armature_data.edit_bones.new(name)
        b.head = (*head, 0.12)
        b.tail = (*tail, 0.12)
        if parent:
            b.parent = armature_data.edit_bones[parent]
        return b

    bone('root', (0.0, -0.05), (0.28, -0.05))
    bone('torso', (-0.10, 0.00), (0.30, 0.02), 'root')
    bone('head', (-0.58, 0.28), (-0.38, 0.48), 'torso')
    bone('tail', (0.38, 0.14), (0.70, 0.32), 'torso')
    bone('front_leg', (-0.38, -0.15), (-0.45, -0.65), 'torso')
    bone('rear_leg', (0.26, -0.14), (0.35, -0.63), 'torso')
    bpy.ops.object.mode_set(mode='POSE')

    # Smooth weights by broad anatomical zones. The mesh is dense enough that
    # bone motion is continuous instead of a sequence of independent cels.
    for bone_name in ('root', 'torso', 'head', 'tail', 'front_leg', 'rear_leg'):
        mesh_obj.vertex_groups.new(name=bone_name)
    for vertex in mesh_obj.data.vertices:
        x, y, _ = vertex.co
        if x < -0.34 and y > 0.05:
            name = 'head'
        elif x > 0.34 and y > -0.25:
            name = 'tail'
        elif y < -0.28 and x < -0.08:
            name = 'front_leg'
        elif y < -0.28 and x >= -0.08:
            name = 'rear_leg'
        else:
            name = 'torso'
        mesh_obj.vertex_groups[name].add([vertex.index], 1.0, 'REPLACE')

    modifier = mesh_obj.modifiers.new('CatRigDeformation', 'ARMATURE')
    modifier.object = armature_obj

    bpy.ops.object.mode_set(mode='OBJECT')
    return armature_obj


def set_pose(armature_obj, frame, front, rear, tail, head, body_y=0.0):
    bpy.context.scene.frame_set(frame)
    poses = armature_obj.pose.bones
    poses['front_leg'].rotation_mode = 'XYZ'
    poses['rear_leg'].rotation_mode = 'XYZ'
    poses['tail'].rotation_mode = 'XYZ'
    poses['head'].rotation_mode = 'XYZ'
    poses['torso'].rotation_mode = 'XYZ'
    poses['front_leg'].rotation_euler[2] = front
    poses['rear_leg'].rotation_euler[2] = rear
    poses['tail'].rotation_euler[2] = tail
    poses['head'].rotation_euler[2] = head
    poses['torso'].location.y = body_y


def animate(armature_obj):
    scene = bpy.context.scene
    scene.frame_start = 1
    scene.frame_end = 49
    poses = armature_obj.pose.bones
    keyframes = [
        (1, 0.00, 0.00, 0.00, 0.00, 0.00),
        (13, 0.28, -0.25, 0.10, -0.02, 0.02),
        (25, -0.25, 0.28, -0.10, 0.02, -0.01),
        (37, 0.28, -0.25, 0.10, -0.02, 0.02),
        (49, 0.00, 0.00, 0.00, 0.00, 0.00),
    ]
    for frame, front, rear, tail, head, body_y in keyframes:
        set_pose(armature_obj, frame, front, rear, tail, head, body_y)
        for name in ('front_leg', 'rear_leg', 'tail', 'head', 'torso'):
            poses[name].keyframe_insert('rotation_euler', frame=frame)
        poses['torso'].keyframe_insert('location', frame=frame)
    for curve in armature_obj.animation_data.action.fcurves:
        for key in curve.keyframe_points:
            key.interpolation = 'BEZIER'


def setup_camera_and_render():
    scene = bpy.context.scene
    scene.render.engine = 'BLENDER_WORKBENCH'
    scene.render.resolution_x = 512
    scene.render.resolution_y = 512
    scene.render.resolution_percentage = 100
    scene.render.fps = 24
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.film_transparent = False
    scene.world.color = (0.82, 0.78, 0.88)
    scene.display.shading.light = 'FLAT'
    scene.display.shading.color_type = 'TEXTURE'
    scene.display.shading.show_shadows = False
    scene.display.shading.show_cavity = False

    camera_data = bpy.data.cameras.new('Camera')
    camera = bpy.data.objects.new('Camera', camera_data)
    bpy.context.collection.objects.link(camera)
    camera.location = (0.0, 0.0, 10.0)
    camera_data.type = 'ORTHO'
    camera_data.ortho_scale = 2.35
    camera.rotation_euler = (0.0, 0.0, 0.0)
    scene.camera = camera

    scene.render.filepath = str(OUTPUT / 'frame-')
    scene.render.film_transparent = False
    bpy.ops.wm.save_as_mainfile(filepath=str(OUTPUT / 'cat-rig-prototype.blend'))
    scene.render.filepath = str(OUTPUT / 'frame-')
    bpy.ops.render.render(animation=True)


clear_scene()
mesh = make_textured_mesh()
rig = make_rig(mesh)
animate(rig)
setup_camera_and_render()
