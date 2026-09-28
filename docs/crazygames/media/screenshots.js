/*
 * Screenshots of the built web game at fixed moments, for comparing it before and after a change.
 *
 *   node docs/crazygames/media/serve.js                               # in one shell
 *   node docs/crazygames/media/screenshots.js shoot before
 *   node docs/crazygames/media/screenshots.js shoot phase-1 --formats landscape,portrait
 *   node docs/crazygames/media/screenshots.js compare before phase-1
 *
 * `shoot` lands in docs/quality/screens/<label>/<format>-<view>.png. The views, in the order a
 * player meets them:
 *
 *   menu         the first screen, a second after it settles
 *   menu-keys    the same menu after two presses of the down arrow: the keyboard cursor
 *   stage-start  stage one opening, its card on screen
 *   gameplay     six seconds into the run, the player having moved and fired
 *   pause        the pause overlay over that same moment
 *
 * The game runs on virtual-clock.js with its dice fixed by --seed, so the same build gives the
 * same pictures every time and a difference between two labels is a difference in the game.
 * Every format starts on a throwaway profile: a first visit, nothing saved.
 *
 * `tour` visits the screens off the menu by keyboard — daily reward, collection, records and
 * the nickname dialog, settings and the reset question, about, the stage list — and saves each
 * as <format>-<screen>.png under the same label. See TOUR.
 *
 * `compare` pairs up the views two labels share and writes one image per pair to
 * docs/quality/screens/<before>-vs-<after>/: landscape stacked, the tall formats side by side.
 *
 * The game has to be built first: gradlew :webApp:wasmJsBrowserDistribution
 */
const { spawn } = require("child_process");
const fs = require("fs");
const path = require("path");
const { launch, FORMATS, FPS } = require("./chrome-session");

const SCREENS = path.resolve(__dirname, "..", "..", "quality", "screens");

const VIEWS = ["menu", "menu-keys", "stage-start", "gameplay", "pause"];
const MENU_SETTLE_FRAMES = FPS;
const KEY_SETTLE_FRAMES = 6;
// Half a second into the card, well inside the 3.6 s the first card of a visit holds.
const STAGE_CARD_FRAMES = FPS / 2;
const GAMEPLAY_FRAME = FPS * 6;
const PAUSE_SETTLE_FRAMES = FPS / 2;
const STRINGS_WAIT_MS = 1500;

/*
 * A solo run of stage one, on the same rules the co-op routes keep (routes.js): never fire
 * facing down, where the base is. Up the left side and then along it, trigger held, so the
 * gameplay shot has shells in the air and something broken.
 */
const GAMEPLAY_EVENTS = [
    { frame: 0, seat: "solo", dir: "up", fire: true },
    { frame: 50, seat: "solo", dir: null },
    { frame: 150, seat: "solo", dir: "left" },
    { frame: 175, seat: "solo", dir: "up" },
    { frame: 215, seat: "solo", dir: null }
];

/*
 * Lets the game catch up after a key: [frames] frames, a few at a time, with a moment of wall
 * time after each few.
 *
 * ironroostCapture.idle steps its frames in one unbroken chain of promises, and Compose resumes
 * some coroutines — the one that hands a freshly opened overlay its focus, for one — through a
 * zero-delay timer, which only runs once that chain lets go. Idle in one block and the next key
 * lands before the overlay has taken focus; a real player's keys are never that fast.
 */
async function settle(session, frames) {
    for (let done = 0; done < frames; done += 3) {
        await session.evaluate(`ironroostCapture.idle(${Math.min(3, frames - done)})`);
        await new Promise(resolve => setTimeout(resolve, 20));
    }
}

const [command, ...rest] = process.argv.slice(2);
const option = (name, fallback) => {
    const index = rest.indexOf(name);
    return index >= 0 ? rest[index + 1] : fallback;
};
const positional = rest.filter((arg, index) => !arg.startsWith("--") && !rest[index - 1]?.startsWith("--"));

