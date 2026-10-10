# Zoey's Pocket Pet — cloud handover

## Current release candidate: responsive care button sizing — v88

The v88 candidate keeps v87's compact adjacent care-action clusters and large
CARE panel, but sizes every care button from the available lower-panel space.
Category and secondary-action geometry is checked for panel bounds, spacing,
and center-button collisions before the largest safe size is selected. Labels
scale with the button, and the same calculated rectangles drive touch handling,
so HEALTH can switch between CHECKUP/VITAMIN and CHECKUP/MEDICINE without
overlap, clipping, or a missing action if more buttons are added later.

## Previous release: compact care actions and full CARE panel — v87

The v87 release keeps v86's visible HEALTH score and replayable child-friendly
tutorial. Secondary radial actions now form compact adjacent clusters along
their category direction, with a small gap instead of a widely spaced fan. The
closed-state CARE control expands into a large styled panel filling the space
below the stat bars, so the lower section remains intentional and easy to tap.

The v87 APK uses the unchanged cloud certificate and is published at
`https://github.com/bottMage/zoeys-pocket-pet/releases/download/v87/zoeys-pocket-pet-v87.apk?download=87`.
Its verified SHA-256 is
`ef0d9d6b8da44fbb3524d88c3bf442ab5b0f375f791f98102b5c050b13d8244f`.

## Previous release: visible health and kid-friendly tutorial — v86

The v86 release relabels the visible header score from CARE to HEALTH. The
displayed value now reflects the pet's current needs and drops clearly when
the pet is sick, while the original lifetime care-quality score remains
internal for growth and reminder decisions. A short three-page tutorial now
appears once for a new player and can be replayed from MENU > SETTINGS > HOW TO
PLAY. It explains the need bars, the radial CARE menu, CHECKUP, VITAMIN, and
MEDICINE without changing existing progress or gameplay rules.

The v86 APK uses the unchanged cloud certificate and is published at
`https://github.com/bottMage/zoeys-pocket-pet/releases/download/v86/zoeys-pocket-pet-v86.apk?download=86`.
Its verified SHA-256 is
`cee0de7a946a6c79521f179da0e53ab9a5b09a82bb442b1ca60790c2b6e2cdbc`.

## Previous release: protected stats and persistent care actions — v85

The v85 release keeps v84's true radial CARE menu but gives the lower care
area a roughly 50/50 share of the phone screen. The scenery is shorter, the
stat check-in stays readable while care is open, and the radial radius adapts
to the available lower space. REST's NAP and SLEEP choices use a wider fan so
they do not overlap. Choosing an action leaves the category menu open for the
next action; the player closes it deliberately with the center CARE control.

The v85 APK uses the unchanged cloud certificate and is published at
`https://github.com/bottMage/zoeys-pocket-pet/releases/download/v85/zoeys-pocket-pet-v85.apk?download=85`.
Its verified SHA-256 is
`49266956b668cc9c1584a8b2aa29a1fc5e9a5e4d71c9c474e33dc20a570b46f1`.

## Previous release: true radial care menu and persistent opening — v84

The v84 release corrects the v83 bottom-menu interaction and layout. The
categories now fan around the central CARE button in a real radial arc rather
than appearing in a row, and each selected category fans its actions outward
from that category direction. Releasing the opening CARE tap leaves the menu
open; it no longer requires holding the button. Existing pet/scenery artwork,
animation, audio, saves, Firebase sync, updater, and release signing are
unchanged.

The health model remains elapsed-time based and offline-safe. If overall care is
poor for about four hours, or one need remains critically low for about two
hours, the pet becomes sick with a gentle symptom state; medicine clears it and
restores normal growth. Health state is saved locally and in Firestore. Closed
app reminders can repeat sickness help every three hours until medicine is
given, while existing stat-threshold and cute two-hour general reminders remain
rate-limited and battery-conscious.

The v84 APK uses the unchanged cloud certificate and is published at
`https://github.com/bottMage/zoeys-pocket-pet/releases/download/v84/zoeys-pocket-pet-v84.apk?download=84`.
Its verified SHA-256 is
`07486949a7c410e03f63857f412f37e634f8037035558b80dfb054f41730a021`.

## Previous release: radial care menu and gentle health state — v83

The v83 release kept v82's threshold and gentle general reminder work while
adding the elapsed-time health state and the first two-level care menu. A
central CARE button opened FOOD, FUN, REST, CLEAN, and HEALTH categories; each
naps and sleep, bath and tidy, or checkup, medicine, and vitamin. The v84
release refines only that bottom-menu interaction and layout.

