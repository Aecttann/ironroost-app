# Battle City

A Kotlin Multiplatform tank game for Android, iOS and the browser. The simulation, the UI and
the game data are shared; only storage, audio playback and screen control are per-platform.

## Modules

| Module        | What it is                                                                 |
|---------------|----------------------------------------------------------------------------|
| `:engine`     | Pure game logic — simulation, models, level parsing, ports. No Compose, no Android APIs. Runs and is tested on the JVM. |
| `:shared`     | Compose Multiplatform UI, packed resources, and the `expect`/`actual` platform layer. |
| `:androidApp` | Android host: one activity that calls `App()`.                             |
| `:webApp`     | Browser host: Kotlin/Wasm entry point plus the page shell — `index.html`, `styles.css`, the icon and the `portal.js` bridge. Also packages the CrazyGames upload. |
| `iosApp`      | iOS host: SwiftUI shell around `MainViewController()`.                      |
| `legacy-code` | The original single-module Android sources this game was extracted from. Reference only, not built. |
| `docs/assets-kit` | Provenance and source material for the art: legal note, kit README, spritesheets, unused dev-kit data. |

The dependency direction is one way: `androidApp`, `webApp` and `iosApp` → `:shared` → `:engine`.

## Building and running

```bash
./gradlew :androidApp:assembleDebug
```

iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run. Set `TEAM_ID` in
`iosApp/Configuration/Config.xcconfig` first.

Web development server:

```powershell
.\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun
```

The development bundle is unoptimised and starts several times slower than the shipping one,
so judge load time and frame rate on the production build:

```powershell
.\gradlew.bat :webApp:wasmJsBrowserProductionRun
```

CrazyGames Basic upload package:

```powershell
.\gradlew.bat :webApp:packageCrazyGamesBasic
```

Submission copy, controls and the QA handoff live in `docs/crazygames/`. The listing covers and
preview videos, and the tools that generate them from the game's own art and a recorded run, are
in `docs/crazygames/media/`.

The Google Play listing lives in `docs/play/`: store texts in the eight game languages, the icon,
feature graphics and phone screenshots rendered from the game's own sprites and maps, and the
answers to Play Console's app-content forms. `java docs/play/CheckPlayListing.java` checks it all
against Play's limits before upload.

## What the browser build needs from the page

`webApp/src/webShell/index.html` is a template, not a resource: `:webApp:stampAppVersion`
writes `releaseVersionName` into its `app-version` meta tag and only the stamped copy is packed,
so the About screen always names the build that was uploaded. Everything else the page needs —
`styles.css`, `icon.svg` and `portal.js` — lives in `webApp/src/wasmJsMain/resources`.

On CrazyGames the Data Module is the canonical key-value store for stage progress, settings,
daily state, collection counters and records. The first Data-enabled release migrates any keys
that only exist in the older prefixed `localStorage` save. Outside an enabled SDK environment the
same bridge falls back to `localStorage`, so the production ZIP also runs on localhost and as a
standalone web build. The CrazyGames submission must select **Yes, using the Data Module** or the
SDK will reject those calls.

`portal.js` is the only JavaScript in the project. Besides the CrazyGames SDK bridge it:

- waits for SDK v3 initialization and CrazyGames Data Module adoption before it starts the
  Kotlin/Wasm bundle, so the repositories cannot read a stale local save first;
- shows a "could not start" panel instead of an endless spinner when the browser has no
  WebAssembly GC or the bundle fails to load;
- swallows the arrow, space and paging keys so they cannot scroll the host page out from under
  the board;
- re-reads the device pixel ratio when the window moves to a display with a different density.

**Snapshot state does not cross a browser callback on Kotlin/Wasm.** A value written from a key
handler or read back inside a `withFrameNanos` loop can be the one the frame started with, which
left the keyboard, the fire button and every sound effect dead in the browser. Anything the
simulation reads per frame is therefore held in a plain field — see `TanksHeldInput` and
`TanksSoundBank` — with snapshot state kept only for what Compose has to recompose. Keep new
per-frame input on that side of the line.

## Tests

```bash
./gradlew :engine:testAndroidHostTest :shared:testAndroidHostTest
```

- `:engine` — the simulation and meta rules: fixed timestep determinism, brick quarters,
  independent co-op player slots, stage status, endless wave scaling and deterministic upgrade
  drafts, daily rewards, collection unlocks, records and level parser validation. The endless
  loop is driven for real rather than checked as rules: a wave is cleared in a corridor fixture,
  which is what pins the gate holding the board still, the queue refill, and each upgrade that
  pays out at the rollover.
- `:shared` — the packed resources: every stage parses and is playable, difficulty never goes
  backwards, no two stages share a map, every sprite and clip the code asks for is present,
  and every locale defines the same string keys. Also the input and sound rules the simulation
  depends on: last key wins, a finger beats the keyboard, focus loss drops the keys the browser
  will never release, and a clip asked for before the bank finished decoding still gets played.

On a Mac you can also run `./gradlew :shared:iosSimulatorArm64Test`.

## What needs a Mac

Kotlin/Native cross-compiles Apple klibs on any host, so `iosMain` is fully type-checked on
Windows and Linux:

```bash
./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64
```

What a macOS host is still required for:

- linking the framework (`linkDebugFrameworkIosSimulatorArm64` and friends) — needs the Xcode toolchain
- running `iosSimulatorArm64Test`
- building and running the Xcode project