async function shoot(label) {
    if (!/^[\w.-]+$/.test(label ?? "")) throw new Error("give the shots a label: letters, digits, . - _");
    const formats = option("--formats", "landscape,portrait,phone").split(",");
    const views = option("--views", VIEWS.join(",")).split(",");
    const seed = Number(option("--seed", 1));
    const port = Number(option("--port", 8130));
    const dir = path.join(SCREENS, label);
    fs.mkdirSync(dir, { recursive: true });

    for (const format of formats) {
        if (!FORMATS[format]) throw new Error(`unknown format ${format}`);
        // CSS size, one image pixel per layout pixel: what a 1280x720 player sees, not a
        // listing asset. Sharp enough to judge a layout and small enough to keep in the repo.
        const session = await launch({ format, port, scale: 1, dist: option("--dist", "productionExecutable") });
        try {
            const save = async view => {
                if (!views.includes(view)) return;
                const { data } = await session.client.send("Page.captureScreenshot", { format: "png" });
                const file = path.join(dir, `${format}-${view}.png`);
                fs.writeFileSync(file, Buffer.from(data, "base64"));
                console.log(`${path.relative(process.cwd(), file)}`);
            };

            await session.openMenu({ seed });
            await session.evaluate(`ironroostCapture.idle(${MENU_SETTLE_FRAMES})`);
            await save("menu");
            if (views.includes("menu-keys")) {
                // The first arrow shows the cursor where focus already is; the second moves it.
                for (let i = 0; i < 2; i++) {
                    await session.evaluate("ironroostCapture.press('down')", false);
                    await settle(session, KEY_SETTLE_FRAMES);
                }
                await save("menu-keys");
                // The cursor now sits on Endless; the run below starts with Enter on New game.
                await session.openMenu({ seed });
                await settle(session, MENU_SETTLE_FRAMES);
            }
            if (views.every(view => view.startsWith("menu"))) continue;

            // The game screen names its stage the moment it opens, card and all, while the
            // run itself only starts once the card is gone; the card is shot in between.
            await session.evaluate(`ironroostCapture.begin("campaign")`, false);
            let cardShot = false;
            for (let idled = 0; ; idled += 3) {
                const state = await session.evaluate("ironroostCapture.beginState", false);
                if (state.done) {
                    if (state.error) throw new Error(`${format}: ${state.error}`);
                    break;
                }
                if (!cardShot && (await session.evaluate("ironroostCapture.status()", false)).stage !== null) {
                    await session.evaluate(`ironroostCapture.idle(${STAGE_CARD_FRAMES})`);
                    await save("stage-start");
                    cardShot = true;
                }
                if (idled > FPS * 60) throw new Error(`${format}: stage one never started`);
                await session.evaluate("ironroostCapture.idle(3)");
            }
            if (!cardShot) console.warn(`${format}: the stage card came and went between two checks`);
            await session.evaluate(`ironroostCapture.startTake(${JSON.stringify({ events: GAMEPLAY_EVENTS })})`, false);
            const status = await session.evaluate(`ironroostCapture.advance(${GAMEPLAY_FRAME})`);
            if (status.runOver) throw new Error(`${format}: the run was lost before the gameplay shot`);
            await save("gameplay");

            // Let go of everything first, so the overlay is not drawn over a held trigger.
            await session.evaluate(`ironroostCapture.startTake({ events: [] })`, false);
            await session.evaluate("ironroostCapture.press('pause')", false);
            await settle(session, PAUSE_SETTLE_FRAMES);
            // An overlay's strings are fetched the first time it opens, on the real clock, and
            // it draws its buttons blank until they land. Give them wall time, then frames.
            await new Promise(resolve => setTimeout(resolve, STRINGS_WAIT_MS));
            await session.evaluate(`ironroostCapture.idle(${PAUSE_SETTLE_FRAMES})`);
            await save("pause");
        } finally {
            session.close();
        }
    }
}

/*
 * The screens off the menu, reached the way a keyboard player reaches them: Tab walks the
 * focusable controls in the order they are built, Enter presses one. Each route starts from a
 * freshly loaded menu with New game focused, so the tab counts below are counted from there, in
 * build order, and only change when a screen gains or loses a control ahead of the target.
 *
 * A step is a key from capture.js's KEYS, "run" to start stage one as `shoot` does, or
 * "shot:<name>" to save the screen.
 */
const TOUR = {
    daily: ["tab", "tab", "enter", "shot:daily"],
    collection: ["tab", "tab", "tab", "enter", "shot:collection"],
    records: ["tab", "tab", "tab", "tab", "enter", "shot:records", "tab", "enter", "shot:records-nickname"],
    settings: ["tab", "tab", "tab", "tab", "tab", "enter", "shot:settings", "tab", "enter", "shot:settings-reset"],
    about: ["tab", "tab", "tab", "tab", "tab", "tab", "enter", "shot:about"],
    stages: ["run", "pause", "tab", "tab", "enter", "shot:stages"]
};

async function tour(label) {
    if (!/^[\w.-]+$/.test(label ?? "")) throw new Error("give the shots a label: letters, digits, . - _");
    const formats = option("--formats", "landscape,phone").split(",");
    // --steps runs one ad-hoc route instead, for working out a new one: "run,pause,shot:a,tab,shot:b".
    const adHoc = option("--steps");
    if (adHoc) TOUR.custom = adHoc.split(",");
    const routes = adHoc ? ["custom"] : option("--routes", Object.keys(TOUR).join(",")).split(",");
    const port = Number(option("--port", 8130));
    const dir = path.join(SCREENS, label);
    fs.mkdirSync(dir, { recursive: true });

    for (const format of formats) {
        const session = await launch({ format, port, scale: 1, dist: option("--dist", "productionExecutable") });
        try {
            for (const route of routes) {
                const steps = TOUR[route];
                if (!steps) throw new Error(`no tour route ${route}; expected one of ${Object.keys(TOUR).join(", ")}`);
                await session.openMenu({ seed: 1 });
                await session.evaluate(`ironroostCapture.idle(${MENU_SETTLE_FRAMES})`);
                for (const step of steps) {
                    if (step.startsWith("shot:")) {
                        // Every new screen fetches its strings on the real clock; see pause.
                        await new Promise(resolve => setTimeout(resolve, STRINGS_WAIT_MS));
                        await session.evaluate(`ironroostCapture.idle(${PAUSE_SETTLE_FRAMES})`);
                        const { data } = await session.client.send("Page.captureScreenshot", { format: "png" });
                        const file = path.join(dir, `${format}-${step.slice(5)}.png`);
                        fs.writeFileSync(file, Buffer.from(data, "base64"));
                        console.log(path.relative(process.cwd(), file));
                    } else if (step === "run") {
                        await session.evaluate(`ironroostCapture.begin("campaign")`, false);
                        for (let idled = 0; !(await session.evaluate("ironroostCapture.beginState", false)).done; idled += 15) {
                            if (idled > FPS * 60) throw new Error(`${format}: stage one never started`);
                            await session.evaluate("ironroostCapture.idle(15)");
                        }
                        await session.evaluate(`ironroostCapture.startTake({ events: [] })`, false);
                        await session.evaluate(`ironroostCapture.advance(${FPS})`);
                    } else {
                        await session.evaluate(`ironroostCapture.press(${JSON.stringify(step)})`, false);
                        await settle(session, KEY_SETTLE_FRAMES * 3);
                    }
                }
            }
        } finally {
            session.close();
        }
    }
}

