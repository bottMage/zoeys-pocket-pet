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
./gradlew --offline --no-daemon --max-workers=2 testDebugUnitTest \
  --tests com.example.shortsgesturecontrol.PlayDateRenderTest
```

Run native rendering and release build/lint sequentially on the 8 GB worker
to avoid memory contention. Rendering output is under
`/workspace/artifacts/play-date-v127`: both mixed perspectives, egg-only
regression, and live/live crossings. Original cels contain alpha-250..254
interior pixels, so tests preserve source alpha rather than imposing hard
opaque masks. Preview clips are encoded at 30 fps; they are not phone
recordings or measurements of device frame rate.
