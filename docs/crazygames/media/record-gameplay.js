/*
 * Records a real run of the built web game and writes it next to this file as WebM.
 *
 *   node docs/crazygames/media/serve.js            # in one shell
 *   node docs/crazygames/media/record-gameplay.js landscape 45
 *
 * Chrome is launched on a throwaway profile, pointed at the production bundle, and driven over
 * the DevTools protocol: capture.js presses the menu button, plays for the requested number of
 * seconds and posts the recording back to serve.js. Nothing touches the user's own browser.
 *
 * The game has to be built first: gradlew :webApp:wasmJsBrowserDistribution
 */
const { spawn } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const CHROME_CANDIDATES = [
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
    `${os.homedir()}/AppData/Local/Google/Chrome/Application/chrome.exe`,
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium"
];

/*
 * Captured below the delivered size on purpose. The browser encodes 1280x720 at about 47 frames
 * a second and 1600x900 at ten, so the smaller take is the smooth one; build-preview.js scales it
 * up afterwards, which chunky pixel art survives far better than a slideshow.
 */
const FORMATS = {
    landscape: { css: { width: 1280, height: 720 }, scale: 1, deliver: "1920x1080" },
    portrait: { css: { width: 720, height: 1080 }, scale: 1, deliver: "1080x1620" }
};

const format = process.argv[2] ?? "landscape";
const seconds = Number(process.argv[3] ?? 20);
// The opening seconds are mostly the spawn animation; build-preview.js trims them off.
const port = Number(process.argv[4] ?? 8130);
const size = FORMATS[format];
if (!size) throw new Error(`unknown format ${format}; expected ${Object.keys(FORMATS).join(" or ")}`);

const GAME_URL = `http://localhost:${port}/webApp/build/dist/wasmJs/productionExecutable/index.html`;
const DEBUG_PORT = 9333;

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

function findChrome() {
    const found = CHROME_CANDIDATES.find(candidate => fs.existsSync(candidate));
    if (!found) throw new Error("no Chrome binary found; add its path to CHROME_CANDIDATES");
    return found;
}

async function debuggerTarget() {
    for (let attempt = 0; attempt < 100; attempt++) {
        try {
            const targets = await fetch(`http://localhost:${DEBUG_PORT}/json/list`).then(r => r.json());
            const page = targets.find(t => t.type === "page" && t.url.includes("productionExecutable"));
            if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl;
        } catch (_) { /* Chrome is still coming up. */ }
        await sleep(300);
    }
    throw new Error("Chrome never exposed the game page over the DevTools protocol");
}

/** Minimal DevTools client: one socket, numbered commands, promises per id. */
function connect(url) {
    const socket = new WebSocket(url);
    const pending = new Map();
    let nextId = 1;
    const ready = new Promise((resolve, reject) => {
        socket.addEventListener("open", resolve, { once: true });
        socket.addEventListener("error", reject, { once: true });
    });
    const listeners = new Map();
    socket.addEventListener("message", event => {
        const message = JSON.parse(event.data);
        if (message.method) {
            listeners.get(message.method)?.(message.params);
            return;
        }
        const waiter = pending.get(message.id);
        if (!waiter) return;
        pending.delete(message.id);
        message.error ? waiter.reject(new Error(message.error.message)) : waiter.resolve(message.result);
    });
    return {
        ready,
        close: () => socket.close(),
        on: (method, handler) => listeners.set(method, handler),
        send(method, params = {}) {
            const id = nextId++;
            return new Promise((resolve, reject) => {
                pending.set(id, { resolve, reject });
                socket.send(JSON.stringify({ id, method, params }));
            });
        }
    };
}

async function evaluate(client, expression, awaitPromise = true) {
    const result = await client.send("Runtime.evaluate", {
        expression, awaitPromise, returnByValue: true, userGesture: true
    });
    if (result.exceptionDetails) {
        throw new Error(result.exceptionDetails.exception?.description ?? "evaluation failed");
    }
    return result.result.value;
}

/**
 * Saves what the page looks like right now, for when a headless run does not go as expected.
 * These land in the temp directory: they are for reading a failure, not for keeping.
 */
async function snapshot(client, name) {
    const { data } = await client.send("Page.captureScreenshot", { format: "png" });
    const file = path.join(os.tmpdir(), name);
    fs.writeFileSync(file, Buffer.from(data, "base64"));
    console.log(`snapshot ${file}`);
}

/**
 * An ffmpeg concat list carrying each frame's real on-screen duration, so a capture that wobbles
 * between fifteen and forty frames a second still replays at the speed it was recorded.
 */
function frameList(timeline) {
    const slashes = file => file.split(path.sep).join("/");
    const lines = [];
    for (let i = 0; i < timeline.length; i++) {
        const next = timeline[i + 1];
        const duration = next ? Math.max(0.005, next.at - timeline[i].at) : 0.033;
        lines.push(`file '${slashes(timeline[i].file)}'`, `duration ${duration.toFixed(4)}`);
    }
    lines.push(`file '${slashes(timeline.at(-1).file)}'`);
    return lines.join("\n");
}

