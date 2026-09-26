/*
 * Cuts captured runs into the preview video CrazyGames asks for.
 *
 *   node docs/crazygames/media/build-preview.js landscape endless-coop:20.3-29.8 campaign-coop:2.5-10.5
 *
 * After the format comes one or more segments, each a route record-gameplay.js captured for that
 * format and a window of it in seconds. The video is those segments in order, with straight cuts
 * and nothing added: it opens on the first frame of gameplay. Every segment plays at the rate it
 * was captured — one frame per sixtieth of a second of game time — so the video runs at exactly
 * the speed the game does.
 */
const { spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const FORMATS = {
    landscape: { width: 1920, height: 1080 },
    portrait: { width: 1080, height: 1620 }
};

// The portal cuts anything longer to twenty seconds.
const MAX_SECONDS = 20;

const format = process.argv[2] ?? "landscape";
const spec = FORMATS[format];
if (!spec) throw new Error(`unknown format ${format}; expected ${Object.keys(FORMATS).join(" or ")}`);

const segments = process.argv.slice(3).map(argument => {
    const match = /^([a-z-]+):([\d.]+)-([\d.]+)$/.exec(argument);
    if (!match) throw new Error(`segment ${argument} is not route:start-end, e.g. endless-coop:0.5-8`);
    const [, route, from, to] = match;
    const manifest = path.join(__dirname, `capture-${format}-${route}.json`);
    if (!fs.existsSync(manifest)) {
        throw new Error(`no ${route} capture for ${format}; run record-gameplay.js ${format} ${route} <seconds>`);
    }
    const capture = JSON.parse(fs.readFileSync(manifest, "utf8"));
    if (!fs.existsSync(capture.dir)) throw new Error(`the ${route} capture at ${capture.dir} is gone; record it again`);
    const start = Number(from);
    const end = Number(to);
    const length = capture.frames / capture.fps;
    if (!(end > start) || end > length) {
        throw new Error(`${argument} does not fit the ${route} capture, which holds ${length}s`);
    }
    return { start, end, capture };
});
if (segments.length === 0) throw new Error("name at least one segment, e.g. endless-coop:0.5-8");

const fps = segments[0].capture.fps;
if (segments.some(segment => segment.capture.fps !== fps)) throw new Error("segments were captured at different rates");
const total = segments.reduce((sum, { start, end }) => sum + end - start, 0);
if (total > MAX_SECONDS) throw new Error(`that comes to ${total.toFixed(1)}s; the portal keeps only ${MAX_SECONDS}`);

const output = path.join(__dirname, `preview-${format}.mp4`);
const { width, height } = spec;

// The captures are already the delivered size, so the scale is a no-op kept as a guard; should it
// ever do something, nearest-neighbour keeps the pixel art hard-edged.
const fit = `scale=${width}:${height}:flags=neighbor,setsar=1,fps=${fps}`;
const labels = segments.map((_, index) => `[v${index}]`).join("");
const filter = [
    ...segments.map(({ start, end }, index) =>
        `[${index}:v]trim=start=${start}:end=${end},setpts=PTS-STARTPTS,${fit}[v${index}]`),
    `${labels}concat=n=${segments.length}:v=1:a=0[out]`
].join(";");

const result = spawnSync("ffmpeg", [
    "-y", "-v", "warning",
    ...segments.flatMap(({ capture }) => ["-framerate", String(fps), "-i", path.join(capture.dir, "%06d.png")]),
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
