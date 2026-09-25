# Football Tactics Simulator (v1 core loop)

A Kotlin + Jetpack Compose Android project implementing the tactics-teaching
core loop we scoped: formation board → rule-based match engine → replay →
analytics → commentary. **No scoreline-only mode** — score is included, and
it's a byproduct of the engine's shot/xG events rather than a separate system.

## ⚠️ Important: this has not been compiled or run

I don't have an Android SDK or network access in this environment, so I could
not build an APK or verify this compiles cleanly in Android Studio. The code
is written carefully and the architecture is real, but treat this as a strong
first draft to open, build, and fix up in Android Studio — not a tested
binary. Most likely friction points: dependency version bumps (I used recent
but not bleeding-edge AGP/Compose/Kotlin versions) and any typos Android
Studio's compiler catches that I couldn't.

## How to open it
1. Install Android Studio (Koala/Ladybug or newer).
2. Open this folder as a project (File → Open → select `football-tactics-simulator`).
3. Let Gradle sync — it will download the AGP/Kotlin/Compose versions pinned
   in `build.gradle.kts` files.
4. Run on an emulator or device (min SDK 24).

## What's implemented
- **`model/`** — `Position` (all requested roles: GK, RB, LB, CB, CDM, CM,
  CAM, LM, RM, LW, RW, ST/CF, RWB, LWB, etc.), color-coded by role group;
  `Player`, `Team`, and `Formation` presets (4-4-2, 4-3-3, 3-5-2).
- **`engine/MatchEngine.kt`** — the rule-based tick engine: movement toward
  formation "zones" biased by ball position, a pass-selection heuristic
  (forward progress vs. distance), shot/xG rolls in the box, turnovers, and
  automatic halftime end-swap (mirrors every player's zone left/right).
- **`engine/Events.kt`** — every tick emits/stores a `MatchFrame` (positions +
  ball + event), and `MatchStats` derives score, possession, xG, passes and
  chances purely from the event log — nothing is tracked twice.
- **`engine/Commentary.kt`** — templated, analytical commentary lines
  triggered by the same events, tiered so not every pass produces a line.
- **`ui/PitchView.kt`** — Compose Canvas pitch renderer with color-coded dots.
- **`ui/MatchScreen.kt`** — timer with selectable half length (5/10/15/20 min
  representing a 45-min half), play/pause, live scoreboard, stats panel,
  scrolling commentary feed, and a replay scrubber over stored frames.
- **`audio/StadiumSound.kt`** — loops ambience on kickoff, whistle stings at
  halftime/fulltime. **Placeholder silent files are in `res/raw/`** —
  replace `stadium_ambience.mp3` and `whistle.mp3` with real royalty-free
  audio; the code fails silently if they're missing/invalid so it won't crash.

## Known gaps / next steps (deliberately out of v1 scope)
- **Drag-and-drop formation editing** — formations are currently fixed
  presets (4-4-2 vs 4-3-3). Adding manual drag-to-reposition on the pitch is
  the natural next feature and slots into `PitchView` + a new edit mode.
- **Heat maps / pass network visuals** — the data is already being logged
  (`MatchFrame.homePositions`/`awayPositions`, and pass events with
  `secondPlayerId`), but there's no rendering for them yet.
- **Formation picker UI** — engine supports any `Formation`, but the screen
  doesn't yet expose a formation-switcher control.
- **Stamina-driven visible fatigue** — stamina decays and slows movement, but
  there's no on-screen fatigue indicator yet.
- **Tuning** — pass/shot/tackle probabilities are hand-picked starting
  values (see constants in `MatchEngine.kt`); expect to tune these once you
  can watch it run, to get match flow that "feels" right.

## Design notes
The movement/passing/shooting logic is intentionally **rule-based, not
physics or ML** — this was a deliberate choice from our earlier discussion:
it's tunable and keeps tactical cause-and-effect legible (e.g. "this
formation change opens this passing lane"), which a black-box or full
physics sim would obscure.
