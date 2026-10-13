# Color Block Jam Clone (`color_black_jam`)

A from-scratch Android re-implementation of the "Color Block Jam" style puzzle, written for a
reverse-engineering study. No assets, data or code from the original APK are used; the mechanics were
derived by playing the installed game and reading class names in its IL2CPP metadata.

## Gameplay (as implemented)

- Rectangular grid board with a frame. Coloured polyomino blocks (1x1 up to 3x3 shapes) are dragged
  freely in 2D; they slide and collide with other blocks, static wall cells and the frame.
- Gates are coloured segments of the frame. A block leaves the board when it is pushed into a gate of
  its colour and its whole span fits inside the gate segment. The level is won when every block is gone.
- Countdown timer per level; running out fails the level (retry is free, lives are unlimited for now,
  see `Progress.LIVES_UNLIMITED`).
- Gimmicks: ice doors (gate opens after N blocks exited), frozen blocks (unmovable for N exits),
  hidden-colour blocks ("?" until N exits), timer-bomb blocks, wall cells.
- Boosters: Freeze Timer (10 s, unlock level 6) and Hammer (remove one block, unlock level 8);
  continue after time-out with coins. 40 coins per level.
- Level-complete popup only every 10th level; other levels flash a banner and continue automatically.
- 700 levels: 1-6 are hand-authored tutorial boards; 7-700 are generated deterministically from the
  level number by `LevelGenerator` (reverse construction guarantees solvability; the generator also
  records a solution that the unit tests replay through the real engine).
- Generator tuning follows statistics measured on the original game's own level data (board sizes
  6x6-9x9, 15-30 blocks per level, shape mix 1x2 48% / 1x1 30% / small L and 1x3 7% each / 2x2 3%,
  6-10 gates of width 1-3, ~3 min time limit (original: ~2 min), ~20% "hard" levels, cut corners and holes from level 6, frozen
  blocks from 10, timer bombs from 120; 301-700 use the original's smaller 6x7-8x8 boards with 17-22
  blocks, 6-8 gates and 30-40% hard levels). Hidden-colour blocks appear from level 240 here although the
  original introduces them at 701. No level layout is copied; see `kang_work/cbj_analysis/ANALYSIS.md`.

## Layout

```
app/src/main/java/com/kang/colorblockjam/
  model/Types.kt     BlockColor, Side, Shape, Block, Gate, Move, Level
  model/Board.kt     game engine: continuous drag with collision sliding, gate exit, ice/frozen/hidden/bomb, timers
  model/Levels.kt    hand-made levels 1-6 + LevelGenerator (levels 7-300)
  ui/GameView.kt     in-game screen (HUD, board, blocks, gates, boosters, overlays) on a Canvas
  ui/HomeView.kt     home screen with "play next" button and a scrollable 300-level grid
  Progress.kt        SharedPreferences progress: unlocked level, coins, booster counts
  MainActivity.kt    swaps HomeView/GameView; `--ei level N` intent extra jumps into a level
app/src/test/...     JUnit: engine behaviour, 300-level well-formedness, solution replay, DFS cross-check
```

## Build / test / install

```bash
cd color_black_jam
./gradlew assembleDebug                       # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                   # JVM unit tests (no device needed)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kang.colorblockjam/.MainActivity --ei level 45   # debug: open level 45 directly
```

Cleaning build outputs: `./clean.sh` lists the git-ignored files (build/, .gradle/, .kotlin/, app/build/, ...),
`./clean.sh -y` deletes them (`--sdk` also removes local.properties, `--all` additionally removes untracked
files that were never added to git, after an explicit confirmation).

Toolchain: Gradle 8.13 wrapper, AGP 8.11.2, Kotlin 2.0.21, compileSdk 35, minSdk 24, JDK 17+
(`local.properties` points at `/home/kd3/Android/Sdk`). No AndroidX runtime dependency.

## Tuning knobs

- `Progress`: coin rewards/costs, unlock levels, `LIVES_UNLIMITED`.
- `Board`: `GATE_TOLERANCE` (alignment slack when entering a gate), `AUTO_EXIT_DEPTH`.
- `LevelGenerator.spec()`: board size, block count, shape pool, gimmick counts and time per level tier.
