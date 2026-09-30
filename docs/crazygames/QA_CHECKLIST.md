# CrazyGames Basic QA checklist

## Automated release checks

- `:engine:testAndroidHostTest`
- `:shared:testAndroidHostTest`
- `:androidApp:assembleDebug` (guards the existing Android host)
- `:webApp:compileKotlinWasmJs`
- `:webApp:verifyCrazyGamesBasic` (root index, 1,500-file limit, 250 MB total limit, 50 MB compressed ceiling)
- `:webApp:packageCrazyGamesBasic`

All six in one line:

```powershell
.\gradlew.bat :engine:testAndroidHostTest :shared:testAndroidHostTest :androidApp:assembleDebug :webApp:packageCrazyGamesBasic
```

## Local browser matrix

Serve the **production** bundle, not the development one — the development build is several
times slower to start and says nothing useful about load time or frame rate:

```powershell
.\gradlew.bat :webApp:wasmJsBrowserProductionRun
```

Test both keyboard and pointer/touch input at these viewport sizes:

- 821×462, 907×510, 1077×606, 1216×684
- 1280×720, 1366×768, 1536×864, 1920×1080
- 800×450 and 1080×607 with touch emulation
- at least one narrow portrait viewport

For each representative layout verify PLAY reaches playable stage 1 in one click, movement,
fire, pause/resume, sound, restart, stage summary, game over, settings, About, Continue and
progress reset. On the short 16:9 sizes confirm the logo, PLAY, Endless and the row of icons are
all visible without scrolling; on portrait confirm the menu scrolls if it has to.
`node docs/crazygames/media/screenshots.js shoot qa --formats 821x462,1216x684,800x450t`
photographs any size (WIDTHxHEIGHT, a trailing t for touch) for a first look. Confirm arrow/Space keys do not scroll the iframe
and browser focus loss does not leave looping audio behind.

Run the new retention paths as well:

- start solo Endless, clear a wave, choose an upgrade and verify the HUD reflects its level;
- finish an Endless run and verify its wave/score appears only on the Endless records tab;
- on a fresh profile the page opens straight in stage one, not the menu, with the keys drawn
  over the player's tank (or the stick and FIRE blinking gold on touch); each group disappears
  once used, and a reload after moving and firing opens on the menu;
- a stage card holds for a moment and a tap or any key dismisses it at once;
- select two players and verify P1 uses WASD + Space while P2 independently uses arrows + Enter;
- with touch emulation on and no keyboard, verify the two-player option is disabled and explains
  why, and that New game and Endless both start a one-tank run. Then press any key and confirm the
  option becomes selectable — the browser build only guesses until it sees a real key;
- claim the daily reward, reload before starting a run, then start Campaign and Endless in
  separate clean-save passes; the banked lives must survive reload and be consumed exactly once;
- unlock at least one collection card, reload and confirm its progress remains.

### Browser-specific checks

Compose reaches the browser through a narrow bridge, and these are the places it has broken
before. Walk them on at least one desktop layout:

- **Keyboard after a button.** Tap the on-screen PAUSE, resume from the overlay, then steer with
  the arrows and WASD. The on-screen pad must light up in the direction being held — that is the
  same value the simulation steers with, so a dead pad means dead keys.
- **Fire.** Space and the FIRE button must both put a shell on the board.
- **Both at once.** Hold a direction on the pad while pressing a key: the finger wins, and
  letting go hands control back to the key still held.
- **Sound from the first card.** The stage-one card must play the jingle even though the clips
  are still decoding when it appears, and shots, hits and the engine rumble must be audible once
  the run starts.
- **Held keys on focus loss.** Click outside the frame while holding a direction; the tank must
  stop rather than run on with a key the browser will never release.
- **Unsupported browser.** With `WebAssembly.validate` stubbed to return `false` in the console
  before load, the page must show the "could not start" panel instead of a spinner that never ends.
- **About names the build.** The version on the About screen must match `releaseVersionName`
  from the packaged build.
- **Nothing extra shipped.** The distribution must contain no `.map` files and no absolute paths.
- **SDK-before-game ordering.** Throttle the network: `ironroost.js` must not be requested until
  SDK initialization and Data Module adoption have settled. A returning cloud save must be
  visible on the first rendered menu, without a second reload.

## CrazyGames Developer Portal preview

- SDK initialization reports `local` locally and `crazygames` in portal preview.
- The first `gameplayStart` occurs only when the stage becomes playable.
- Pausing, summaries, game over and returning to menu produce `gameplayStop`.
- `?muteAudio=true` prevents all sound even when in-game Sound is On.
- The submission's Progress Save option is **Yes, using the Data Module**. Reload restores stage,
  daily/collection state and both personal records boards. Test once as a guest and once signed in
  on a second browser/device.
- If a leaderboard key has been issued, an Endless game over logs one valid `submitScore` call in
  the leaderboard QA tool; campaign scores must not be submitted to the portal board.
- No ad UI or blocked ad flow is visible during Basic Launch.
- Reload restores the next unlocked stage.
- There are no console errors, absolute game-file paths, mixed-content requests or third-party
  assets. The CrazyGames SDK is the only external script; a browser opened directly on the game
  may still probe `/favicon.ico` and get a 404, which is harmless and produces no console error.
- Test on current Chrome and Edge, including a 4 GB Chromebook-class device if available.

Portal preview and real-device checks require the publisher's CrazyGames account and hardware;
record their results here before pressing Submit for review.

## Mandatory listing media

All five files are built and sitting in `docs/crazygames/media/`, along with the tools that made
them and instructions for rebuilding after an art change:

| Requirement | File | What it is |
|-------------|------|------------|
| Landscape cover 1920×1080 | `cover-landscape.png` | 1920×1080 |
| Portrait cover 800×1200 | `cover-portrait.png` | 800×1200 |
| Square cover 800×800 | `cover-square.png` | 800×800 |
| Landscape video, 1080p 16:9, 15–20 s, muted | `preview-landscape.mp4` | 1920×1080, 60 fps, 17.5 s, no audio track |
| Portrait video, 1080p 2:3, 15–20 s, muted | `preview-portrait.mp4` | 1080×1620, 60 fps, 17.5 s, no audio track |

Both videos are recordings of real runs of the packaged build from the first frame to the last,
with no cover or anything else added. The covers carry the title and nothing else, the videos
carry only the game's own HUD, and there are no borders, black bars, store badges or a mouse
cursor anywhere.

Check before uploading, since these are the rules a reviewer applies: only the title may be added
as cover text, and no borders, “Play Now”, store/social icons, copyrighted material, black bars
or a visible mouse cursor.
