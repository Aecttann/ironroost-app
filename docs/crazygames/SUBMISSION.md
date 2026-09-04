# CrazyGames Basic submission

## Upload

Build the production package from the project root:

```powershell
.\gradlew.bat :webApp:packageCrazyGamesBasic
```

Upload `webApp/build/crazygames/steel-eagle-crazygames-basic.zip`. The archive contains a
root `index.html` and only relative references to game files. Do not upload a development
webpack build.

The archive is roughly 4.7 MB — 156 files, 13.04 MiB raw and 4.63 MiB compressed, well inside
both the 50 MB Basic ceiling and the 20 MB mobile target. Source maps are not built for the
production bundle, so the Kotlin sources are not shipped to players. Set the version the About
screen shows with `-PreleaseVersionName=…`; it defaults to `1.0.0`.

## Listing media

The five required files are in `docs/crazygames/media/`: three covers and two preview videos,
built from the game's own sprites and from recordings of real runs of this exact bundle. Sizes,
durations and the rebuild steps are in that folder's README.

## Portal fields

- **Title:** Steel Eagle
- **Technology:** HTML5 / WebAssembly
- **Primary category:** Action
- **Suggested tags:** Tank, Arcade, Retro, Singleplayer, 2 Player
- **Mobile optimized:** Yes
- **Orientation:** Responsive; landscape preferred, portrait supported
- **Content:** PEGI 12 compatible; stylized vehicle combat, no blood or graphic violence
- **Languages:** English. The Android/iOS builds contain seven additional translations, but the
  portal build intentionally pins English until a future localization pass maps CrazyGames
  locale data to the shipped resource sets.
- **External login:** None
- **Advertisements:** None in the Basic build
- **In-game purchases:** None
- **External links:** None
- **Progress save:** Select **Yes, using the Data Module from the CrazyGames SDK**. Campaign
  progress, settings, daily rewards, collection counters and personal records use it. The bridge
  falls back to browser-local storage only outside an enabled CrazyGames SDK environment.

### Short description

Defend the eagle base through 35 stages or build an unstoppable tank in escalating Endless waves.

### Full description

Steel Eagle is a top-down tank action game inspired by the feel of 1980s console arcades.
Protect your eagle base, break through brick defenses and outmaneuver enemy waves in a 35-stage
campaign, or enter Endless mode and choose a new build upgrade after every cleared wave. Play
solo or share one keyboard in two-player local co-op. Daily rewards bank lives for the next run,
while persistent collections and separate campaign and Endless records give every session a
goal. Every map, sprite and sound was created for this project. Progress follows signed-in
CrazyGames players through the Data Module and remains available to guests.

### Controls

- **Solo:** WASD or arrows to move; Space, Enter or Numpad 0 to fire
- **Two-player local co-op:** P1 uses WASD + Space; P2 uses arrows + Enter or Numpad 0
- **Touch:** On-screen directional pad and FIRE button; touch play is solo
- **Pause / resume:** P or the on-screen PAUSE / START button
- **Menus:** Mouse or touch

## Basic SDK integration

The build initializes CrazyGames HTML5 SDK v3 before invoking SDK methods and implements:

- `gameplayStart` on playable gameplay;
- `gameplayStop` on menus, pause, loading, stage summary and game over;
- `setGameContext` / `clearGameContext` for the current level;
- `reportGameCompletedPercentage` for campaign progression;
- the portal `muteAudio` setting with higher priority than the in-game sound preference.
- CrazyGames Data Module storage after awaited SDK initialization, including migration of an
  older prefixed local save when a Data key does not exist yet.

No ad, banner, login prompt or payment calls exist in the Basic build. Outside the CrazyGames
and localhost environments SDK calls are disabled and storage falls back to local browser data.

The optional CrazyGames leaderboard is deliberately limited to comparable Endless scores. It is
dormant until CrazyGames enables a leaderboard and the build is stamped with
`-PcrazyGamesLeaderboardKey=…`; personal campaign and Endless boards work without it.

The same bridge also handles the things a portal iframe demands of any HTML5 game: it stops the
arrow, space and paging keys from scrolling the host page, keeps the board scaled correctly when
the window moves to a display with a different pixel density, and replaces the loading spinner
with a readable message on a browser that cannot run WebAssembly GC.

## Ownership note

The public title is **Steel Eagle**. “Battle City” remains only in internal package/module names
and must not be used in the portal listing or cover art. Asset provenance is documented in
`docs/assets-kit/LEGAL_NOTE.md`. Before submission, the publisher still needs to perform a
trademark search for “Steel Eagle” and confirm ownership of the submitted build and metadata.
