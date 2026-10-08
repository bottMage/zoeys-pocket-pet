# Cat 3D prototype — not shipped in the Android app

This is a Blender-authored first-pass recreation of the lavender/cream cat.
The original app art remains the visual reference. The external body, limbs,
ears, neck and tail are one fused, connected mesh. Facial details are actual
3D geometry bound to the same skeleton, not textured image cutouts.

`cat-prototype.blend` is the editable model, rig, baked walk and preview scene.
`cat-prototype.glb` contains the model, skin weights, joints and one 1.2-second
full-body walk clip, including 0.72-unit forward root motion. It is intended for
real-time GPU skinning after visual approval, not for playing rendered frames
inside the app. Root motion must be extracted or consumed once, never applied
again on top of an independent movement controller.
The model faces Blender -Y / glTF +Z; exported forward displacement is +0.72 Z.
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
`walk-preview.mp4` is a 60-fps, 3.6-second review covering three complete cycles;
`cat-preview.png` is a still from that review. Lighting is intentionally simple
studio lighting, not finished in-app scenery or a promise of final fur quality.

The preview is an offline Blender render, not an Android device performance
measurement. Visual approval is required before any app integration or release.
This is a prototype, not a claim of production/game/movie-quality animation.

No app code, save data, account/cloud behavior, update metadata, or APK changes
are needed to evaluate this prototype.
