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
| `preview-landscape.mp4` | Landscape preview, 1920×1080, 30 fps, 18.2 s, silent |
| `preview-portrait.mp4` | Portrait preview, 1080×1620 (2:3), 30 fps, 17.7 s, silent |

Both previews open on the matching static cover for just over a second, then cut to gameplay, as
the portal requires. The title is the only text anywhere in the set; there are no borders, black
bars, store badges or mouse cursors.

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
are all at the top of that file.

**Previews** — record a run, then cut it:

```powershell
node docs/crazygames/media/record-gameplay.js landscape 26
node docs/crazygames/media/build-preview.js landscape 0.5 17
```

`record-gameplay.js` launches its own headless Chrome on a throwaway profile — it never touches
your browser — plays a fixed route from `capture.js`, and leaves the frames in a temp folder.
`build-preview.js` takes the seconds you name out of that capture, scales them up, puts the cover
in front and writes the MP4. Repeat with `portrait` for the other format. `ffmpeg` has to be on
the path.

Watch the fps line `record-gameplay.js` prints. Anything near twenty is a usable take; a much
lower number means the capture stalled and the run is worth repeating.

### Why it is built this way

Recording a WebGL canvas turned out to be the hard part. `MediaRecorder` stops feeding its stream
two or three seconds into a take on a headless GPU, and the DevTools screencast stops after about
seven, so both produce a few seconds of video and then a still frame. Grabbing the canvas and
encoding a JPEG per frame inside the page keeps up with the game and never stalls, which is what
`capture.js` does. The take is captured at 1280×720 and scaled up afterwards, because the browser
manages roughly twenty-four frames a second at that size and about ten at 1600×900 — smooth beats
sharp, and nearest-neighbour scaling of pixel art costs almost nothing.

The run in `capture.js` is deliberately cautious. An earlier route that charged up the board lost
all three lives inside ten seconds and ended the take on the game-over screen.
