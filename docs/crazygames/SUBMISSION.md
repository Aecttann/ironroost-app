# CrazyGames Basic submission

## Upload

Build the production package from the project root:

```powershell
.\gradlew.bat :webApp:packageCrazyGamesBasic
```

Drag the folder `webApp/build/crazygames/ironroost-crazygames-basic` onto the portal's upload
page; the portal takes a folder, not an archive. The task rebuilds that folder from scratch
every run, so nothing from an earlier build can end up in it. It has a root `index.html` and
only relative references to game files. Do not upload a development webpack build.

The build is roughly 7 MB — 156 files, 15.00 MiB raw and 6.93 MiB compressed (the menu and battle
music are most of the growth), well inside both the 50 MB Basic ceiling and the 20 MB mobile
target. Source maps are not built for the
production bundle, so the Kotlin sources are not shipped to players. Set the version the About
screen shows with `-PreleaseVersionName=…`; it defaults to `releaseVersionName` in `gradle.properties`.

## Listing media

The five required files are in `docs/crazygames/media/`: three covers and two preview videos,
built from the game's own sprites and from recordings of real runs of this exact bundle. Sizes,
durations and the rebuild steps are in that folder's README.

## What changed since the first review

The first submission was declined on overall quality. For the resubmission field, in short:

> Rebuilt the whole presentation. New pixel-art identity: our own tanks, tiles, base (an iron
> egg in a steel nest), bonuses and effects, drawn for this game. A pixel UI with its own font
> and logo, an arcade menu with a live battle behind it, a framed board with a side-panel HUD and
> proper touch controls. Screen shake, debris, score pop-ups and a stage curtain; menu and battle
> music with separate volume sliders. A first visit starts straight in stage one with the
> controls shown on the field. A friendlier first stage, and a faster board on low-end phones.

The detailed record, phase by phase with before/after screenshots, is
`docs/quality-update-plan.md` and `docs/quality/screens/`.

## Portal fields

- **Title:** Ironroost
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

Guard the iron egg in its steel nest through 35 stages, or build an unstoppable tank in Endless waves.

### Full description

Ironroost is a top-down pixel-art tank shooter. A flock of iron crow tanks rolls in from the top
of the map, and your job is to guard the iron egg in its nest at the bottom. One hit on the egg
ends the game, however many lives you have left.

Your first visit drops you straight into stage one with the keys drawn next to your tank; they
fade away as soon as you use them.

The campaign has 35 stages. Brick walls break when you shoot them, but steel only gives way once
your tank is fully upgraded. Water blocks the road, ice makes you slide, and trees hide anything
under them. Destroy a flashing tank and it drops a bonus: a shield that makes you invulnerable
for ten seconds, steel plates that wall the nest in, an hourglass that stops the enemy in its
tracks, a bomb that destroys every enemy tank on the screen, a star that makes your tank
stronger, and a few others. Every 20,000 points earns an extra life.

Endless mode is a single arena with waves that keep getting tougher. After each wave you pick an
upgrade: faster reload, a second shell in the air, extra armor, shells that bounce off steel or
break right through it, a steel wall round the nest, or a spare life. How far you get depends a
lot on what you take.

Both modes can be played with a friend on the same keyboard. Hit your partner by accident and
their tank just freezes for a moment instead of blowing up.

The daily reward gives you extra lives for your next run. There's also a collection to fill and
personal records to beat in both modes, and the campaign saves your progress, so you can always
pick up from the next stage.

### Controls

Move: WASD or arrow keys (ZQSD also works on AZERTY keyboards)
Shoot: Space
Pause: P

Two players on one keyboard:
Player 1: WASD to move, Space to shoot
Player 2: arrow keys to move, Enter or Numpad 0 to shoot

Touch screen: joystick on the left to move, FIRE button on the right to shoot, pause button on
screen. Two-player mode needs a keyboard.

Not for the portal field: the touch pad drives player one only, so on a device with no keyboard
the menu shows the two-player option disabled with the reason. Pressing any key enables it, since
that is the first moment the browser build can tell a keyboard is there.

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
with a readable message on a browser that cannot run WebAssembly GC. On touch devices it has the
game draw at 1x and lets the browser scale the picture up: at an iPad's native 2x the game ran as
a slideshow in the CrazyGames app.

## Ownership note

The public title is **Ironroost**. “Battle City” remains only in internal package/module names
and must not be used in the portal listing or cover art. Asset provenance is documented in
`docs/assets-kit/LEGAL_NOTE.md`.

The title is a coined compound chosen so that nothing else competes for it. An earlier working
name, “Steel Eagle”, was dropped because a currently listed commercial PC game uses that exact
title — the same name for the same class of goods — and because two ordinary words would have
left the game invisible in search behind it.

Still open before submission:

- A registry search for “Ironroost” in **class 9** (game software) and **class 41** (online
  game services) via TMview, the USPTO search and WIPO Global Brand Database, plus a sweep of
  Steam, itch.io, Google Play and the App Store for unregistered use. A web screen turned up no
  game, product or known brand under the name; that is a screen, not clearance, and it cannot
  see a mark that is registered but unused.
- Confirmation that the publisher owns the submitted build and metadata.
- The manual passes in `QA_CHECKLIST.md`: the local browser matrix, and the portal preview and
  real-device checks that need the publisher's CrazyGames account.
