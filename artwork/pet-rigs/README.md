# Anatomy-specific pet rigs

The user asked to leave the cat's current 2D rig as the baseline and extend the
same recipe to the other species, respecting their different anatomy. This is
continuous joint/mesh animation, not playback of whole-pet walk images. It is
still a stylized layered 2D rig, not a Blender-quality 3D character.

The four `*-layers.png` sheets were generated from each existing
`walk_<species>_0.png` reference. They preserve the character's colors and design
direction, but are newly drawn separable artwork, not the original pixels.
Original companion/walk PNGs and all existing cat artwork remain untouched.

## Anatomy and parenting

- Dog: diagonal leg timing, articulated knees, flexible tail and floppy ears.
- Bunny: paired fore/hind timing, larger hind feet and compression following the
  hind-leg push; long ears inherit the head. This is a grounded bound-like gait,
  not an authored airborne hop.
- Hamster: compact leg proportions, short strides, low paw lift and small ears.
- Dragon: independent near/far wing layers hinge from the shared trunk, with
  restrained follow-through; grounded legs, neck and tail retain dragon-specific
  placement. No flight sequence is claimed.

`PetRig.java` defines geometry, parenting, depth order and skin weights.
`PetMotion.java` uses persistent world-space paw contacts and timed recoveries.
The trunk is rigid; body/head/tail/appendage motion follows the same travel phase.
Near attachment caps are softened only on cached bitmaps, not on source files.
Actual paws and free appendage contours keep their antialiased source alpha.
There are at most three recovering paws. Recovery targets must remain reachable
even if travel is interrupted, and supporting contacts bound root travel.

## Reproduce exports and checks

From the repository root, with JDK 21 on PATH:

```bash
mkdir -p /tmp/pet-rig-classes
javac -d /tmp/pet-rig-classes tools/ExportPetLayers.java
for kind in dog bunny hamster dragon; do
  java -cp /tmp/pet-rig-classes ExportPetLayers \
    artwork/pet-rigs/$kind-layers.png "$kind" app/src/main/res/drawable-nodpi
done
javac -d /tmp/pet-rig-classes \
  app/src/main/java/com/example/shortsgesturecontrol/CatRig.java \
  app/src/main/java/com/example/shortsgesturecontrol/CatMotion.java \
  app/src/main/java/com/example/shortsgesturecontrol/CatLayers.java \
  app/src/main/java/com/example/shortsgesturecontrol/PetRig.java \
  app/src/main/java/com/example/shortsgesturecontrol/PetMotion.java \
  tools/CatRigPreview.java tools/PetRigPreview.java tools/PetRigCheck.java
java -Djava.awt.headless=true -cp /tmp/pet-rig-classes PetRigCheck \
  app/src/main/res/drawable-nodpi
java -Djava.awt.headless=true -cp /tmp/pet-rig-classes PetRigPreview \
  app/src/main/res/drawable-nodpi /tmp/pet-rig-preview all 540
ffmpeg -framerate 60 -i /tmp/pet-rig-preview/frame-%03d.png \
  -vf scale=1280:864 -c:v libx264 -crf 18 -pix_fmt yuv420p \
  -movflags +faststart /tmp/all-pets-preview.mp4
```

The exporter finds separated alpha components instead of assuming uniform grid
cells. It retains source partial alpha; it never promotes background noise to
opaque pixels. Preview uses production pose/contact/mesh math with Java2D,
including Android's attachment masks and mirroring. It is an offline inspection
tool, not a phone performance measurement or runtime animation asset.

Checks cover 180 simulated seconds per species at 30/60/120 fps and three travel
lanes, including actions, no available travel, frame stalls, turning and repeated
restarts. They check contacts, joint reach, finite meshes, paw speed, appendage
bounds, soft alpha and supporting paws. Device smoothness still needs phone
testing. Turning currently mirrors the rig at rest; there is no authored turn.
