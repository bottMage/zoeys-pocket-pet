# Existing-artwork cat polish — review only

The user prefers the original purple/cream cutout artwork to the rejected 3D
prototypes. This candidate keeps every PNG unchanged. It is still a 2D layered
rig, not a professionally authored 3D character or replacement cel animation.
Do not claim the animation quality has been approved.

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
  tools/CatMotionCheck.java tools/CatGaitCheck.java tools/CatRigPreview.java
/workspace/.toolchains/jdk-21/bin/java \
  -cp /workspace/artifacts/cat-rig-review/classes CatGaitCheck
/workspace/.toolchains/jdk-21/bin/java \
  -cp /workspace/artifacts/cat-rig-review/classes CatMotionCheck
/workspace/.toolchains/jdk-21/bin/java -Djava.awt.headless=true \
  -cp /workspace/artifacts/cat-rig-review/classes CatRigPreview \
  app/src/main/res/drawable-nodpi /workspace/artifacts/cat-rig-review/final 540 roam
ffmpeg -hide_banner -loglevel error -framerate 60 \
  -i /workspace/artifacts/cat-rig-review/final/frame-%03d.png \
  -c:v libx264 -crf 18 -pix_fmt yuv420p -movflags +faststart \
  artwork/cat-rig-review/coordinated-walk-preview.mp4
```

Tests check rendered paw height, planted contact drift, reach, recovery speeds,
finite meshes, C2 trajectory joins, repeated crossings and actions at 30/60/120
fps. Build/lint are required before release. App version remains v39; no release
or update manifest changes, and no local/cloud progress or installer changes.
