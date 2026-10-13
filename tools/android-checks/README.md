# Play-date rendering regression

`PlayDateRenderTest.java` exercises the production private view through
reflection and Android's native Canvas via Robolectric 4.14.1 / Android 34.
It uses original APK artwork and offline Firebase fixtures: no real users,
sign-in, cloud writes, or session acceptance are exercised.

Run this in an isolated copy of the Android project, with the current
production sources/resources copied unchanged. Put the test in
`app/src/test/java/com/example/shortsgesturecontrol/` and enable Android
resources in `testOptions`; add test-only JUnit 4.13.2 and Robolectric 4.14.1.
Do not add these dependencies to the shipped app. Select native graphics and
the cached instrumented Android 34/35 jars for offline execution.

The existing cloud fixture lives at
`/workspace/artifacts/dragon-cel-smooth/android-preview`; its native temporary
directory and jar cache are outside the repository. Run only the new test:

```bash
./gradlew --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=512m' testDebugUnitTest \
  --tests com.example.shortsgesturecontrol.PlayDateRenderTest
```

Run native rendering and release build/lint sequentially on the 8 GB worker
to avoid memory contention. Rendering output is under
`/workspace/artifacts/cloud-bubbles-v128`: both mixed perspectives, egg-only
regression, and live/live crossings. Original cels contain alpha-250..254
interior pixels, so tests preserve source alpha rather than imposing hard
opaque masks. Preview clips are encoded at 30 fps; they are not phone
recordings or measurements of device frame rate.

v128 also checks the themed cloud artwork for all four play labels, both
scene edges, independent timer expiry and movement-following anchors. It
renders `cloud-eggs.png` and `cloud-live-pets.png` through the production view.
Include the current `CloudSpeechBubble.kt` when copying production sources.
Set the native test executor heap to 1536MB on the current 8GB worker: the
pre-existing /tmp tmpfs uses nearly 5GB and a 3GB test heap can trigger OOM.

## Rain review (unpublished)

`RainRenderTest.java` captures the actual scene through the same offline native
fixture. Run `RainRenderTest.previewBaseline` only while the isolated source
is the published v128 renderer, before copying the new rain source. A guard
prevents capturing candidate footage as a false baseline. With the candidate
source (including `RainEffect.kt`) copied unchanged, select:

```bash
./gradlew --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=512m' testDebugUnitTest \
  --tests com.example.shortsgesturecontrol.RainRenderTest.previewCandidate \
  --tests com.example.shortsgesturecontrol.RainRenderTest.independentDropsAndSceneClipping \
  --tests com.example.shortsgesturecontrol.RainRenderTest.overcastCoverAndDayIntensity
```

Output: `/workspace/artifacts/rain-review-20261013`. Each version contains 180
day/live-pet and 180 night/egg scene PNGs plus full-screen stills. Encode at
30fps for review; do not regenerate/relabel the original baseline from the
candidate. Drop/clipping checks include direction, taper, bounded work,
splashes and repeatability at the same elapsed timestamp. No publishing,
version bump or updater change until the user specifically approves the rain.

The revised preview also needs `RainCloudCover.kt`. Candidate rendering now
writes `candidate-clouds/` to retain the initially accepted `candidate/` motion
preview. It covers day-only stronger rain, thick cloud ceilings in both scenes,
zero cloud metadata and dimmer rainy daylight. Cloud/pet shapes must remain
smooth; inspect both day/night stills before showing the clip.

For the latest screenshot-only cloud correction, select
`RainRenderTest.previewCloudStills` instead of `previewCandidate`. This writes
only day/night full-view and scene PNGs to `candidate-puffy-clouds/`, with the
latest irregular whole-cloud silhouettes; no video frames or encoding.
Keep the approved RainEffect unchanged. The user rejected the earlier wave
contours and has not yet authorized publication.

After the final puffy-cloud screenshot the user explicitly authorized v129
publication. For release verification, select the screenshot-only and two
rain-check methods plus the PlayDateRenderTest regressions; do not run the
old-baseline capture against current source. Test executor properties
`mochi.rain.output` and `mochi.playdate.output` can select new artifact folders
so earlier approved preview files are not overwritten. The isolated v129
fixture uses `/workspace/artifacts/rain-release-v129` and
`/workspace/artifacts/play-date-v129`; no video encoding is required.
