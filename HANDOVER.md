# Zoey's Pocket Pet — cloud handover

## Existing-artwork cat polish candidate — not published (2026-10-08)

### Front-pair standing pose trial — still review first

After the relocation preview, the user still found the front pair wrong and
approved correcting their resting posture before another animation clip. Keep
the PNGs and bind geometry; repose the near paw 24px rearward (146->170), raise
its shoulder 6px, and bring the far paw 4px forward (203->199). Both resting
paws are now within 3px of their shoulders; front-paw spacing is 29px versus
28px shoulder spacing, rather than the earlier 57px paw gap. Original bone
lengths, bend direction and gait phases are retained. Rear standing meshes,
head, torso, tail, layer order, UI, saves and updates are untouched.

The upright front stance needs a reach guard: a front paw takes a timed recovery
step before exceeding its standing reach, including during braking/actions.
It does not slide into place or stretch a bone. Front recovery speed reserves
more headroom for vertical movement; rear recovery parameters are unchanged.
Contact, reach, speed, attachment and long-running roaming checks pass at
30/60/120 fps. `assembleDebug` and `lintDebug` pass with the original signing
certificate verified; these are not visual approval or a phone performance
measurement.

Latest review is `artwork/cat-rig-review/front-pair-standing.png`, rendered from
the same production pose/mesh math. Preserve all earlier preview videos. Show
this standing still to the user before making another walk clip. This is an
unpublished source trial: public APK/version/manifest stay v39. Do not tag or
publish a release without approval of the visual candidate.

### Near foreleg placement trial

The user liked the depth correction but found the foremost leg too far forward,
then authorized moving it back a little. `FRONT_NEAR` moves 34 authoring pixels
rearward: artwork x 70->104, shoulder 138->172, elbow 145->179, paw 112->146.
All y coordinates, dimensions, bone lengths, bend, gait phase and controller
parameters are unchanged. Other limbs, tail, head, artwork, layering, saves and
updates are unchanged. `CatLayersCheck` checks chest placement and retained
relative joint/art coordinates. Latest review is
`artwork/cat-rig-review/foreleg-placement-preview.mp4`; keep prior previews.
This remains an unpublished trial awaiting visual approval, not a new release.

### Depth/attachment correction after first preview

The user found the improved timing better but correctly identified that every
limb and the tail were behind the torso. The new render order is far limbs,
torso, tail, near limbs, head. `CatLayers` shares the depth groups, tail placement
and proximal alpha masks between Android and the Java2D preview. Simple draw
order changes alone expose closed black cutout caps, so cached copies open only
the near-limb and tail attachment ends. PNG source files are not edited, and
paws, head, torso, far limbs and the free tail contour retain their alpha.

Proximal limb mesh rows bind to the torso transform instead of swinging as a
closed cap. Tail placement moves down 25 authoring pixels and its pivot is now
at the rump (348,270), not above the back. Gait/controller/timing and head motion
are unchanged. Tail and thigh colours remain the original artwork; this is not
a repainted or genuinely 3D model. Do not promise completely invisible seams.

`CatLayersCheck` checks depth groups, preserved alpha regions, pinned root rows
and the horizontal animation envelope. Contact, reach and roaming regressions
still pass at 30/60/120 fps. The latest offline review is
`artwork/cat-rig-review/depth-connected-preview.mp4`; preserve the previous
`coordinated-walk-preview.mp4` for comparison. No APK tag or update manifest
changes; this remains a visual review candidate until the user approves it.

The user rejected revision 2 of the 3D prototype as limping/unconvincing. Be
candid: technical skin/contact checks are not evidence of professional animation
quality. The user now says the original cutout cat was the best visual result
and authorizes the best possible polish of it. Do not resume the rejected 3D
procedural tweaks or imply a professional animator/paid asset is required to
continue; do not guarantee that these changes solve the visual complaint.

This candidate preserves every original PNG and the layered rendering approach.
`CatMotion` keeps world-space planted contacts through starts/stops instead of
shrinking the gait amplitude with speed. Recoveries are timed and finish when
travel pauses; standing feet are not dragged into a neutral pose. Starts select
the most urgent paw and use lateral-walk order; stopping brakes before an edge.
`CatRig.Pose` drives a small rigid torso weight transfer and coordinated neck/tail
counterbalance. The torso is not mesh-warped. Turning still flips facing while
stationary; it is not a fully authored turn animation.