## Game data

Stages live in `shared/src/commonMain/composeResources/files/battle_city/data/levels/prototype_13x13`
and are read through Compose Resources, so the same files serve both platforms.

Each stage is a 13x13 `grid` of tile characters:

| char | tile   | blocks tanks                        | blocks bullets                |
|------|--------|-------------------------------------|-------------------------------|
| `.`  | empty  | no                                  | no                            |
| `B`  | brick  | per surviving quarter               | yes                           |
| `S`  | steel  | yes                                 | yes, until the player hits level 4 |
| `W`  | water  | yes, unless the boat bonus is held  | no                            |
| `F`  | forest | no                                  | no                            |
| `I`  | ice    | no, but the tank slides             | no                            |
| `H`  | base   | yes                                 | ends the stage                |

`difficulty` is a 1..5 star rating that rises with the stage number. The engine uses it to
scale enemy fire and decision rates; the stage picker shows it as stars. Stage thumbnails are
drawn at runtime from `grid`, so there are no preview images to keep in sync.

All 35 maps are generated originals, validated for symmetry, a reachable base, clear spawns
and uniqueness. See `docs/assets-kit/LEGAL_NOTE.md` for the provenance of every asset
category, and read it before adding art from anywhere else.

## Embedding the game elsewhere

`:engine` defines the only things the game wants from a host, as ports:

- `TanksWallet` — what a run costs. The standalone app plugs in `FreePlayWallet`; a host app
  with its own economy can charge for a run by passing a real wallet and a non-zero
  `attemptCost` to `TanksViewModel`.
- `TanksProgressStore` — cleared stages, attempt counters, the sound setting.
- `TanksAnalytics` — attempt started, stage cleared. `NoopTanksAnalytics` by default.

`:shared` supplies the storage and audio implementations through `TanksPlatform`, resolved by
`rememberTanksPlatform()`. The browser host also plugs in a narrow `TanksPortal` bridge for
CrazyGames lifecycle, progress and level context without leaking JavaScript into the game UI.

## Modes and screens

`App()` keeps one view model across menu, game, daily reward, collection, records, settings and
about. There is no navigation library — the game is one screen and the rest are leaves off the
menu.

- **Campaign** — 35 unlockable stages, solo or two-player local co-op. The stage card holds the
  board for 1.5 s, then play starts automatically. Co-op uses WASD + Space for P1 and arrows +
  Enter for P2; touch controls remain solo because two pads do not fit a phone responsibly.
- **Endless** — a fixed comparable arena with escalating waves. A deterministic three-card draft
  between waves builds one of eight run upgrades; campaign and endless records stay separate.
- **Daily reward** — a UTC-day streak banks bonus lives for the next selected run and awards a
  collection card on day seven. A clock-rollback guard prevents reopening an old claim.
- **Collection** — persistent kill, power-up, campaign, feat and streak milestones.
- **Records** — separate personal campaign and endless top tens plus an optional CrazyGames
  leaderboard submission for endless when the portal issues a leaderboard key.
- **Settings / About** — sound, confirmed campaign-progress reset, asset origin and stamped build
  version.

The wide menu uses three columns at CrazyGames' short 16:9 iframe sizes; narrow portrait layouts
scroll instead of clipping actions below the canvas.

## Naming

"Battle City" is a Namco trademark and cannot be a public title, so the game ships as
**Ironroost** — iron for the tanks, roost for the eagle's nest you are defending.

The name is a coined compound on purpose. It replaced an earlier "Steel Eagle", which had to go
for two reasons that are worth keeping in mind before anyone proposes a new one:

- a paid, currently listed PC game already uses that exact title, so the same name in the same
  class of goods was a live conflict rather than a distant one;
- two ordinary English words are a weak mark either way. They are easy to collide with, hard to
  own, and they lose the search results to whoever got there first. A coined single token wins
  both: nothing else ranks for it, and fanciful marks get the strongest protection if the
  publisher ever registers one.

A web screen of Ironroost found no game, product or well-known brand using it. That is a screen,
not clearance — it cannot see a registered but unused mark. Before the name is committed to
anywhere expensive, run it through TMview, the USPTO search and WIPO Global Brand Database
filtered to **classes 9 and 41**, and sweep Steam, itch.io, Google Play and the App Store for
unregistered use.

Renaming again means `app_name` in the eight `composeResources/values*/strings.xml` files and in
`androidApp/src/main/res/values*/strings.xml` for the launcher label, plus the page shell title,
the `outputModuleName` and archive name in `webApp/build.gradle.kts`, the `ironroostPortal`
global and storage prefix in `portal.js`, and the listing art under `docs/crazygames/media/`.
The package, module and class names keep the `battlecity` spelling and are invisible to players.

## Not built yet

- The portal preview and real-device passes in `docs/crazygames/QA_CHECKLIST.md`, which need
  the publisher's CrazyGames account and hardware.
- A registry trademark search for “Ironroost” in classes 9 and 41. A web screen found nothing
  using the name, which is not the same as clearance. Also confirmation that the publisher owns
  the submitted build and metadata. See `docs/crazygames/SUBMISSION.md`.
- Difficulty is tuned by eye. The tests only guard the pathological cases — that a passive
  player is not wiped out before they could react. Real balance needs playtesting.