/** Width and height straight out of a PNG header. */
function pngSize(file) {
    const header = Buffer.alloc(24);
    const fd = fs.openSync(file, "r");
    fs.readSync(fd, header, 0, 24, 0);
    fs.closeSync(fd);
    return { width: header.readUInt32BE(16), height: header.readUInt32BE(20) };
}

/** A font ffmpeg's drawtext can label the panels with; without one they go unlabelled. */
const LABEL_FONT = [
    "C:/Windows/Fonts/segoeuib.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
].find(candidate => fs.existsSync(candidate));

/**
 * Two shots in one image with their labels above them: landscape stacked, tall formats side by
 * side, so the pair stays readable at the size a chat or a review shows it. ffmpeg, which the
 * preview pipeline already needs, does the layout.
 */
function composite(beforeFile, afterFile, labels, outFile) {
    const before = pngSize(beforeFile);
    const after = pngSize(afterFile);
    const width = Math.max(before.width, after.width);
    const height = Math.max(before.height, after.height);
    const stacked = width > height;
    const gap = 16;
    const caption = 40;
    const background = "0x101216";
    // Each panel carries the gap to its left and above its caption; the last pad closes the
    // right and bottom edges.
    const panel = (input, label, name) => {
        const text = LABEL_FONT
            ? `,drawtext=fontfile='${LABEL_FONT.replace(/:/g, "\\:")}':text='${label}':fontcolor=0xE7EAEF:fontsize=22:x=${gap}:y=${gap + 6}`
            : "";
        return `[${input}:v]pad=${width + gap}:${height + caption + gap}:${gap}:${caption + gap}:color=${background}${text}[${name}]`;
    };
    const filter = [
        panel(0, labels[0], "a"),
        panel(1, labels[1], "b"),
        `[a][b]${stacked ? "vstack" : "hstack"}=inputs=2,pad=iw+${gap}:ih+${gap}:0:0:color=${background}[out]`
    ].join(";");
    return new Promise((resolve, reject) => {
        const ffmpeg = spawn("ffmpeg", [
            "-y", "-loglevel", "error", "-i", beforeFile, "-i", afterFile,
            "-filter_complex", filter, "-map", "[out]", "-frames:v", "1", outFile
        ], { stdio: ["ignore", "ignore", "pipe"] });
        let errors = "";
        ffmpeg.stderr.on("data", chunk => { errors += chunk; });
        ffmpeg.on("error", error => reject(new Error(`ffmpeg is needed on the path for compare: ${error.message}`)));
        ffmpeg.on("exit", code => (code === 0 ? resolve() : reject(new Error(`ffmpeg exited ${code}: ${errors.trim()}`))));
    });
}

async function compare(before, after) {
    if (!before || !after) throw new Error("compare needs two labels: compare <before> <after>");
    const beforeDir = path.join(SCREENS, before);
    const afterDir = path.join(SCREENS, after);
    const shared = fs.readdirSync(beforeDir).filter(name => name.endsWith(".png") && fs.existsSync(path.join(afterDir, name)));
    if (!shared.length) throw new Error(`${before} and ${after} have no shots in common`);
    const outDir = path.join(SCREENS, `${before}-vs-${after}`);
    fs.mkdirSync(outDir, { recursive: true });
    for (const name of shared) {
        const out = path.join(outDir, name);
        await composite(path.join(beforeDir, name), path.join(afterDir, name), [before, after], out);
        console.log(path.relative(process.cwd(), out));
    }
}

(async () => {
    if (command === "shoot") await shoot(positional[0]);
    else if (command === "tour") await tour(positional[0]);
    else if (command === "compare") await compare(positional[0], positional[1]);
    else throw new Error("usage: screenshots.js shoot <label> | tour <label> | compare <before> <after>");
})().catch(error => {
    console.error(error.message ?? error);
    process.exit(1);
});
