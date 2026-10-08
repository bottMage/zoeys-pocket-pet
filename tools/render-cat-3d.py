"""Render a lightweight animated review of the saved genuine-3D Blender asset.

blender -b artwork/cat3d/cat-prototype.blend -t 4 \
  --python tools/render-cat-3d.py -- --output /workspace/artifacts/cat3d-preview

Workbench renders actual mesh/armature transforms; the video is only a review
artifact, never an in-app animation asset. It uses vertex colours and studio
lighting to make a 60 fps preview feasible on the cloud's CPU/software GPU.
"""

import argparse
import sys
from pathlib import Path

import bpy

parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--first", type=int, default=1)
parser.add_argument("--last", type=int, default=None)
parser.add_argument("--still", action="store_true")
args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:])
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=True)

for obj in bpy.data.objects:
    if obj.type != "MESH" or obj.data.color_attributes:
        continue
    color = obj.data.color_attributes.new(name="ReviewColor", type="FLOAT_COLOR", domain="POINT")
    for polygon in obj.data.polygons:
        value = obj.data.materials[polygon.material_index].diffuse_color if obj.data.materials else (.5, .5, .5, 1)
        for vertex in polygon.vertices:
            color.data[vertex].color = value

scene = bpy.context.scene
scene.render.engine = "BLENDER_WORKBENCH"
scene.render.resolution_x, scene.render.resolution_y = 720, 540
scene.render.image_settings.file_format = "PNG"
scene.render.fps = 60
scene.render.film_transparent = False
scene.display.render_aa = "5"
scene.display.shading.light = "STUDIO"
scene.display.shading.studio_light = "paint.sl"
scene.display.shading.studiolight_rotate_z = .45
scene.display.shading.color_type = "VERTEX"
scene.display.shading.show_shadows = True
scene.display.shading.show_cavity = False
scene.display.shading.show_object_outline = False
scene.display.shading.show_specular_highlight = True
scene.display.shading.shadow_intensity = .30
scene.display.shading.background_type = "WORLD"
scene.world.color = (.18, .20, .25)
scene.view_settings.view_transform = "Standard"
scene.view_settings.look = "Medium Low Contrast"
scene.view_settings.exposure = .6
scene.frame_start, scene.frame_end = args.first, args.last or int(bpy.data.objects["Cat_Rig"].get("clip_frames", 216))
if args.still:
    scene.frame_set(args.first)
    scene.render.filepath = str(out / "review-still.png")
    bpy.ops.render.render(write_still=True)
else:
    scene.render.filepath = str(out / "frames" / "frame-")
    bpy.ops.render.render(animation=True)