The v83 APK remains published at
`https://github.com/bottMage/zoeys-pocket-pet/releases/download/v83/zoeys-pocket-pet-v83.apk?download=83`.
Its SHA-256 was
`1eddc05543c7749bc49c14b37325486a5fa7b5801007aca9ce4e111c7fa4f88f`.

## Previous release: gentle general reminders — v82

The v82 release kept v81's threshold-triggered care reminders and added
gentle general check-ins while the app is closed. After about two hours away,
the pet or egg can send a short, age-appropriate message, repeating no more
often than every two hours. The idle timer resets when the app returns to the
foreground, reminders stay quiet while it is visible, and care alerts suppress
general reminders so the notification stream does not become noisy. The
existing notification permission, Settings toggle, WorkManager fallback,
elapsed-time care model, and lightweight threshold alarm are reused.

The v82 APK uses the unchanged cloud certificate and is published at
`https://github.com/bottMage/zoeys-pocket-pet/releases/download/v82/zoeys-pocket-pet-v82.apk?download=82`.
Its SHA-256 is
`78817a6a5c5ca3a428905e63e35457507b1823f903c676a07a2643ab8da0c678`.

## Previous release: threshold-triggered care reminders — v81

The v81 release added battery-conscious offline care reminders without a live
background service. WorkManager provides a periodic fallback, while a single
AlarmManager wakeup is scheduled for the next projected stat crossing at 20%.
The app also checks immediately as it leaves the foreground, so a stat that
reached the threshold during the visible session does not wait for the fallback
interval. Reminder repeats are bounded at six hours for ordinary low stats,
three hours when overall care is affecting growth, and 90 minutes for severe
projected needs. Existing progress, updater, Firebase sync, animation,
scenery, audio, and release signing workflows remain unchanged.

The v81 APK was built with the unchanged cloud certificate and is published at
`https://github.com/bottMage/zoeys-pocket-pet/releases/download/v81/zoeys-pocket-pet-v81.apk?download=81`.
Its SHA-256 is
`874b66c624033bedcd0bd6dc9430e86fa16c95eeec397ea38dab1d24675030d7`.

## Previous release: idle rendering and sync optimization — v80

The v80 candidate keeps v79's non-blocking care-button feedback and all
existing pet, lifecycle, scenery, audio, updater, local-save, and Firebase
behavior. Idle scenery redraws at 30 Hz while walking, actions, transitions,
and touch reactions retain the existing display-synchronized cadence. Activity
pause/resume now stops background animation, ambient audio, and weather polling
cleanly. Local checkpoints remain every 30 seconds, while background Firestore
uploads are limited to once every five minutes; explicit care/lifecycle/pause
checkpoints still sync immediately. The v80 APK is signed with the unchanged
cloud certificate and has SHA-256
`58098ec5c04f8b164fcba9cc42809dbb60728aa358aae7e8ec296c361c919e39`.

## Previous release: non-blocking care-button feedback — v79

The v79 candidate keeps v78's full closed-eye sleep mask unchanged. Care
buttons now use Android's lightweight built-in click effect instead of
creating and releasing a ToneGenerator on every tap. That removes a
device-dependent main-thread stall that could build into an Android ANR after
repeated care actions. Pet rendering, cloud saves, ambient audio, and other
behavior are unchanged.

## Long-term project context

This handover is a consolidated record of the project from its beginning, not
just a release note for the latest build. The sections below preserve the
important product decisions and rejected approaches so a new chat does not
restart an old experiment or mistake historical notes for current behavior.

### Product and visual direction

The app began as a small Tamagotchi-style Android pet for the user's daughter,
originally using a retro/pixel dragon. That direction was abandoned because
the pets were not recognizable. The project then moved to the current cute,
full-body illustrated pets: cat, dog, bunny, hamster, and dragon. The original
pet designs and their visual style are the source of truth; do not replace
them with a green cartoon squirrel, generic silhouettes, or unrelated artwork.
The app is now named **MochiGotchi**, supports a custom player name and custom
pet name, and uses the approved purple egg-and-paw Android icon artwork with
transparent/round-icon-safe treatment.

Animation went through several failed experiments: frame swapping that looked
like a GIF, independently warped limb cutouts that looked pasted on, procedural
rigs with detached paws/tails, and a separate 3D-rendering prototype. Those
approaches were rejected. The retained runtime approach is coherent whole-pet
animation cels with per-pet anatomy and grounded alpha bounds. Do not revive
the rejected limb warping or claim that timing tweaks alone make the motion
professional. The user values smooth, weighty, connected movement, but the
current cels remain a known limitation and require visual validation on a
device.

