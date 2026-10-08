# Existing-artwork cat polish — review only

The user prefers the original purple/cream cutout artwork to the rejected 3D
prototypes. This candidate keeps every PNG unchanged. It is still a 2D layered
rig, not a professionally authored 3D character or replacement cel animation.
Do not claim the animation quality has been approved.

## Livelier walk trial

Latest review: `livelier-walking-preview.mp4` (18s, 60fps). The user approved the
standing placement and subsequent walk, but found its movement too restrained.
The original artwork and approved resting geometry stay unchanged. Travel is
72px/s (previously 42), stride 60px (48), paw lift 26px (15), with broader,
smooth joint recovery and shorter pauses. Head, tail and rigid-body weight
transfer have larger coordinated ranges; no torso stretching or new art.
Reach protection covers all four legs during normal motion and interruptions.

Keep `front-pair-walking-preview.mp4` as the prior motion comparison. The new
preview remains unpublished pending approval; no updater, save, UI or other-pet
changes. `CatLivelinessCheck` measures travel/lift/neck/tail/joint ranges and
support contacts, in addition to the existing mechanical regressions. These
measurements are not visual approval or a phone frame-rate measurement.

## Front-pair standing pose trial

Latest review: `front-pair-standing.png`, a neutral standing render, not another
walk clip. Near paw moves rearward 24px relative to its bind pose and its shoulder
rises 6px; far paw moves forward 4px. Front paws are under their shoulders with
29px separation instead of 57px. Bind bone lengths, art, phase and rear standing
geometry are unchanged. Front recoveries guard their narrower standing reach,
including interrupted movement; contacts stay planted until a recovery step.
Rear recovery settings, travel speed, torso/head/tail, UI, progress and updater
are unchanged. `CatLayersCheck` additionally tests neutral paw/shoulder spacing
and unchanged rear meshes. Await standing-pose approval before a new walk clip.

Render just the still using the compiled classes below:

```bash
/workspace/.toolchains/jdk-21/bin/java -Djava.awt.headless=true \
  -cp /workspace/artifacts/cat-rig-review/classes CatRigPreview \
  app/src/main/res/drawable-nodpi /workspace/artifacts/cat-standing-review/still 1 roam
cp /workspace/artifacts/cat-standing-review/still/frame-000.png \
  artwork/cat-rig-review/front-pair-standing.png
```

## Near foreleg placement trial

Latest review: `foreleg-placement-preview.mp4`. The complete near foreleg is
translated 34 authoring pixels rearward under the chest. Shoulder, elbow, paw
and art canvas move together; proportions, height, phase and motion controller
stay unchanged. All other parts and the depth/attachment correction stay as
before. Keep `depth-connected-preview.mp4` as the previous placement comparison.
`CatLayersCheck` also guards the chest placement and preserved relative geometry.

## Depth/attachment revision

Latest review: `depth-connected-preview.mp4`. Keep the first
`coordinated-walk-preview.mp4` for comparison. The user identified the old
all-appendages-behind-the-torso order as pasted-on depth.

`CatLayers` now defines far legs behind the torso and near legs in front. The
tail is above the torso but below the near limbs. Its placement is lowered 25
authoring pixels and its rotation pivot is at the rump. Cached bitmap copies
blend only the closed proximal caps into their joins; original source PNGs are
unchanged. The free tail contour and paws are not faded. Upper limb attachment
patches inherit the torso transform; exposed limbs still use the original IK.
No gait, travel, head-motion, clock or save/update behaviour changes are made.
This is still layered 2D art; subtle paint/pattern differences at joins remain
possible, and the user has not approved the new visual result.

The controller now keeps supporting paws fixed in world space, including speed
ramps. Swinging paws finish a smooth recovery when travel stops; feet are not
pulled toward a neutral pose by a speed-dependent animation-amplitude blend.
Starts pick the paw nearest its reach limit, with lateral-walk sequencing;
stops brake before scene boundaries. The trunk is rigid, gently transferring
weight; head and tail inherit that transform and counterbalance the same gait.
No torso stretching, art regeneration or sprite swapping is used.

Android uses nanosecond elapsed time with small integration steps and vsync
redraws. Head decoding is sized for display density, existing-cat assets are
preloaded before the first draw, and background time is excluded from movement.
None of this establishes measured phone performance. The review video is an
offline Java2D rendering of the production math, at 60 fps, not a runtime asset.
Turning is still a stationary facing-direction flip, not an authored turn.

Run from the repository with the cloud JDK:

```bash
mkdir -p /workspace/artifacts/cat-rig-review/classes
/workspace/.toolchains/jdk-21/bin/javac \
  -d /workspace/artifacts/cat-rig-review/classes \
  app/src/main/java/com/example/shortsgesturecontrol/CatRig.java \
  app/src/main/java/com/example/shortsgesturecontrol/CatMotion.java \
  app/src/main/java/com/example/shortsgesturecontrol/CatLayers.java \
  tools/CatMotionCheck.java tools/CatGaitCheck.java tools/CatRigPreview.java \
  tools/CatLayersCheck.java tools/CatLivelinessCheck.java
/workspace/.toolchains/jdk-21/bin/java \
  -cp /workspace/artifacts/cat-rig-review/classes CatLayersCheck
/workspace/.toolchains/jdk-21/bin/java \
  -cp /workspace/artifacts/cat-rig-review/classes CatGaitCheck
/workspace/.toolchains/jdk-21/bin/java \
  -cp /workspace/artifacts/cat-rig-review/classes CatMotionCheck
/workspace/.toolchains/jdk-21/bin/java \
  -cp /workspace/artifacts/cat-rig-review/classes CatLivelinessCheck
/workspace/.toolchains/jdk-21/bin/java -Djava.awt.headless=true \
  -cp /workspace/artifacts/cat-rig-review/classes CatRigPreview \
  app/src/main/res/drawable-nodpi /workspace/artifacts/cat-rig-review/final 1080 roam
ffmpeg -hide_banner -loglevel error -framerate 60 \
  -i /workspace/artifacts/cat-rig-review/final/frame-%03d.png \
  -c:v libx264 -crf 18 -pix_fmt yuv420p -movflags +faststart \
  artwork/cat-rig-review/livelier-walking-preview.mp4
```

Tests check rendered paw height, planted contact drift, reach, recovery speeds,
finite meshes, C2 trajectory joins, repeated crossings and actions at 30/60/120
fps. Build/lint are required before release. App version remains v39; no release
or update manifest changes, and no local/cloud progress or installer changes.
