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

## What's implemented (round 6 — languages, corners/throw-ins/free-kicks, movement quality)
- **`model/CommentaryLanguage.kt`** (new) + **`engine/SettingsStore.kt`** (new)
  — a **Settings** section on the setup screen now lets you pick the spoken
  commentary language: English, Español, Français, Deutsch, Português or
  Italiano. The choice is remembered locally (SharedPreferences) for next
  time. `MatchConfig` carries the selection through to the match.
- **`engine/Commentary.kt`** — rewritten around a per-language line-pack
  table instead of one hardcoded English set. `CommentaryGenerator` now
  takes a `CommentaryLanguage` and picks lines from that language's pack for
  every event type, including the three new ones below.
- **`audio/CommentaryVoice.kt`** — the on-device TextToSpeech engine is now
  initialized with the chosen language's locale (and picks a matching voice
  if the device offers more than one for it) instead of being hardcoded to
  English. Same offline, no-API-key approach as before.
- **`engine/Events.kt` / `engine/MatchEngine.kt`** — three new match events,
  each with its own trigger logic in the engine rather than just cosmetic
  text:
  - **Corner kicks** — roughly a third of blocked/deflected shots from
    inside the box now go behind for a corner instead of a routine keeper
    take; the nearest attacker heads to the corner arc to take it.
  - **Throw-ins** — a pass sprayed too close to the touchline can now run
    out of play, awarded to the defending side at the point it went out.
  - **Free kicks** — roughly one in five tackle attempts is now given as a
    foul instead of a clean challenge; the attacking side keeps the ball
    for a free kick rather than losing possession.
  - `MatchStats` tracks corners and free kicks per side, shown as new rows
    in the in-match stats panel.
- **`engine/MatchEngine.kt` — `moveTeamTowardShape`** — player movement is
  no longer one constant speed in a straight line to the target. It now
  eases (fast when far from its spot, settling like a jog on approach
  instead of snapping the last inch), varies pace by role (forwards/wingers
  noticeably brisker than center-backs and the goalkeeper), and adds a
  small stable per-player jitter so a settled player doesn't look frozen.
- **`ui/PitchView.kt`** — markers now draw a soft ground shadow (reads as
  standing on the pitch rather than floating) and a short motion streak
  trailing behind fast-moving players, stretching further the quicker
  they're covering ground — a cheap but effective way to sell pace and
  momentum on a 2D top-down pitch without a sprite/animation system.
- Corner/throw-in/free-kick restarts reuse the existing ball-position-driven
  movement logic rather than a separate set-piece system: moving the ball to
  the corner arc / touchline / foul spot is enough to pull nearby players
  toward it through the same `moveTeamTowardShape` pull each frame already
  applies, so no extra state machine was needed to get players reacting to
  the restart.