### Gameplay and lifecycle decisions

The lifecycle is egg, baby, young, and adult. Dragon eggs use a nest; other
eggs use the appropriate straw/hay setting. Eggs rock or breathe subtly, show
a crack near hatch readiness, and hatch through a visible transition. The
current live-pet artwork is the baby stage, with larger and subtly recoloured
young/adult variants. Incubation is intended to be about two days; each live
stage is about a week, with poor care lengthening growth rather than erasing
earned progress. Care actions affect only their own need and do not directly
change the other stats. Adults eventually reach old age, but the player is
offered the choice to let the pet go or keep playing indefinitely.

Hatching, evolution, and old age are consent-based pending states. A player
can choose “not yet”; the prompt returns later. Hatching and growth use a
staged transition rather than an unexplained instant replacement. Reset and
data-loss behavior must remain conservative because the app is intended for
real use by the user's daughter.

### Scene, interaction, and audio decisions

The scene has a time-of-day sun/moon cycle and weather-responsive scenery,
with mountains and enlarged trees behind the foreground subject. The pet or
egg is always the foreground subject; birds and frogs are background ambience
and may disappear naturally behind it, but must never visibly clip through it.
Grass is one continuous, dense, pointed bank anchored to the absolute bottom
of the scene; blades stay planted and sway locally rather than scrolling.

Daytime uses the approved isolated bird-chirp clip. Rain uses the approved
first 50-second section loop. Night uses the approved cricket/frog section.
The user-provided cartoon snore is a separate ambient sleep loop, not button
feedback. Sleep lasts about one minute, with a distinct care-button click
acknowledgment, staggered visible Zs, and an upright breathing egg or a
lowered live-pet sleep pose. The live-pet closed-eye treatment is per-pet and
must fully cover the original eye rather than leaving a visible iris or making
an eyebrow-like line.

Care controls map spatially to the stats: FEED/WARM top-left, PLAY/SOOTHE
top-right, SLEEP/REST bottom-left, and BATH/TIDY bottom-right. Action feedback,
visible reactions, readable contrast, centered labels, and non-overlapping UI
are part of the product requirement.

### Persistence, sign-in, and updating

The app is local-first and must continue working offline. Google sign-in and
Firestore back up progress per account. Local state is restored before any new
pet/setup flow is allowed to overwrite it; a reinstall must never replace
cloud progress with a default pet. Changes made offline are queued locally and
synced when connectivity returns, including when the app is reopened or
connectivity changes while it is open. Settings provide separate reset choices
for local data, cloud data, or both.

The updater checks the public GitHub release/manifest, downloads the signed APK
inside the app, shows blocking progress while downloading, and hands the file
to Android's package installer. Android still requires the final user approval
for a normal APK; it cannot be silently installed by this app. Earlier v4/v5
installer and cache problems are historical. The current release pipeline and
v79 updater state are documented below.

## Previous release: full closed-eye sleep mask — v78

The v78 candidate keeps v77's direction-aware sleeping tilt. It enlarges and
re-centres the per-pet sleep mask so it fully covers the original eye, iris,
and eye border before drawing the curved closed lid inside it. This removes
the half-open-eye and misplaced-eyelid appearance. Hatching, walking, grass,
audio, and other interactions are unchanged.

## Previous release: directional sleeping tilt — v77

The v77 candidate keeps v76's per-pet closed-eye overlays and lower compressed
sleep pose. It corrects the remaining sleep posture issue by tilting the head
toward the ground based on the pet's facing direction; a fixed tilt could
raise the head when the pet faced right and make it look propped up. Hatching,
walking, grass, audio, and other interactions are unchanged.

## Previous release: clearer live-pet sleep pose — v76

The v76 candidate keeps v75's bottom-anchored taller grass unchanged. Live
pet sleep now settles the whole body lower with a gentler tilt and stronger
vertical compression, so it reads as resting rather than standing upright.
The closed-eye overlay is positioned per pet and covers the open iris with a
face-coloured almond before drawing a curved eyelid, avoiding stray eyebrow
marks. Hatching, walking, audio, and other interactions are unchanged.

## Previous release: bottom-anchored taller grass — v75

The v75 candidate keeps v74's dense, pointed grass blades but anchors every
blade directly to the scenery's bottom edge. The grass bank is taller so there
is no bare strip beneath it, while the egg or pet remains the foreground layer
above the grass. Hatching behavior is unchanged; the egg in v74's screenshot
was correctly at 99%, below the real prompt threshold.