Android uses high-resolution frame intervals, small integration steps, vsync
redraws, density-appropriate head decoding and initial existing-cat preloading.
Background time is not played back as missed walk poses. `CatGaitCheck` checks
production contacts, paw height, reach and frame-to-frame recovery motion;
`CatMotionCheck` retains long-running edge/action regressions. All are exercised
at 30/60/120 fps. `CatRigPreview` now renders the same trunk/head/tail pose and
controller, including fractional coordinates; roaming preview time is 60 fps,
not the old mismatched 30-Hz simulation exported as 60-fps video.

`artwork/cat-rig-review/coordinated-walk-preview.mp4` is review-only, not an app
asset or device performance measurement. Obtain visual approval before tagging
or publishing an APK. Build remains v39 and the public v39 APK/manifest are
unchanged. Local/cloud saving, account restore, updater, UI and other pets are
untouched. See `artwork/cat-rig-review/README.md` for commands and limitations.

## True-3D cat prototype — review only (2026-10-08)

### Revision 2 — more lively, still awaiting visual approval

The user liked the genuine 3D movement direction but found the first pass too
restrained/cramped. The second pass opens paw spacing and increases stride from
0.72 to 0.98 units with a 1-second gait cycle, allows knee flexion and air-phase
paw curl, and adds stronger coupled shoulder/pelvis/spine movement. Head glances
and tail swishes span a 4-second/four-stride clip; total root motion is 3.92 units
(Blender -Y / glTF +Z). Phase follows root distance so the small speed variation
does not produce planted-paw sliding. Rig custom properties/exported extras
record clip/stride/stance parameters for validation and future integration.

The fixed preview camera now shows actual travel. Framing is checked across
the clip. A tail-tip weight-classification issue exposed by bigger swishes was
fixed: frontmost tip vertices must bind to the tail, not fall through to head
weights; tail links blend by adjacent arc position instead of unrelated nearest
bones. The body mesh/character design is otherwise unchanged. Preserve the
first `walk-preview.mp4` for comparison; latest is `playful-stroll-preview.mp4`.
Do not interpret the user's approval of the **direction** as approval to ship.
The app, updater and local/cloud progress remain untouched.

The user rejected the independent PNG-part rig as rigid and robotic and approved
a genuine 3D recreation of the purple/cream cat, with a walk preview **before**
changing or publishing the app. `artwork/cat3d/` contains the editable Blender
model/rig/scene, GLB and structural/motion validation. `tools/build-cat-3d.py`
authors one connected external skin and a 26-bone full-body skeleton, including
spine, pelvis, chest, scapulae, neck/head, legs/paws and six tail bones. Facial
features are actual geometry attached to the same skeleton, not PNG billboards.
The model remains first-pass styling, not user-approved production artwork.

The first GLB had a baked 1.2-second walk with 0.72-unit root motion; revision 2
supersedes it as described above. Extract/consume
that motion once when integrating; do not add a separate controller's movement
on top. No image textures/cutouts are required. GPU skinning uses four normalized
influences per vertex. `tools/check-cat-3d.py` checks exported skin/animation and
colours, body/shoulder/tail channels, supporting paw-bone and rendered-pad drift,
ground height, finite positions and root-compensated looping. Always invoke
Blender with `--python-exit-code 1` for meaningful failure status.

`tools/render-cat-3d.py` renders an offline 60-fps review using lighter studio
lighting for the cloud's software GPU. Rendered frames/video are **review only**,
not runtime animation assets or a phone performance measurement. The app still
ships v39's existing renderer; no app code, APK, update manifest, or save/cloud
behavior has changed for this prototype. Obtain visual approval before app
integration or a new APK release.

## v39 update discovery correction (2026-10-08)

The static raw manifest was cached by GitHub for five minutes (`max-age=300`).
`UpdateChecker.java` now uses unique cache keys and no-cache headers. If the
manifest does not offer a newer build, it independently checks the public
`/releases/latest` redirect (not the rate-limited/API host) and verifies a newer
APK exists before offering it. Missing/invalid metadata and unconfirmed latest
versions are failures, not “up to date.” Overlapping launch/manual checks share
one request and cannot show duplicate update dialogs. `tools/UpdateCheckerCheck.java`
exercises the production HTTP code and decision logic with a local HTTP fixture.
Publish the signed APK first, verify it exists, then update `update.json` as usual.
Older installed builds still use the cached static manifest until they install v39.
Animation, installer handoff and progress-saving behavior are unchanged.

## Current animation work — v37 (2026-10-08)

### v38 correction

The user reported a permanent stop after the first walk and visible frayed seams.
The stop was an edge restart bug: a zero-speed first frame at an edge was treated
as another collision. `CatMotion.java` now owns cat roaming, only ends a walk on
an actual outward collision, and is exercised by `tools/CatMotionCheck.java` over
three minutes at 30/60/120 fps, including actions and temporary zero-width lanes.
Cat sizing reserves a travel lane for every evolution stage.