/** Splits the packed take into numbered JPEGs plus the concat list ffmpeg wants. */
function unpack(packFile, format) {
    const blob = fs.readFileSync(packFile);
    const framesDir = fs.mkdtempSync(path.join(os.tmpdir(), `steel-eagle-${format}-`));
    const count = blob.readUInt32LE(0);
    const timeline = [];
    let cursor = 4;
    for (let i = 0; i < count; i++) {
        const length = blob.readUInt32LE(cursor);
        const at = blob.readDoubleLE(cursor + 4) / 1000;
        const file = path.join(framesDir, `${String(i + 1).padStart(6, "0")}.jpg`);
        fs.writeFileSync(file, blob.subarray(cursor + 12, cursor + 12 + length));
        timeline.push({ file, at });
        cursor += 12 + length;
    }
    fs.writeFileSync(path.join(framesDir, "frames.txt"), frameList(timeline));
    fs.writeFileSync(path.join(__dirname, `frames-${format}.txt`), path.join(framesDir, "frames.txt"));
    fs.rmSync(packFile);
    return framesDir;
}

/** The page is still navigating when the debugger attaches, which throws away the context. */
async function waitForDocument(client) {
    for (let attempt = 0; attempt < 100; attempt++) {
        try {
            if (await evaluate(client, "document.readyState", false) === "complete") return;
        } catch (error) {
            if (!/context was destroyed|Cannot find context/i.test(error.message)) throw error;
        }
        await sleep(300);
    }
    throw new Error("the game page never finished loading");
}

(async () => {
    const chrome = spawn(findChrome(), [
        "--headless=new",
        `--remote-debugging-port=${DEBUG_PORT}`,
        `--user-data-dir=${path.join(os.tmpdir(), `steel-eagle-record-${Date.now()}`)}`,
        `--window-size=${size.css.width},${size.css.height}`,
        "--hide-scrollbars",
        "--mute-audio",
        // The listing is English, and the game follows the browser's language.
        "--lang=en-US",
        "--autoplay-policy=no-user-gesture-required",
        // Skia draws the game through WebGL and the recorder has to encode 1080p in real time, so
        // both want the real GPU. Software rendering falls back to a few frames a second.
        "--use-angle=d3d11",
        "--enable-gpu",
        "--ignore-gpu-blocklist",
        "--enable-unsafe-swiftshader",
        // Without these Chrome decides the window nobody is looking at can stop painting, and a
        // canvas that stops changing records as a single still frame.
        "--disable-background-timer-throttling",
        "--disable-backgrounding-occluded-windows",
        "--disable-renderer-backgrounding",
        "--disable-features=CalculateNativeWinOcclusion",
        "--no-first-run",
        "--no-default-browser-check",
        GAME_URL
    ], { stdio: ["ignore", "ignore", "inherit"] });

    try {
        const client = connect(await debuggerTarget());
        await client.ready;
        await client.send("Runtime.enable");
        await client.send("Page.enable");
        await client.send("Emulation.setDeviceMetricsOverride", {
            width: size.css.width,
            height: size.css.height,
            deviceScaleFactor: size.scale,
            mobile: false
        });
        await client.send("Emulation.setUserAgentOverride", {
            userAgent: await evaluate(client, "navigator.userAgent", false).catch(() => ""),
            acceptLanguage: "en-US"
        });
        await client.send("Page.reload", { ignoreCache: true });

        console.log("waiting for the game to load…");
        await waitForDocument(client);
        await evaluate(client, `
            new Promise(resolve => {
                const tick = setInterval(() => {
                    const host = document.getElementById("webApp")?.firstElementChild?.firstElementChild;
                    if (host?.shadowRoot?.querySelector("canvas")) { clearInterval(tick); resolve(true); }
                }, 200);
            })
        `);

        await evaluate(client, fs.readFileSync(path.join(__dirname, "capture.js"), "utf8"), false);
        await snapshot(client, `_menu-${format}.png`);

        const canvas = await evaluate(client, "beginRun()");
        console.log(`stage is live at ${canvas.width}x${canvas.height}; capturing ${seconds}s…`);

        const pack = path.join(__dirname, `take-${format}.bin`);
        const stats = await evaluate(
            client,
            `recordRun({ seconds: ${seconds}, name: "take-${format}.bin" })`
        );
        await snapshot(client, `_play-${format}.png`);
        client.close();

        const framesDir = unpack(pack, format);
        console.log(
            `captured ${stats.frames} frames over ${stats.span}s ` +
            `(${(stats.frames / stats.span).toFixed(1)} fps; the page painted at ${stats.pageFps} fps)`
        );
        console.log(`frames in ${framesDir}`);
    } finally {
        chrome.kill();
    }
})().catch(error => {
    console.error(error);
    process.exit(1);
});
