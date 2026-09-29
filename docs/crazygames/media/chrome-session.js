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
    portrait: { css: { width: 720, height: 1080 }, scale: 1.5 },
    // Not a listing format: the smallest phone the HUD has to read on, with a touchscreen, so
    // the game shows its on-screen stick and trigger. Only screenshots.js uses it.
    phone: { css: { width: 360, height: 640 }, scale: 2, touch: true },
    // The same phone turned on its side: stick and trigger move into the board's flanks.
    "phone-land": { css: { width: 640, height: 360 }, scale: 2, touch: true },
    // Also screenshots only: a full-HD desktop window, the largest the HUD has to hold up at.
    hd: { css: { width: 1920, height: 1080 }, scale: 1 }
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
 * it is drawn, so a seed behaves the same at either scale. [scale] overrides the format's
 * density outright, for screenshots that should come out at the CSS size. [dist] picks the
 * build: developmentExecutable builds in a fraction of the time, for checking a screen quickly.
 */
/**
 * [visitor] is who the game thinks has arrived. "returning" — the default — has finished the
 * first-run lesson, so the page opens on the menu, which is where every take and every tour
 * starts. "first" is a stranger: the game skips the menu and opens straight on stage one with its
 * controls drawn on the field.
 */
async function launch({ format, port = 8130, debugPort = 9333, dry = false, scale, dist = "productionExecutable", visitor = "returning" }) {
    const size = FORMATS[format];
    if (!size) throw new Error(`unknown format ${format}; expected ${Object.keys(FORMATS).join(" or ")}`);
    const gameUrl = `http://localhost:${port}/webApp/build/dist/wasmJs/${dist}/index.html`;

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
        // Blank first: the game is only opened once virtual-clock.js is installed ahead of it.
        // Opened straight away it ran a first time on the real clock, and saved today's date
        // before the reload that put it on the held one.
        "about:blank"
    ], { stdio: ["ignore", "ignore", "ignore"] });

    let client;
    try {
        let socketUrl = null;
        for (let attempt = 0; attempt < 100 && !socketUrl; attempt++) {
            try {
                const targets = await fetch(`http://localhost:${debugPort}/json/list`).then(r => r.json());
                socketUrl = targets.find(t => t.type === "page")
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
            await session.openMenu({ seed });
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
        },

        /**
         * (Re)loads the game on the virtual clock and stops on the menu with the clock held and
         * the dice fixed: the first screen a player sees, and where every run starts from. For a
         * "first" visitor it stops a few seconds into stage one instead, which is where they land.
         */
        async openMenu({ seed }) {
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
        }
    };

    await client.send("Runtime.enable");
    await client.send("Page.enable");
    await client.send("Emulation.setDeviceMetricsOverride", {
        width: size.css.width,
        height: size.css.height,
        deviceScaleFactor: scale ?? (dry ? 1 : size.scale),
        mobile: Boolean(size.touch)
    });
    // Takes effect from the next load, which is why openMenu reloads: portal.js decides between
    // keyboard and touch controls, and the render density, as the page starts.
    if (size.touch) await client.send("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });
    await client.send("Emulation.setUserAgentOverride", {
        userAgent: await evaluate("navigator.userAgent", false).catch(() => ""),
        acceptLanguage: "en-US"
    });
    // Installed ahead of every script on the page, so the game never sees the real clock.
    await client.send("Page.addScriptToEvaluateOnNewDocument", {
        source: fs.readFileSync(path.join(__dirname, "virtual-clock.js"), "utf8")
    });
    // The profile is a throwaway one, so a returning player is one whose save says so: the
    // lesson's key (TanksTutorial.KeyDone) under the page's storage prefix (portal.js).
    if (visitor === "returning") {
        await client.send("Page.addScriptToEvaluateOnNewDocument", {
            source: `try { localStorage.setItem("ironroost.tanks_tutorial_done", "true"); } catch (_) {}`
        });
    } else if (visitor !== "first") {
        chrome.kill();
        throw new Error(`unknown visitor ${visitor}; expected returning or first`);
    }
    await client.send("Page.navigate", { url: gameUrl });
    // Let the first load finish before anything reloads it: a reload sent mid-navigation lands
    // on a page that is being swapped out and fails with "not attached to an active page".
    for (let attempt = 0; ; attempt++) {
        if (attempt > 100) throw new Error("the game page never finished its first load");
        try {
            if (await evaluate("location.href.startsWith('http') && document.readyState === 'complete'", false)) break;
        } catch (error) {
            if (!/context was destroyed|Cannot find context|not attached/i.test(error.message)) throw error;
        }
        await sleep(200);
    }
    return session;
}

module.exports = { launch, FORMATS, FPS };