The layer export previously replaced original 8-bit alpha with a 1-bit connected
component mask, promoting almost-transparent colored noise to fully opaque edges.
The repaired artwork is exported by `tools/export-cat-layers.sh` without replacing
alpha. `tools/CatAssetCheck.java` checks fractional alpha and opaque colored fringe
speckles. The torso's attachment strokes are repaired to soften leg/neck seams.

The historical notes below describe v5 and must not be treated as the current release.
v37 replaces v36's rigid swinging cat legs with a two-bone IK chain and a level
paw bone. `CatRig.java` computes the mesh vertices for `drawBitmapMesh` on the
hardware Canvas. Four staggered steps use a 68% stance period. During steady
walking, root distance and gait phase share a 48 authoring-pixel stride; supporting
paws stay fixed in world space. Walking acceleration/deceleration and pose blend
are eased; the cat settles before reversing. Head and tail follow gently.
The cat head's neck attachment was repaired and the extra chest overlay removed.
The other pets retain their existing cel renderer. Cloud progress and updater
code were not modified by this animation change.

`tools/CatRigPreview.java` renders the same joint/mesh calculations outside Android
and checks the rendered paw's horizontal contact, ground height, finite vertices,
and continuity at touchdown. Compile it with `CatRig.java` using the cloud JDK,
then run with drawable-nodpi and an output directory as its two arguments. Optional
third argument controls frame count. Preview output is 60 fps at a steady walk.
Rendered poses and contact math were inspected, and Android assemble/lint must
pass before publishing. No phone or emulator is attached to this workspace, so
do not claim measured device frame rates or final user-approved visual quality.

## What the user wants now

Continue improving the Android pet app, especially the creature animation. The user is unhappy with the current movement: pets look as if they float, are rigid/glitchy, and may appear to run backward or slide rather than interact with the ground. Treat this as the main product problem; do not claim it is solved without visual validation on a device/screenshot.

They also asked for a complete handover because this cloud conversation cannot be placed in a Codex Project folder. This document is intended for the next chat.

## Repository and current state

- Repository: `https://github.com/bottMage/zoeys-pocket-pet` (public).
- Cloud checkout: `/workspace/zoeys-pocket-pet`.
- Local branch name is `work`; completed changes are pushed to remote `main` using `git push origin HEAD:main`.
- Latest pushed commit: `450771c Open Android installer after update download`.
- Latest public release/tag: `v5`.
- Current Android version: `versionCode = 5`, `versionName = "5.0"` in `app/build.gradle.kts`.
- Do not rewrite or discard existing changes. Check `git status --short` before edits.

## Build environment

The project is a Kotlin Android app. Use this exact build command in the repository:

```bash
JAVA_HOME=/workspace/.toolchains/jdk-21 \
ANDROID_HOME=/workspace/.android-sdk \
ANDROID_USER_HOME=/workspace/.android-user \
GRADLE_USER_HOME=/workspace/.gradle \
bash ./gradlew --no-daemon --max-workers=4 assembleDebug lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Critical signing constraint

The phone is now using the cloud-signed build. Future updates **must** be signed by the same cloud debug key. The certificate SHA-256 is:

```text
552f2d2f4a8f6e2ddb84306c0183c88ef240ef44acad872e5c3d66df5b566cca
```

Verify a built APK with:

```bash
/workspace/.android-sdk/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
```

Do not move the build to the user's laptop or rebuild it in GitHub Actions with another key. That would make Android reject updates as “app not installed.”

## Release/update pipeline

In-app update checks GitHub Releases, not source-code tags alone. It expects the latest release tag to be `v<versionCode>` and searches its assets for an `.apk`.

The workflow `.github/workflows/publish-release.yml` publishes an already cloud-signed APK from the repository. For every release:

1. Increment both `versionCode` and `versionName`.
2. Build and lint with the command above.
3. Copy the built APK to `updates/zoeys-pocket-pet-vN.apk`, where `N` is the new version code.
4. Verify the certificate digest.
5. Commit the app changes and the versioned APK.
6. Push the commit to remote `main`.
7. Create and push annotated tag `vN`.
8. Confirm the GitHub Release asset is available, for example:

```bash
curl -I -L https://github.com/bottMage/zoeys-pocket-pet/releases/download/vN/zoeys-pocket-pet-vN.apk
```

The tag triggers GitHub Actions, which attaches the committed cloud-signed asset. GitHub API access from this cloud may be blocked, but public GitHub pages and normal `git push` work. The Actions page can be read publicly if diagnostics are needed.

## In-app updater implementation

`MainActivity.kt` contains `AppUpdateManager`.

- It checks on app launch (silent when no update), and via the **UPDATES** pill next to Reset.
- It uses the GitHub `/releases/latest` API.
- Updates are optional: a dialog offers Download or Not Now.
- Android cannot silently install a normal app. The final Android package-installer approval is mandatory.
- v5 changes the completed-download handoff: it sets the APK MIME type and returns the installer launch to the app's main/UI thread. This should open Android's installer after Download instead of making the user browse Downloads. The user reported that v4 left only a file in Downloads. Ask them to test v5 before assuming the fix works.
- If v4 cannot hand off to v5, direct download is:
  `https://github.com/bottMage/zoeys-pocket-pet/releases/download/v5/zoeys-pocket-pet-v5.apk`

