/*
 * The headless Chrome both record-gameplay.js and author-route.js drive: a throwaway profile,
 * the production bundle, the page on virtual-clock.js from its first script, capture.js on top.
 * Nothing here touches the user's own browser.
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
 * The page lays out at the CSS size and draws at 1.5x, so the canvas comes out at exactly the
 * delivered size with no scaling afterwards. The CSS size is what the menu coordinates in
 * capture.js were measured against, which is why it stays put.
 */
const FORMATS = {
    landscape: { css: { width: 1280, height: 720 }, scale: 1.5 },
    portrait: { css: { width: 720, height: 1080 }, scale: 1.5 }
};

const FPS = 60;
// The menu finishes painting a beat after the canvas exists; the clock is held only after this.
const MENU_SETTLE_MS = 3500;

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

function findChrome() {
    const found = CHROME_CANDIDATES.find(candidate => fs.existsSync(candidate));
    if (!found) throw new Error("no Chrome binary found; add its path to CHROME_CANDIDATES");
    return found;
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
    socket.addEventListener("message", event => {
        const message = JSON.parse(event.data);
        const waiter = pending.get(message.id);
        if (!waiter) return;
        pending.delete(message.id);
        message.error ? waiter.reject(new Error(message.error.message)) : waiter.resolve(message.result);
    });
    return {
        ready,
        close: () => socket.close(),
        send(method, params = {}) {
            const id = nextId++;
            return new Promise((resolve, reject) => {
                pending.set(id, { resolve, reject });
                socket.send(JSON.stringify({ id, method, params }));
            });
        }
    };
}

/**
 * [dry] draws at 1x: a dry run keeps no frames, and the game's rules do not care how sharply
 * it is drawn, so a seed behaves the same at either scale.
 */
async function launch({ format, port = 8130, debugPort = 9333, dry = false }) {
    const size = FORMATS[format];
    if (!size) throw new Error(`unknown format ${format}; expected ${Object.keys(FORMATS).join(" or ")}`);
    const gameUrl = `http://localhost:${port}/webApp/build/dist/wasmJs/productionExecutable/index.html`;

    const chrome = spawn(findChrome(), [
        "--headless=new",
        `--remote-debugging-port=${debugPort}`,
        `--user-data-dir=${path.join(os.tmpdir(), `ironroost-record-${Date.now()}`)}`,
        `--window-size=${size.css.width},${size.css.height}`,
        "--hide-scrollbars",
        "--mute-audio",
        // The listing is English, and the game follows the browser's language.
        "--lang=en-US",
        "--autoplay-policy=no-user-gesture-required",
        // Skia draws the game through WebGL. The take no longer has to keep up with anything,
        // but on the software fallback every frame of it costs several times as long.
        "--use-angle=d3d11",
        "--enable-gpu",
        "--ignore-gpu-blocklist",
        "--enable-unsafe-swiftshader",
        // Until the clock is held the game follows the wall clock through requestAnimationFrame,
        // and Chrome would otherwise decide a window nobody is looking at can stop ticking.
        "--disable-background-timer-throttling",
        "--disable-backgrounding-occluded-windows",
        "--disable-renderer-backgrounding",
        "--disable-features=CalculateNativeWinOcclusion",
        "--no-first-run",
        "--no-default-browser-check",
        gameUrl
    ], { stdio: ["ignore", "ignore", "ignore"] });

    let client;
    try {
        let socketUrl = null;
        for (let attempt = 0; attempt < 100 && !socketUrl; attempt++) {
            try {
                const targets = await fetch(`http://localhost:${debugPort}/json/list`).then(r => r.json());
                socketUrl = targets.find(t => t.type === "page" && t.url.includes("productionExecutable"))
                    ?.webSocketDebuggerUrl ?? null;
            } catch (_) { /* Chrome is still coming up. */ }
            if (!socketUrl) await sleep(300);
        }
        if (!socketUrl) throw new Error("Chrome never exposed the game page over the DevTools protocol");
        client = connect(socketUrl);
        await client.ready;
    } catch (error) {
        chrome.kill();
        throw error;
    }

    const evaluate = async (expression, awaitPromise = true) => {
        const result = await client.send("Runtime.evaluate", {
            expression, awaitPromise, returnByValue: true, userGesture: true
        });
        if (result.exceptionDetails) {
            throw new Error(result.exceptionDetails.exception?.description ?? "evaluation failed");
        }
        return result.result.value;
    };

    const session = {
        format,
        client,
        evaluate,
        close() {
            client.close();
            chrome.kill();
        },

        /** Saves what the page looks like now, for reading a failure. Lands in the temp directory. */
        async snapshot(name) {
            const { data } = await client.send("Page.captureScreenshot", { format: "png" });
            const file = path.join(os.tmpdir(), name);
            fs.writeFileSync(file, Buffer.from(data, "base64"));
            return file;
        },

        /**
         * (Re)loads the game on the virtual clock and gets it to frame zero of a take: the
         * route's run live, its seed fixed, nothing left to chance.
         */
        async enterTake({ mode, seed }) {
            await client.send("Page.reload", { ignoreCache: true });
            for (let attempt = 0; ; attempt++) {
                if (attempt > 100) throw new Error("the game page never finished loading");
                try {
                    if (await evaluate("document.readyState", false) === "complete") break;
                } catch (error) {
                    if (!/context was destroyed|Cannot find context/i.test(error.message)) throw error;
                }
                await sleep(300);
            }
            await evaluate(`
                new Promise(resolve => {
                    const tick = setInterval(() => {
                        const host = document.getElementById("webApp")?.firstElementChild?.firstElementChild;
                        if (host?.shadowRoot?.querySelector("canvas")) { clearInterval(tick); resolve(true); }
                    }, 200);
                })
            `);
            if (!await evaluate("Boolean(window.__ironroostClock)", false)) {
                throw new Error("virtual-clock.js did not load ahead of the game");
            }
            await sleep(MENU_SETTLE_MS);
            await evaluate(fs.readFileSync(path.join(__dirname, "capture.js"), "utf8"), false);

            // From here on nothing moves unless it is stepped, so the run starts on the same
            // frame, with the same dice, every time.
            await evaluate(`ironroostCapture.hold(${Number(seed) | 0})`, false);
            await evaluate(`ironroostCapture.begin(${JSON.stringify(mode)})`, false);
            for (let idled = 0; ; idled += 15) {
                const state = await evaluate("ironroostCapture.beginState", false);
                if (state.done) {
                    if (state.error) throw new Error(state.error);
                    break;
                }
                if (idled > FPS * 60) throw new Error(`the ${mode} run never started`);
                await evaluate("ironroostCapture.idle(15)");
            }
        }
    };

    await client.send("Runtime.enable");
    await client.send("Page.enable");
    await client.send("Emulation.setDeviceMetricsOverride", {
        width: size.css.width,
        height: size.css.height,
        deviceScaleFactor: dry ? 1 : size.scale,
        mobile: false
    });
    await client.send("Emulation.setUserAgentOverride", {
        userAgent: await evaluate("navigator.userAgent", false).catch(() => ""),
        acceptLanguage: "en-US"
    });
    // Installed ahead of every script on the page, so the game never sees the real clock.
    await client.send("Page.addScriptToEvaluateOnNewDocument", {
        source: fs.readFileSync(path.join(__dirname, "virtual-clock.js"), "utf8")
    });
    return session;
}

module.exports = { launch, FORMATS, FPS };
