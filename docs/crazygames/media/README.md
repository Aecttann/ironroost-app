# Listing media

Everything CrazyGames asks for alongside the build, and the tools that made it. All of it comes
from the game itself: the covers are drawn with the game's own 16×16 sprites, and the previews are
recordings of real runs of the packaged web build — no mockups, no footage from anywhere else.

## What ships

| File | Where it goes |
|------|---------------|
| `cover-landscape.png` | Landscape cover, 1920×1080 |
| `cover-portrait.png` | Portrait cover, 800×1200 |
| `cover-square.png` | Square cover, 800×800 |
| `preview-landscape.mp4` | Landscape preview, 1920×1080, 60 fps, 17.5 s, silent |
| `preview-portrait.mp4` | Portrait preview, 1080×1620 (2:3), 60 fps, 17.5 s, silent |

Both previews are gameplay from the first frame to the last, with nothing added: two players in
the endless arena, then a straight cut to two players on the forest stage of the campaign. The
covers are uploaded on their own and appear nowhere in the videos. There are no borders, black
bars, black transitions, store badges or mouse cursors; the only text is the game's own HUD.

## Rebuilding it

The videos record the packaged build, so build that first:

```powershell
.\gradlew.bat :webApp:wasmJsBrowserDistribution
```

Then, from the repository root, with the media server running in its own shell:

```powershell
node docs/crazygames/media/serve.js
```

**Covers** — open `http://localhost:8130/docs/crazygames/media/cover.html`, then run `saveAll()`
in the console. The board layout, the camera each format points at it, and where the tanks stand
are all at the top of that file, together with `LABEL_ZONES`: the top-left corner the portal
covers with its NEW / HOT badges, measured off the submission page's crop tool. `saveAll()`
refuses to write a cover whose title or tanks reach into it; `renderAll({ showZones: true })`
shades the zones in the preview.

**Previews** — record each route in both formats, then cut them together:

```powershell
node docs/crazygames/media/record-gameplay.js landscape endless-coop 30 --seed 1
node docs/crazygames/media/record-gameplay.js landscape campaign-coop 12 --seed 1
node docs/crazygames/media/build-preview.js landscape endless-coop:20.3-29.8 campaign-coop:2.5-10.5
```

Repeat with `portrait` for the other format; the same route and seed give the same run, frame
for frame, so the two videos show the same fights. `ffmpeg` has to be on the path. If Node is not
installed system-wide, the Kotlin Gradle plugin has already downloaded one:
`~/.gradle/nodejs/node-*/node.exe`.

`record-gameplay.js` launches its own headless Chrome on a throwaway profile — it never touches
your browser — plays a route from `routes.js` and leaves one PNG per frame in a temp folder, with
a `capture-<format>-<route>.json` beside this file pointing at it. `build-preview.js` joins the
named windows of those captures with straight cuts, adds nothing else, and refuses anything over
the portal's twenty seconds.

To find a seed worth recording, `--search 1-12` plays seeds without keeping frames and prints what
happened in each: a cleared wave, or the run lost and when. Seed 1 is the one shipped; its base
falls at 30.4 s, which is why the endless window stops at 29.8.

The routes reach their modes by clicking measured viewport fractions, because the menu is painted
on a canvas with nothing to query. Those fractions live in `MENU` in `capture.js` and only cover
the two capture sizes — **a menu change moves them.** `beginRun` checks which stage the engine
actually loaded and throws rather than recording the wrong mode. The solo `campaign` mode probes
down the centre column instead; it hides the keyboard from the menu while it does, so the probe
cannot switch on two players on its way to New game.

## Screenshots for before/after

`screenshots.js` photographs the built game at fixed moments — a first visit, the menu, the
stage-one card, six seconds into a solo run, the pause overlay — at 1280×720, 720×1080 and a 360×640 touch phone, on
the same virtual clock and seed as the previews, so two builds can be compared picture for picture:

```powershell
node docs/crazygames/media/screenshots.js shoot before
node docs/crazygames/media/screenshots.js shoot phase-1
node docs/crazygames/media/screenshots.js compare before phase-1
```

Shots land in `docs/quality/screens/<label>/` and are committed; `compare` writes labelled pairs to
`docs/quality/screens/<before>-vs-<after>/` (ignored by git, rebuilt on demand) and needs `ffmpeg`.
`--formats` and `--views` take comma-separated subsets. Every format starts on a fresh profile.
A fresh profile is a stranger, and a stranger skips the menu for stage one with the controls drawn
on the field; that is the `first-visit` view. Everything else — here, in `tour` and in
`record-gameplay.js` — is shot as a returning player: `launch({ visitor })` in `chrome-session.js`
defaults to `"returning"`, which writes the game's "lesson done" key into `localStorage` before
the page loads.
Besides the three default formats there are `hd` (a 1920×1080 desktop window) and `phone-land`
(the phone on its side, touch), for checking the game screen at its extremes.

`tour <label>` visits the screens off the menu — daily reward, collection, records and the nickname
dialog, settings and the reset question, about, the stage list from the pause menu — by keyboard:
Tab walks the controls in the order they are built, Enter presses one. The routes are the `TOUR`
table in the script; `--steps "run,pause,tab,shot:x"` tries an ad-hoc one while working out a new
route. `--dist developmentExecutable` points either command at the development build, which
`gradlew :webApp:wasmJsBrowserDevelopmentExecutableDistribution` produces in a few minutes instead of
a quarter of an hour — good for checking a screen, not for the shots that get committed.

The page's calendar reads 1 January 2026 from its first script on (`virtual-clock.js`), not only once
the clock is held, so the daily reward is always on its first claimable day in these shots.
The tour's last two routes change the save and so run last: `daily-claimed` claims the reward,
and `daily-behind` reloads with the calendar two days back (a `calendar:-2` step, kept in the
tab's `sessionStorage`) so the reward shows its "clock is behind" state.

### Why it is built this way

The first previews were grabbed in real time, and at 1080p the browser can encode about six
frames a second while the game draws sixty, so they kept a fraction of the frames, unevenly
spaced, and stuttered. Now the page runs on `virtual-clock.js`, installed ahead of the game's
own scripts: `requestAnimationFrame`, `performance.now`, `Date.now` and timers all read a clock
the recorder moves one sixtieth of a second per captured frame, however long that frame takes to
save. Every frame the game draws is in the video, and the video runs at exactly the game's speed.
The same file fixes `Math.random`, which the game draws each run's seed from, so with the inputs
scripted too a take is fully reproducible — which is what lets one seed search serve both
formats.

A script cannot see where the tanks are, so the co-op routes follow one rule that makes it safe
anyway: a seat never fires facing down or towards the middle. The first co-op takes, which fired
every way, shot their own base inside four seconds. The endless route parks each tank on the edge
column below a corner spawn, the only way out of those corners, and fires up it; `routes.js` has
the geometry.
