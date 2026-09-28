/*
 * Renders the Google Play listing graphics from studio.html into docs/play/graphics.
 *
 *   node docs/play/media/render.js             everything
 *   node docs/play/media/render.js 3-bonuses   only outputs whose path contains the text
 *   node docs/play/media/render.js --serve     serve the repository and print the studio URL
 *
 * Launches its own headless Chrome on a throwaway profile — it never touches your browser. The
 * page fetches Roboto from Google Fonts, so rendering wants a network connection; without one the
 * text falls back to a system font and every file is reported as such.
 *
 * If Node is not installed system-wide, the Kotlin Gradle plugin has already downloaded one:
 * ~/.gradle/nodejs/node-*\/node.exe.
 */
const http = require("http");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");
const { pathToFileURL } = require("url");

const repoRoot = path.resolve(__dirname, "..", "..", "..");
const outRoot = path.resolve(__dirname, "..", "graphics");
const studioPath = "/docs/play/media/studio.html";

const CHROME_CANDIDATES = [
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
    `${os.homedir()}/AppData/Local/Google/Chrome/Application/chrome.exe`,
    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium"
];

const TYPES = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".xml": "application/xml; charset=utf-8",
    ".png": "image/png"
};

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

/** Read-only static server over the repository, so the page can read sprites, maps and strings. */
function serve(port = 0) {
    const server = http.createServer((req, res) => {
        const url = decodeURIComponent(req.url.split("?")[0]);
        const file = path.join(repoRoot, url);
        if (!file.startsWith(repoRoot)) return res.writeHead(403).end();
        fs.stat(file, (error, stat) => {
            if (error || !stat.isFile()) return res.writeHead(404).end("not found");
            res.writeHead(200, {
                "content-type": TYPES[path.extname(file)] ?? "application/octet-stream",
                "cache-control": "no-store"
            });
            fs.createReadStream(file).pipe(res);
        });
    });
    return new Promise(resolve => server.listen(port, "127.0.0.1", () => resolve(server)));
}

/** Minimal DevTools client: one socket, numbered commands, a promise per id. */
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

async function launchChrome() {
    const binary = CHROME_CANDIDATES.find(candidate => fs.existsSync(candidate));
    if (!binary) throw new Error("no Chrome or Edge found; add its path to CHROME_CANDIDATES");
    const profile = fs.mkdtempSync(path.join(os.tmpdir(), "ironroost-play-"));
    const chrome = spawn(binary, [
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--mute-audio",
        "--no-first-run",
        "--no-default-browser-check",
        `--user-data-dir=${profile}`,
        "--remote-debugging-port=0",
        "about:blank"
    ], { stdio: "ignore" });

    const portFile = path.join(profile, "DevToolsActivePort");
    for (let i = 0; i < 100 && !fs.existsSync(portFile); i++) await sleep(100);
    if (!fs.existsSync(portFile)) throw new Error("Chrome did not open a DevTools port");
    const [port, browserPath] = fs.readFileSync(portFile, "utf8").split("\n").map(line => line.trim());

    const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: "PUT" })).json();
    const page = connect(target.webSocketDebuggerUrl);
    await page.ready;
    await page.send("Page.enable");

    return {
        page,
        async close() {
            page.close();
            // Asked to quit rather than killed: a killed Chrome leaves helper processes holding
            // the profile, and Windows then refuses to delete it.
            const exited = new Promise(resolve => chrome.once("exit", resolve));
            const browser = connect(`ws://127.0.0.1:${port}${browserPath}`);
            await browser.ready.then(() => browser.send("Browser.close")).catch(() => chrome.kill());
            await Promise.race([exited, sleep(5000)]);
            try {
                fs.rmSync(profile, { recursive: true, force: true, maxRetries: 10, retryDelay: 300 });
            } catch (error) {
                console.warn(`could not remove the temporary profile ${profile}: ${error.message}`);
            }
        }
    };
}

async function evaluate(page, expression) {
    const { result, exceptionDetails } = await page.send("Runtime.evaluate", { expression, returnByValue: true });
    if (exceptionDetails) throw new Error(exceptionDetails.exception?.description ?? exceptionDetails.text);
    return result.value;
}

/** Width, height and colour type straight from the IHDR, so a wrong file never goes unnoticed. */
function inspectPng(buffer) {
    if (buffer.toString("ascii", 1, 4) !== "PNG") throw new Error("not a PNG");
    return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20), colourType: buffer[25] };
}

async function jobs() {
    const { SHOTS, LOCALES } = await import(pathToFileURL(path.join(__dirname, "scenes.mjs")).href);
    // Colour type 6 is RGBA, 2 is RGB: the icon must carry alpha, everything else must not.
    const list = [{ query: "job=icon", out: "icon-512.png", width: 512, height: 512, colourType: 6 }];
    for (const [lang, locale] of Object.entries(LOCALES)) {
        list.push({ query: `job=feature&lang=${lang}`, out: `feature-graphic/${locale}.png`, width: 1024, height: 500, colourType: 2 });
        for (const scene of SHOTS) {
            list.push({ query: `job=shot&id=${scene.id}&lang=${lang}`, out: `phone/${locale}/${scene.id}.png`, width: 1080, height: 1920, colourType: 2 });
        }
    }
    return list;
}

async function main() {
    const args = process.argv.slice(2);
    const server = await serve(args.includes("--serve") ? 8140 : 0);
    const base = `http://127.0.0.1:${server.address().port}`;
    if (args.includes("--serve")) {
        console.log(`${base}${studioPath}`);
        return;
    }

    const filter = args.find(arg => !arg.startsWith("--"));
    const selected = (await jobs()).filter(job => !filter || job.out.includes(filter));
    if (!selected.length) throw new Error(`nothing matches ${filter}`);

    const chrome = await launchChrome();
    let failures = 0;
    try {
        for (const job of selected) {
            await chrome.page.send("Page.navigate", { url: `${base}${studioPath}?${job.query}` });
            let meta = null;
            for (let i = 0; i < 600 && !meta; i++) {
                await sleep(100);
                meta = await evaluate(chrome.page,
                    "window.__result && { error: window.__result.error, fontsOk: window.__result.fontsOk }").catch(() => null);
            }
            if (!meta) throw new Error(`${job.out}: the page never finished`);
            if (meta.error) throw new Error(`${job.out}: ${meta.error}`);

            const buffer = Buffer.from(await evaluate(chrome.page, "window.__result.png"), "base64");
            const png = inspectPng(buffer);
            const problems = [];
            if (png.width !== job.width || png.height !== job.height) problems.push(`is ${png.width}x${png.height}`);
            if (png.colourType !== job.colourType) problems.push(`has colour type ${png.colourType}`);
            if (!meta.fontsOk) problems.push("used a fallback font");

            const file = path.join(outRoot, job.out);
            fs.mkdirSync(path.dirname(file), { recursive: true });
            fs.writeFileSync(file, buffer);
            const size = `${(buffer.length / 1024).toFixed(0)} KB`;
            if (problems.length) {
                failures++;
                console.log(`FAIL ${job.out} ${size}: ${problems.join(", ")}`);
            } else {
                console.log(`ok   ${job.out} ${png.width}x${png.height} ${size}`);
            }
        }
    } finally {
        await chrome.close();
        server.close();
    }
    if (failures) {
        console.log(`${failures} file(s) need another look`);
        process.exitCode = 1;
    }
}

main().catch(error => {
    console.error(error.message);
    process.exit(1);
});
