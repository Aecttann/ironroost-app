/*
 * Turns a captured run into the preview video CrazyGames asks for.
 *
 *   node docs/crazygames/media/build-preview.js landscape [startSeconds] [gameplaySeconds]
 *
 * Reads the frame list record-gameplay.js left behind, holds the matching cover for a beat, then
 * plays the trimmed run. The capture is deliberately smaller than the delivered size — see
 * record-gameplay.js — so this is where it is scaled up.
 */
const { spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const FORMATS = {
    landscape: { width: 1920, height: 1080, cover: "cover-landscape.png" },
    portrait: { width: 1080, height: 1620, cover: "cover-portrait.png" }
};

const COVER_SECONDS = 1.2; // Long enough to read the title, short enough to stay out of the way.
const FPS = 30;

const format = process.argv[2] ?? "landscape";
const start = Number(process.argv[3] ?? 0);
const gameplay = Number(process.argv[4] ?? 17);
const spec = FORMATS[format];
if (!spec) throw new Error(`unknown format ${format}; expected ${Object.keys(FORMATS).join(" or ")}`);

const listPointer = path.join(__dirname, `frames-${format}.txt`);
if (!fs.existsSync(listPointer)) {
    throw new Error(`no capture for ${format}; run record-gameplay.js ${format} first`);
}
const frames = fs.readFileSync(listPointer, "utf8").trim();
if (!fs.existsSync(frames)) {
    throw new Error(`the capture at ${frames} is gone; record ${format} again`);
}
const cover = path.join(__dirname, spec.cover);
if (!fs.existsSync(cover)) throw new Error(`missing ${spec.cover}; open cover.html and save the covers`);

const output = path.join(__dirname, `preview-${format}.mp4`);
const { width, height } = spec;

// Nearest-neighbour on the way up: the game is pixel art, and a smooth filter turns crisp bricks
// into mush. The cover is already the delivered size and only needs the pixel format matched.
const filter = [
    `[0:v]scale=${width}:${height}:flags=neighbor,setsar=1,fps=${FPS}[cover]`,
    `[1:v]trim=start=${start}:duration=${gameplay},setpts=PTS-STARTPTS,` +
        `scale=${width}:${height}:flags=neighbor,setsar=1,fps=${FPS}[play]`,
    `[cover][play]concat=n=2:v=1:a=0[out]`
].join(";");

const result = spawnSync("ffmpeg", [
    "-y", "-v", "warning",
    "-loop", "1", "-t", String(COVER_SECONDS), "-i", cover,
    "-f", "concat", "-safe", "0", "-i", frames,
    "-filter_complex", filter,
    "-map", "[out]",
    "-an", // The portal plays previews muted, and a silent track is one less thing to get wrong.
    "-c:v", "libx264", "-profile:v", "high", "-preset", "slow", "-crf", "18",
    "-pix_fmt", "yuv420p", "-movflags", "+faststart",
    output
], { stdio: "inherit" });

if (result.status !== 0) process.exit(result.status ?? 1);

const probe = spawnSync("ffprobe", [
    "-v", "error",
    "-select_streams", "v:0",
    "-show_entries", "stream=width,height,r_frame_rate",
    "-show_entries", "format=duration,size",
    "-of", "default=nw=1",
    output
], { encoding: "utf8" });
console.log(`preview-${format}.mp4`);
console.log(probe.stdout.trim());