## Previous release: dense pointed grass and clear hatch readiness — v74

The v74 candidate keeps the continuous grass bank from v73 but replaces the
loose line marks with three tightly packed rows of tapered, pointed blades.
They remain planted and only sway at their tips. The hatch progress label no
longer rounds a nearly-complete egg up to 100% before the hatch prompt is
actually ready. Pet, creature, sleep, and audio behavior are unchanged.

## Previous release: continuous grass bank — v73

The v73 candidate keeps v72's fixed, locally swaying grass motion but groups
the ground detail into one continuous grass bank across the full lower strip.
Its top edge is gently uneven and the dense blades remain planted while their
tips sway. Pet, creature, sleep, and audio behavior are unchanged.

## Previous release: rooted wind-swaying grass — v72

The v72 candidate keeps the v71 natural background creature layering and
replaces scrolling ground marks with denser, deterministic grass tufts. Each
tuft stays planted at a fixed ground position and only its blades sway locally
with the wind, so grass no longer slides off one edge and reappears at the
other. Pet, creature, sleep, and audio behavior are unchanged.

## Previous release: natural background creature layering — v71

The v71 candidate keeps the v70 grounded frog hop timing and reverted distant
bird marks, but restores the correct scene draw order. Birds and frogs are
drawn inside the clipped scenery pass before the egg or pet. The egg/pet is
always the foreground subject, so ambient creatures can naturally disappear
behind it without skip/clamp pop-in or visible clipping.

## Previous release: grounded frog hops and reverted distant birds — v70

The v70 candidate keeps the improved frog anatomy but replaces continuous frog
sliding with explicit hop cycles: a short grounded pause, a smooth launch and
landing arc, then a grounded pause at the new position. Frogs use two distant
ground lanes and are clamped around the pet footprint rather than being
skipped, so they stay visible and cannot pop in/out through the nest. Birds are
back to the original bendy-stick silhouettes with rounded strokes and a modest
speed increase. Sleep audio and Z animation are unchanged.

## Previous release: faster ambient flight and safe frog layering — v69

The v69 candidate keeps the v68 creature silhouettes but increases bird travel,
flap amplitude and flight cadence, and increases frog hop cadence, travel and
arc height. Frogs are now rendered after the pet as foreground scenery while
skipping the pet footprint, so they cannot disappear behind or clip through an
egg, nest or live pet. The approved sleep audio and Z animation are unchanged.

## Previous release: smoother ambient birds and frogs — v68

The v68 candidate keeps the v67 Z timing and all approved sleep audio
unchanged. Daytime birds now use compact filled silhouettes with body, head,
beak, tail and layered wings, with slower continuous travel and coordinated
flapping. Night frogs now have clearer anatomy, eyes, mouths and feet, plus
three independently phased smooth hops across the ground instead of sitting in
place.

## Previous release: natural sleep Z timing — v67

The v67 candidate keeps the v66 approved snore audio and upright breathing egg
sleep exactly unchanged. Sleep Zs now spawn one at a time with staggered
timing, eased upward motion, fade-in/fade-out, a small drift, and dynamic
day/night contrast. The Z cycle no longer pops three white letters in and out
at the same instant.

## Previous release: upright breathing egg sleep — v66

The v66 candidate keeps the v65 approved ambient sleep loop and one-minute
sleep behavior. During egg sleep, the egg remains upright and uses a slow
whole-egg vertical breathing pulse instead of rotating onto its side. Live
pet sleep behavior is unchanged.

## Previous release: approved ambient sleep loop — v65

The v65 candidate keeps the v64 one-minute sleep behavior and separate short
SLEEP button acknowledgment. The approved user-provided snore clip is now
stored as `sleep_ambient_snore.wav`, played only while the egg or pet is in the
sleep action, and looped by MediaPlayer. It stops when the 60-second sleep
action ends or the view is no longer visible. The old snore remains unused.

## Previous release: one-minute sleep and separated audio feedback — v64

The v64 candidate keeps the v63 lifecycle consent, mapped care controls,
whole-pet renderer, ambient audio and updater. The SLEEP/REST button now uses
the same short tap acknowledgment as the other care buttons; the previous
snore is no longer played as button feedback. Sleep settles into its pose in
about 1.2 seconds and remains active for 60 seconds. Replacement cartoon-snore
previews are being reviewed separately and are not yet included in the app.

## Previous release: lifecycle consent and mapped care controls — v63

The v63 candidate keeps the v62 whole-pet renderer, ambient audio and updater.
Care controls now match the stat grid: FEED/WARM is top-left, PLAY/SOOTHE is
top-right, SLEEP/REST is bottom-left, and BATH/TIDY is bottom-right. The four
existing action feedback sounds remain mapped to the action rather than its
screen position.

