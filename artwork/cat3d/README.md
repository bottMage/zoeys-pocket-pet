# Cat 3D prototype — not shipped in the Android app

This is a Blender-authored first-pass recreation of the lavender/cream cat.
The original app art remains the visual reference. The external body, limbs,
ears, neck and tail are one fused, connected mesh. Facial details are actual
3D geometry bound to the same skeleton, not textured image cutouts.

`cat-prototype.blend` is the editable model, rig, baked walk and preview scene.
`cat-prototype.glb` now contains the revision-2 model, skin weights, joints and
one 4-second playful-stroll clip: four 1-second gait cycles, each covering 0.98
units, with 3.92 units of total forward root motion. It is intended for
real-time GPU skinning after visual approval, not for playing rendered frames
inside the app. Root motion must be extracted or consumed once, never applied
again on top of an independent movement controller.
The model faces Blender -Y / glTF +Z; exported forward displacement is +3.92 Z.
The skin has 15,502 vertices / 31,000 triangles and 26 deform joints. Tiny
supporting-pad clearance (about 0.002 units) is recorded in `validation.json`.

`tools/build-cat-3d.py` reproduces the asset using Blender 4.3+. Example:

```sh
blender -b --factory-startup --python-exit-code 1 -t 3 --python tools/build-cat-3d.py -- \
  --output artwork/cat3d --render-still
```

Validate the model and exported animation:

```sh
blender -b artwork/cat3d/cat-prototype.blend --python-exit-code 1 \
  --python tools/check-cat-3d.py
```

`tools/render-cat-3d.py` renders a lighter studio preview of the same geometry
and animation. Review frames/video are not runtime animation assets.
`walk-preview.mp4` preserves the original restrained first-pass review for
comparison. `playful-stroll-preview.mp4` is the revision-2, 60-fps, 4-second
review. Its fixed camera makes forward travel visible, while the slower head
glance and tail swish span several strides rather than repeat identically on
every step. Paw spacing/reach are wider, knees have room to flex, and paws curl
in recovery instead of staying level in the air. Supporting pads remain level
and planted. Gait phase follows root distance even during the subtle speed
variation; clip/gait parameters are stored on the rig and exported as extras.

The curled tail tip uses adjacent-chain weights; its front edge must not be
misclassified as head geometry. The outer skin's geometry/character design is
unchanged; skin weights and animation are revised.

`cat-preview.png` is a still from the latest review. Lighting is intentionally simple
studio lighting, not finished in-app scenery or a promise of final fur quality.

The preview is an offline Blender render, not an Android device performance
measurement. Visual approval is required before any app integration or release.
This is a prototype, not a claim of production/game/movie-quality animation.

No app code, save data, account/cloud behavior, update metadata, or APK changes
are needed to evaluate this prototype.