Android may require the one-time “Allow from this source” permission for Zoey's Pocket Pet before it can hand off to the installer.

## Version label

v4 added a subtle `v<version>` label under the Reset button. It uses `BuildConfig.VERSION_NAME`. `buildFeatures { buildConfig = true }` is intentionally enabled in `app/build.gradle.kts`; do not remove it.

## Animation/art implementation and known shortcomings

The app draws a whole-body animation cel, not separately moving body parts:

- Source: `app/src/main/java/com/example/shortsgesturecontrol/MainActivity.kt`, primarily `PetGameView.drawPet`.
- Art: `app/src/main/res/drawable-nodpi/walk_<kind>_<0..11>.png` for cat, dog, bunny, hamster, and dragon; 60 PNG cels total.
- The earlier individual limb/rig assets were removed. Do not reintroduce them as “animation”; the user explicitly rejected image-part warping/independent layers and wants genuine whole-body animation.
- The pet selector uses `companion_<kind>.png` assets.

Recent motion fixes in v3:

- Corrected a mirror-direction bug. The art faces left by default, so it is mirrored only when travelling right.
- Reduced scene travel speed from `.22f` to `.070f`.
- Slowed cel timing from 70 ms to 105 ms.
- Removed resting whole-body bob.
- Added alpha-bound scanning per cel and anchors the lowest non-transparent pixel to `groundY`, rather than the PNG's transparent 512px border.

These are mechanical improvements, but the user still sees the animation as cheap, rigid, floating, and disconnected from the ground. The underlying cels themselves are likely not a coherent walk cycle / have inadequate foot contacts. More timing tweaks alone will not create convincing animation. A real fix requires replacement coherent animation cels (or a proper skeletal animation authoring/rendering pipeline) designed for each creature, with consistent anatomy, stance, direction, contact poses, and body weight. Keep the user’s requested cute Pokémon-inspired *general feel*, but do not copy Pokémon characters or artwork.

The user’s reported visual history:

- Initial retro/pixel dragon was unrecognizable; pixel direction was abandoned.
- Pokémon-like cute creature art was preferred over the pixel work.
- AI-generated / independently warped parts looked pasted-on, especially wings, head, and overlapping legs.
- Whole-body cels improved this but still need actual animation-quality anatomy and ground contact.
- The user specifically asked for lifelike movement: stand, sit, walk around, look around, proper timing, smooth/elegant motion, and paws visibly planted on the ground.

Do not tell the user the current animation is “fixed” without asking for a screenshot/video or validating it visually.

## Prior screenshots / context

The conversation included phone screenshots of:

- Early pixel dragon that was not recognizable.
- A Tamagotchi reference photograph showing clear, coherent pixel creature shapes.
- Cute green bunny and blue dragon art with anatomy/rig issues.

These attachments may not be available to a new chat. Ask the user to reattach any reference screenshots/video needed for visual judgement; do not pretend you can see attachments that are not present.

## Useful files

- Main UI/game/updater: `app/src/main/java/com/example/shortsgesturecontrol/MainActivity.kt`
- Android config/version: `app/build.gradle.kts`
- Manifest/update permissions/FileProvider: `app/src/main/AndroidManifest.xml`
- FileProvider paths: `app/src/main/res/xml/update_file_paths.xml`
- Release workflow: `.github/workflows/publish-release.yml`
- Release APK assets: `updates/`

## Communication notes

- The app is for the user's daughter; public GitHub is acceptable.
- The user wants cloud-only building and publishing, not laptop builds.
- Keep explanations plain and candid. The user strongly dislikes fake-looking animation and is sensitive to visual quality.
- Before tools, give a short commentary update. Do not promise silent APK installation: Android blocks it on ordinary personal devices.