Hatching, evolution and old-age thresholds are now pending states. They no
longer mutate the pet automatically or archive a pet before the player agrees.
Each shows a repeatable consent dialog. HATCH NOW and GROW NOW run a staged
transition; the hatch transition reveals the baby artwork rising from the
cracked egg, and evolution smoothly scales the current whole-pet cel into the
next size before committing the new generation. NOT YET leaves the pending
state intact and reminds the player later. At old age, LET GO creates the
memorial; KEEP PLAYING persists an immortal adult choice until reset.

The new `oldAgeDeclined` field is saved locally and in Firestore, while pending
thresholds are represented by capped progress values so a process restart
cannot lose or silently apply the decision. `PetLifeCheck` passes the explicit
confirmation and keep-forever behavior, `WholePetCheck` still passes 148,680
whole-pet alpha-bound cases, and clean `assembleDebug`/`lintDebug` passes. The
v63 APK is signed with the unchanged cloud certificate and has SHA-256
`39a0c00b720bf3da6c151faa92b20ec98843e468cbaccbcb0680a67902f603ea`.

## Current work: eggs, visible growth and care lifecycle — v49

This supersedes earlier rollback-only notes. The user requested moving eggs in
a twig nest for dragon/straw for others, then the current design as a small
baby, visibly bigger/changed young and adult stages, and death from old age.
They clarified: egg about two days, **each** live stage about a week, poor care
lengthens evolution, and care actions must not affect other needs.

`PetGrowth.java`/`PetLife.java` own the testable timeline, care speed, palette and
size rules. Main uses the v41 whole-pet renderer, not the rejected limb/3D rigs.
Artwork pixels are unchanged; young/adult differences are size and clearly staged coat colour,
not separate new anatomical drawings. Adult size is fitted first, then baby
55% and young 77%, retaining the complete silhouette clipping protections.
Egg paths/gradients and coat filters are cached. All five stages/species reviews
are in `artwork/growth-stages`; these are offline stills, not phone recordings.

Egg/baby/young growth accumulates at 10..100% speed according to care; no earned
progress is erased. Adults live one week, following the user's "each stage"
timing answer, and die of old age rather than neglect. `PetState` delegates the
clock to the engine, retains existing prefs/cloud keys and generations, migrates
old evolution percentages, and persists incubation, stage progress and adult age.
Legacy adults receive a full new adult lifespan on upgrade/restore, even if saved
and reopened before first draw. Eggs/remembered pets remain Google backup eligible.
Initial restore-before-write protection and updater implementation are retained.
The updater now downloads inside the app with `HttpURLConnection`, follows the
GitHub Release redirect itself, then retries the matching APK committed on
`raw.githubusercontent.com`. This bypasses phones whose system DownloadManager
rejects both hosts. The completed private file still opens the normal installer.

Each action changes only its own need. Eggs show WARM/SOOTHE/TIDY/REST. Old-age
death archives a complete snapshot once; starting a replacement also preserves
the old pet as retired. Opening selection alone no longer resets the pet.
Memories merge by id across local/cloud copies and can be viewed in Settings
or the memorial screen. Explicit local/cloud reset choices retain their semantics.

`PetLifeCheck` and `PetSaveCheck` pass. The latter exercises the actual compiled
Kotlin model against in-memory Android preferences, including old cloud restore,
process-restart migration, egg backup, full archives/history and action wiring.
`WholePetCheck` passes 148,680 alpha-bound placement cases. Final `assembleDebug`
and `lintDebug` pass (zero errors, 132 existing/general warnings). The real-model
save/restore and lifecycle checks also pass against the final compiled classes.
APK package/version 49.0 and unchanged signing certificate are verified after
the release build is published; its SHA-256 is
`36227b7ec7c42932a793fda3f18d3a0a20c294ea62ee6c5a6d701c8bf0eca1cf`.
Publish the signed asset first
and only advance `update.json` after its public availability/checksum are checked.
Release commit and tag `v49` were pushed to `main`. The public
APK returns 200, its downloaded checksum matches the signed build, and latest
release resolves to v49. Root `update.json` will advertise 49 after verification.
See `artwork/growth-stages/README.md` for behavior, checks and limitations.

## Current request: original whole-pet animation for all pets — v41

The user clarified the rollback target with a v23 screenshot: restore that
whole-body cutout/cel setup for ALL five species, with clipping gone. This
supersedes the cat-locked/other-species rig work below. Do not resume the
separate-limb rigs, dog posture trial or rejected 3D prototypes.

