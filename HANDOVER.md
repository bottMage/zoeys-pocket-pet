# Zoey's Pocket Pet — cloud handover

## True-3D cat prototype — review only (2026-10-08)

The user rejected the independent PNG-part rig as rigid and robotic and approved
a genuine 3D recreation of the purple/cream cat, with a walk preview **before**
changing or publishing the app. `artwork/cat3d/` contains the editable Blender
model/rig/scene, GLB and structural/motion validation. `tools/build-cat-3d.py`
authors one connected external skin and a 26-bone full-body skeleton, including
spine, pelvis, chest, scapulae, neck/head, legs/paws and six tail bones. Facial
features are actual geometry attached to the same skeleton, not PNG billboards.
The model remains first-pass styling, not user-approved production artwork.

The GLB has a baked 1.2-second walk with 0.72-unit root motion. Extract/consume
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
