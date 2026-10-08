# Zoey's Pocket Pet

A gentle, stylized virtual-pet game made for Zoey.

## Current animation

All five species now use continuous 2D rigs instead of cycling whole-pet walk
images. The cat baseline is retained; dog, bunny, hamster and dragon have their
own anatomy, step proportions and appendage parenting, including dragon wings.
See [rig sources, preview and checks](artwork/pet-rigs/README.md).
Local/cloud save behavior and account restoration are unchanged by this update.
Current operational and release instructions are in [HANDOVER.md](HANDOVER.md).

## First playable version

The following describes the original prototype, not the current UI or account
features.

- Mochi is Zoey's LCD/pixel-style Tamagotchi pet, drawn locally with chunky sprite blocks and a limited palette.
- New eggs can hatch into original animal choices: cat, dog, bunny, hamster, or dragon.
- Feed, play, bath, and sleep buttons change Mochi's needs and mood.
- Hunger, joy, energy, and cleanliness are saved between launches and gently decay over time.
- The needs use realtime wall-clock catch-up while the app is closed: roughly 8% hunger, 5% joy, 6% energy, and 3.6% cleanliness per hour.
- Pets grow from baby to young to teen to evolved; good average care makes growth more likely.
- Evolution is deliberately long-haul: the first form needs about 12 real hours and sustained care; the next form needs about 72 real hours and stronger care conditions.
- Evolution progress shows age, care history, and the current requirement so the result feels earned rather than instant.
- The visible **RESET** button in the header or **NEW PET** button in the check-in panel starts a new egg, name, and look selection.
- Feed, play, bath, and sleep show an immediate reaction as well as changing the pet's needs.
- No camera, accessibility service, network connection, or account is needed.

## Build in Android Studio

1. Open this folder in Android Studio and allow Gradle sync.
2. Run the `app` configuration on an Android phone or emulator, or choose **Build > Build APK(s)**.
3. Install `app/build/outputs/apk/debug/app-debug.apk` manually if needed.