`MainActivity.kt` now draws the original `walk_<kind>_<frame>.png` whole-pet
artwork for cat, dog, bunny, hamster and dragon. The motion recipe comes from
`bb2ddcc` (v32): v23-style movement with the subsequent source clipping repairs.
Artwork is unchanged, not repainted or regenerated. Dragon source cel 5 still
contained a duplicate, source-edge-clipped tail, so production skips that single
malformed cel: 11 unique dragon frames, 12 for each other kind. No duplicated
hold frame is inserted. Standing uses the same design/cel 0, with facing retained.

`PetSpriteLayout.java` measures every nonzero-alpha pixel once, uses exclusive
right/bottom bounds, and fits the full sequence's envelope in BOTH directions.
It reserves a travel lane, side gap, message clearance and play-bounce headroom
at every evolution size. Bottom visible pixels anchor to the grass. Cached
bitmaps are limited to the current species; drawing remains hardware/vsync
enabled. Historical rig source/assets stay in the repo but are not rendered.

`WholePetCheck` passes 198,240 actual-alpha placement cases across five species,
seven widths, six heights, four growth scales, both facings, five positions and
two bounce offsets. Every selected source silhouette has transparent borders.
This checks clipping, not professional animation quality or device FPS. The
production `UpdateCheckerCheck` also passes. Activity/sign-in/updater code,
UI/action/cloud entry points and the complete pet/save/cloud model match v40
byte-for-byte; no progress migration, uninstall, reset or cloud write is needed.

Source/build version is 41.0. `assembleDebug` and `lintDebug` pass (zero lint
errors; existing/general warnings remain). APK package/version and the unchanged
cloud signing certificate are verified. APK SHA-256 is
`7a3541c2b91660c25c4dc897bb8ceb1560a170cfde61d63a0b21205946770671`.
Commit `c92ba0e` contains the signed v41 APK and was pushed to `main` with
annotated tag `v41`. The public APK returns 200, its streamed checksum matches,
and `/releases/latest` redirects to v41. Root `update.json` now advertises 41,
advanced only after those checks. Keep this asset-first release sequence.
No phone/emulator is attached; device smoothness and user approval of the
restored movement remain unverified.

The rejected dog preview/tool remains recoverable outside the repository at
`/workspace/artifacts/rejected-dog-standing.xvMpqo`. It was never shipped.

Reproduce checks with JDK 21 on PATH:

```bash
check_classes=$(mktemp -d /tmp/whole-pet-check.XXXXXX)
javac -d "$check_classes" \
  app/src/main/java/com/example/shortsgesturecontrol/PetSpriteLayout.java \
  tools/WholePetCheck.java
java -cp "$check_classes" WholePetCheck app/src/main/res/drawable-nodpi
```

## Historical v40 anatomy-specific rigs (2026-10-08)

The latest user request supersedes the older review-only notes below: leave the
cat at its current livelier baseline and apply that recipe to every other pet,
adjusting to anatomy, especially dragon wings. The user is not claiming the cat
looks professional; do not interpret acceptance as a request for another cat
redesign. Earlier permission to keep publishing remains in effect.

All pets now use continuous 2D joint/mesh rendering. Dog, bunny, hamster and
dragon each have nine newly drawn separable layers derived from their existing
reference artwork. Their proportions, pivots, gait phases, strides/lifts and
head/tail/ear/wing arcs are species-specific. Dog uses diagonal step timing and
floppy ears; bunny uses paired timing and hind-push compression (grounded, not
authored flight); hamster uses compact strides and low lift; dragon has two
body-parented wing layers, its own neck placement and grounded gait. This is
the accepted layered recipe, not 3D or movie-quality animation.

`PetRig.java` owns geometry, parent transforms, depth, proximal cap masks and
two-bone leg meshes. `PetMotion.java` owns persistent world contacts, timed
recoveries, acceleration/braking and repeated roaming. A fourth recovery waits
for a support contact. Root travel is bounded by the remaining support's reach,
and recovery targets stay reachable even if a resize/action interrupts travel.
Original cat geometry, motion and all cat PNGs are unchanged. Existing companion
and whole-pet walk PNGs are retained, but non-cat runtime frame cycling is gone.
No designs are swapped between walking and standing.

Android decodes/masks each kind's artwork once into caches, including when
restoring/choosing a kind. It reuses leg vertex buffers and monotonic frame
timing; background time is not played back as animation. Complete appendage
bounds reserve a travel lane and scene-edge margin at every evolution size.
No UI, sign-in, local/cloud save/reset/restore, updater implementation, package
ID, SDK target or signing-key changes. The source diff is rendering only plus
the version bump. Keep protecting progress; do not uninstall/reset to test.

