# Egg and growth lifecycle — v42

The user asked for an egg first, then the existing pet design as a small baby,
followed by young and adult stages that visibly grow and change colour. They
specified about two days in the egg, a week per live stage depending on care,
and eventual death from old age. The final timing reply said **each** stage
should take about a week; adult life is therefore one week too.

`all-stages.png` and the five species rows are offline Java2D reviews of the
actual whole-body cel 0, production bounds, growth sizes and colour matrices.
They are not Android screenshots or separate runtime animation assets. Growth
retains the existing silhouette/animation: the changes are size and modest
coat colour, not newly drawn adult anatomy. Baby colour is unchanged; young and
adult colour shifts are deliberately visible in the UI. Size is
55%, 77% and 100% of the safely fitted adult width, so phone scenery fitting
cannot collapse all stages to the same size. Original PNG artwork is untouched.

Eggs have species colours, slow rigid rocking and a small lift pulse. The dragon
uses a twig nest; the other four use straw bedding. Paths/gradients are cached;
no image generation or per-frame bitmap edits are used. Egg illustration in the
review follows Android's geometry; motion is shared in `PetGrowth`.

`PetLife` is the production clock and care engine. Well-cared eggs hatch after
about 48h, babies grow after a week, young pets grow after another week, and
adults live a further week. Current care scales growth speed from 10% to 100%;
poor care lengthens egg/baby/young time without erasing earned progress. Time is
simulated on reopening, in bounded hourly chunks; the closed app does not run
or sync a background service. Care actions affect only their matching metric:
feed/warm -> hunger/warmth, play/soothe -> joy/comfort, bath/tidy -> clean/nest,
sleep/rest -> energy/rest. They give no direct evolution bonus or cross-effects.

Existing local/cloud keys, package, auth and signing key are retained. Existing
generations stay intact; old evolution percentages migrate into the longer
stages. Legacy adults start their new adult death clock at upgrade/restore,
including a save/process restart before the first draw. They cannot immediately
die due to an old backup timestamp. Eggs and remembered pets are backup eligible;
cloud writes remain gated on the first successful restore read.

Natural death archives a full snapshot once. Replacing a live pet also archives
it as retired; entering the selection screen alone no longer discards it.
Memories merge by id when reading cloud snapshots, including an older backup.
Unreadable history strings have local recovery copies. Current progress and
history share the existing Google backup document. Memories are available in
Settings and on the memorial screen. Explicit reset choices still clear the
data the user selects.

Checks:

- `PetLifeCheck`: timing, care-dependent growth, isolated actions, offline aging,
  one-shot old age, legacy adult safety, legacy evolution and baby colour.
- `PetSaveCheck`: the real compiled Kotlin save model, in-memory preferences and
  a JVM colour stub. Covers local/cloud compatibility, restore-before-draw death
  clock safety, egg backups, full memorials, history merges and actual action wiring.
- `WholePetCheck`: 148,680 placement cases using actual selected cel alpha, both
  facings, growth stages, scene ends and play bounce. The malformed dragon cel
  excluded in v41 stays excluded.

Use JDK 21 and `tools/GrowthStagePreview.java` with `PetGrowth.java` and
`PetSpriteLayout.java` to reproduce the PNGs. `PetSaveCheck` additionally uses
the compiled app classes, Android SDK interfaces, Kotlin stdlib and an org.json
JVM jar, with `tools/save-check-stubs/android/graphics/Color.java` first on the
test classpath. The test stub is outside app source and is not packaged in APKs.
No phone/emulator is attached; neither the stills nor math checks measure phone
FPS or establish user approval of the appearance.