## What's implemented (round 5 — stadium sound + spoken commentary)
- **`audio/CommentaryVoice.kt`** (new) — wraps Android's built-in
  `TextToSpeech` engine to read every `CommentaryLine` out loud in English as
  it's generated (kickoff, goals, shots, tackles, saves, VAR, halftime/full
  time, plus the throttled build-up flavor lines). This uses the OS's own TTS
  engine, so it works fully offline with no API key and no extra dependency —
  the only requirement is that the device/emulator has English voice data
  installed (Android prompts for this automatically the first time TTS is
  used if it's missing). It tries to pick an `en-US` voice if the engine
  offers more than one, and falls back to the system default otherwise.
- **`audio/StadiumSound.kt`** — added `setDucked(Boolean)`, which lowers the
  looping crowd-ambience volume to 25% while the commentator is speaking and
  restores it to full when the line finishes, so the voice and the stadium
  bed sit together the way a real broadcast mix does instead of talking over
  each other at equal volume.
- **`ui/MatchScreen.kt`** — wired `CommentaryVoice` in alongside the existing
  `StadiumSound`: every commentary line pushed during the match is now
  spoken, ducking is driven by the TTS engine's utterance-progress callbacks,
  and a new **"Commentary: On/Off"** button next to Play/Pause and Replay
  lets the user mute the voice track independently of the crowd ambience
  (turning it off also stops whatever line is mid-sentence). Voice shuts down
  cleanly in `onDispose` alongside the ambience player.
- Nothing about the stadium ambience loop itself changed — it already
  started on kickoff and played whistle stings at kickoff/halftime/fulltime;
  this round layers the spoken voice on top of that existing sound design.

## What's implemented (round 4 — arbitrary splits like 8-1-1)
- **`ui/FormationEditorScreen.kt`** — no distribution constraint was ever
  enforced (any slot could already be reassigned to any position, duplicates
  included), so an "8 defenders / 1 mid / 1 attacker + 1 GK" shape — or any
  other split summing to 11 — was already buildable. What was missing was
  visibility: added a live `GK · DEF · MID · ATT` counter above the pitch
  that updates as you reassign roles, so you can see and confirm a split
  like 8-1-1 as you build it. If the goalkeeper count isn't exactly 1, a
  soft amber warning appears — it doesn't block saving, it's just a nudge,
  since the whole point of this screen is that any arrangement is allowed.

## What's implemented (round 3 — custom formations)
- **`ui/FormationEditorScreen.kt`** — full drag-and-drop formation builder.
  All 11 markers can be dragged anywhere on the pitch, and each one's role
  label can be reassigned independently via a tap-to-open dropdown (so you
  can build a back 3, a diamond, two keepers, a lone striker — genuinely any
  arrangement, not just the preset formations). Saving produces a
  `Formation("Custom", ...)` that then appears as a selectable "Custom" chip
  next to the presets on the setup screen, and can be re-opened and edited
  again at any time.
- **`ui/SetupScreen.kt`** — each team (yours and the opponent's) now has its
  own independent "Build/Edit Custom Formation..." entry point, so both
  sides can run fully custom shapes in the same match if you want.
- **`ui/PitchView.kt`** — `drawPitchLines` made `internal` so the formation
  editor can reuse the same pitch background instead of duplicating it.

## What's implemented (round 2)
- **`model/`** — all requested roles, color-coded by role group for the label
  text on markers; `Kit` (team jersey colors — this is the fix for the
  color-coding bug: Team A and Team B now render in their own distinct
  shirt/trim colors instead of sharing one role-based palette); `Strategy`
  (Possession / Counter-Attack / High Press / Park the Bus, each tuning the
  engine's pass risk, tempo, and defensive line); `Controller` (Human / Ai
  with difficulty / OnlineRemote placeholder); `Formation` presets.
- **`ui/SetupScreen.kt`** — pick your team name, jersey, formation and
  strategy; pick the opponent (Computer Medium, Computer Hard, local
  Pass & Play on one device, or Online — shown but disabled with an
  explanation, since it needs a backend); pick the opponent's name, jersey,
  formation and strategy; pick half length. Blocks kickoff if both jerseys
  clash.
- **`engine/MatchEngine.kt`** — strategy biases now actually change engine
  behavior (pass risk tolerance, tempo/attempt-rate, defensive line push);
  AI-controlled teams get a per-difficulty mistake rate and reaction-speed
  multiplier so Medium and Hard genuinely play differently. Goals no longer
  hit the scoreline directly — a `GOAL` becomes a `pendingGoal` and a
  `VAR_REVIEW` event, and only `resolveVar(confirmed)` (called by the UI)
  applies it to `MatchStats` or turns it into a `GOAL_DISALLOWED` event.
- **`ui/MatchScreen.kt`** — shows a full-screen VAR overlay (spinner, then
  "GOAL CONFIRMED" or "NO GOAL — OVERTURNED") whenever a goal is scored,
  auto-resolving after a couple of seconds (82% confirmed, like a real
  review skewing toward standing decisions) before play resumes. Whistle now
  also plays at kickoff (previously only halftime/fulltime).
- **`ui/PitchView.kt`** — markers are now drawn in each team's actual kit
  colors with the position label rendered on top (e.g. "RB", "CAM"), the
  goalkeeper gets a square marker so it's identifiable regardless of kit
  color, and the ball is a small multi-tone circle instead of a plain dot.
- **`engine/ProgressStore.kt`** + **`ui/ResultScreen.kt`** — local
  (SharedPreferences) credits: win/draw/loss awards credits, and crossing a
  threshold unlocks bonus kits (Gold, Pink Flash) that then appear as
  options on the setup screen.
- **`MainActivity.kt`** — simple Setup → Match → Result navigation with no
  extra nav library, so the whole loop (configure → play → see result →
  play again) works end to end.

## About the two big asks that need their own project phase
- **Real online multiplayer (another device/player).** This needs a
  server: matchmaking, an authoritative shared match state (so both devices
  agree on the same simulation), reconnect/disconnect handling, and likely
  accounts. `Controller.OnlineRemote` and the disabled "Online" option in
  the setup screen are placeholders so the rest of the app doesn't need to
  change shape when this gets built — but the networking layer itself is a
  separate project (e.g. Firebase Realtime Database/Firestore for a
  turn-tolerant simple version, or a lightweight WebSocket server on a small
  VM for a real-time version).
- **Illustrated player caricatures.** I can't generate custom character art
  in this environment. The pitch now draws clear jersey-colored, labeled
  markers (and a distinct GK shape) instead of plain dots, which is a
  reasonable placeholder and a clean seam to later swap in real sprite/art
  assets per position — `drawPlayerMarker` in `PitchView.kt` is the one
  place that would change.

## Known gaps / next steps (still deliberately out of scope)
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