Source sheets, standing image, nine-second 60fps combined review, exporter and
reproduction commands: `artwork/pet-rigs/README.md`. Previews use production
geometry with Java2D, not recorded Android rendering; no phone/emulator is
attached, so measured device smoothness is unverified. Turning still mirrors
the rig while stopped. New layers follow the original designs but are not
pixel-identical originals.

`PetRigCheck` passes 180 simulated seconds for each of 36 species/fps/lane
combinations (30/60/120 fps; 24/90/224px lanes), including actions, zero travel,
120ms stalls and repeated turns/restarts. World-contact slip is zero; joint
reach excess stays below the 1 authoring-pixel tolerance, horizontal bounds
stay inside 256px from center, and at most three paws recover together. Existing
`CatGaitCheck`, `CatLayersCheck`, `CatMotionCheck`, `CatLivelinessCheck` and
`UpdateCheckerCheck` pass. These are regressions, not visual approval or phone
FPS measurements.

Release v40: `assembleDebug`/`lintDebug` pass (zero lint errors; existing/general
warnings remain). The APK's package/version and unchanged signing certificate
are verified. Commit `e6ef649` contains `updates/zoeys-pocket-pet-v40.apk` and was
pushed to `main` with annotated `v40`. GitHub workflow run `37815702797` succeeded
and the public release APK returns 200. Root `update.json` now advertises 40,
advanced only after the asset became available. Continue this asset-first
release sequence; never publish a manifest pointing to a missing APK.

## Historical cat polish trials — now the retained baseline

The trial-specific "do not publish" instructions below applied before the
latest request above. They are historical context, not the current workflow.

### Livelier walk trial after front-pair approval

The user approved the front-pair standing pose and its walking preview, but
found the movement too slow/stiff and asked for more travel, articulation,
head movement and tail movement. Keep the approved bind/rest geometry and all
PNGs. `CatMotion.WALK_SPEED` is 72 instead of 42 authoring px/s; stride is 60
instead of 48px and stance fraction .64 instead of .68. Paw lift rises from
15 to 26px using a broader cubic arch with zero contact velocity/acceleration.
Rigid trunk weight transfer crouches 5..8.4px; neck and tail counterbalance
the same distance-driven gait with broader arcs and subtle idle rotations.
No torso stretching or independently swapped art. First wait is 1.2s and pauses
are .65/.7s instead of .9/1.1s; starts still accelerate and stops still brake.

The reach guard now protects all four legs using the current shared body pose,
including interruptions, rather than only the upright front pair's rest reach.
Recovery speed has an explicit 380px/s safety bound with horizontal headroom
for paw lift. The existing contact/reach/attachment/roaming checks pass at
30/60/120 fps. `CatLivelinessCheck` additionally measures actual travel, lift,
head/tail arcs, joint-angle ranges and at least one supporting paw during
roaming. Measurements are not proof of professional quality or device FPS.

Latest review: `artwork/cat-rig-review/livelier-walking-preview.mp4`, 18s/60fps,
rendered from production code. Prior, approved-but-too-slow motion is preserved
as `front-pair-walking-preview.mp4`; `front-pair-standing.png` still records the
approved placement. Do not redesign the pet or return to the rejected 3D work.
Obtain visual approval of this livelier motion before tagging/publishing.
APK version, public v39 release/manifest, UI, other pets and local/cloud saves
are untouched. `assembleDebug`/`lintDebug` pass and the original signing
certificate is verified. Measured review ranges: 72px/s peak travel, 26px lift,
8.4px crouch, 5-degree head and 9.9-degree tail arcs, and 55..75 degrees of joint
articulation. At most three paws recover together. Full-bitmap bounds include
head, torso and tail; the observed horizontal envelope is 254.87/256px.

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

The current priority is safe continuation from the v87 app. Preserve
the working whole-pet renderer, persistence, lifecycle, scenery, audio, and
updater while making only well-validated improvements. The user remains
unhappy with the underlying walk-cel quality: pets can look floaty, rigid,
glitchy, or insufficiently connected to the ground. Do not claim animation is
solved without a device screenshot/video or another direct visual check.

This document is the complete project handover for a new chat. Historical
experiments are retained above for context; the current implementation and
release facts are the sections below.

## Repository and current state

- Repository: `https://github.com/bottMage/zoeys-pocket-pet` (public).
- Cloud checkout: `/workspace/zoeys-pocket-pet`.
- Local branch name is `work`; completed changes are pushed to remote `main` using `git push origin HEAD:main`.
- Latest pushed app commit: `366c4ae Compact care actions and expand CARE panel, prepare v87`.
- Latest public release/tag: `v87`.
- Current Android version: `versionCode = 87`, `versionName = "87.0"` in `app/build.gradle.kts`.
- The v87 release APK is published at
  `https://github.com/bottMage/zoeys-pocket-pet/releases/download/v87/zoeys-pocket-pet-v87.apk?download=87`.
- The v87 APK SHA-256 is
  `ef0d9d6b8da44fbb3524d88c3bf442ab5b0f375f791f98102b5c050b13d8244f`.
- The v79 care-button change was tested by the user on Zoey's phone and
  removed the repeated-OK-button freeze/ANR: it uses Android's built-in click
  effect rather than constructing a `ToneGenerator` on every tap. v79 did not
  change pet rendering, cloud saves, ambient audio, or lifecycle behavior.
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
8. Wait for the GitHub Release asset and confirm it is available, for example:

```bash
curl -I -L https://github.com/bottMage/zoeys-pocket-pet/releases/download/vN/zoeys-pocket-pet-vN.apk
```

The tag triggers GitHub Actions, which attaches the committed cloud-signed asset. GitHub API access from this cloud may be blocked, but public GitHub pages and normal `git push` work. The Actions page can be read publicly if diagnostics are needed.

The repository also contains `update.json`, which must point to the public APK
asset only after that asset returns HTTP 200. Commit and push the manifest
after the release asset exists, then verify the raw manifest with a unique
cache-busting query. GitHub can temporarily cache or return 404 for a release
URL without a query string; the published `?download=N` URL and a cache-busted
manifest check avoid treating that transient state as “up to date.”

## In-app updater implementation

`MainActivity.kt` contains `AppUpdateManager`.

- It checks on app launch (silent when no update), and via the **UPDATES** pill next to Reset.
- It reads the raw `update.json` manifest with cache-busting/no-cache headers
  and independently falls back to the public `/releases/latest` redirect when
  the manifest does not offer a newer build.
- Updates are optional: a dialog offers Download or Not Now.
- Android cannot silently install a normal app. The final Android package-installer approval is mandatory.
- The completed download is handed to Android's package installer with the APK
  MIME type on the app's main/UI thread. This should open the installer instead
  of leaving a file that the user must browse to manually, but Android's final
  approval remains mandatory.
- The in-app download retries the public committed APK when the primary release
  asset fails. A failed check/download must remain an error, not be reported as
  “up to date.”

Android may require the one-time “Allow from this source” permission for Zoey's Pocket Pet before it can hand off to the installer.

## Version label

v4 added a subtle `v<version>` label under the Reset button. It uses `BuildConfig.VERSION_NAME`. `buildFeatures { buildConfig = true }` is intentionally enabled in `app/build.gradle.kts`; do not remove it.

## Animation/art implementation and known shortcomings

The app draws a whole-body animation cel, not separately moving body parts:

- Source: `app/src/main/java/com/example/shortsgesturecontrol/MainActivity.kt`, primarily `PetGameView.drawPet`.
- Art: `app/src/main/res/drawable-nodpi/walk_<kind>_<0..11>.png` for cat, dog, bunny, hamster, and dragon; 60 PNG cels total.
- The earlier individual limb/rig assets were removed. Do not reintroduce them as “animation”; the user explicitly rejected image-part warping/independent layers and wants genuine whole-body animation.
- The pet selector uses `companion_<kind>.png` assets.

Historical mechanical motion fixes in v3:

- Corrected a mirror-direction bug. The art faces left by default, so it is mirrored only when travelling right.
- Reduced scene travel speed from `.22f` to `.070f`.
- Slowed cel timing from 70 ms to 105 ms.
- Removed resting whole-body bob.
- Added alpha-bound scanning per cel and anchors the lowest non-transparent pixel to `groundY`, rather than the PNG's transparent 512px border.

These are mechanical improvements, but the user still sees the animation as
cheap, rigid, floating, and disconnected from the ground. The underlying cels
themselves are likely not a coherent walk cycle / have inadequate foot
contacts. More timing tweaks alone will not create convincing animation. A
real fix requires replacement coherent animation cels (or a proper skeletal
animation authoring/rendering pipeline) designed for each creature, with
consistent anatomy, stance, direction, contact poses, and body weight. Keep
the user's requested cute creature feel, but do not copy Pokémon characters
or artwork.

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
