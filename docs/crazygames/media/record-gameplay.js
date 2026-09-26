/*
 * Records a route of the built web game as a folder of frames, one per sixtieth of a second.
 *
 *   node docs/crazygames/media/serve.js            # in one shell
 *   node docs/crazygames/media/record-gameplay.js landscape endless-coop 30 --seed 7
 *   node docs/crazygames/media/record-gameplay.js landscape endless-coop 30 --search 1-16
 *
 * The game runs on virtual-clock.js with its dice fixed by the seed, and plays the route from
 * routes.js, so a run is a pure function of route and seed: recording the same pair in both
 * formats gives the same run twice. --search plays seeds without keeping frames and reports what
 * happened in each, to find one worth recording.
 *
 * The game has to be built first: gradlew :webApp:wasmJsBrowserDistribution
 */
const fs = require("fs");
const os = require("os");
const path = require("path");
const { launch, FPS } = require("./chrome-session");
const { route } = require("./routes");

const [format = "landscape", routeName = "endless-coop", secondsArg = "30", ...flags] = process.argv.slice(2);
const option = name => {
    const index = flags.indexOf(name);
    return index >= 0 ? flags[index + 1] : undefined;
};
const seconds = Number(secondsArg);
const port = Number(option("--port") ?? 8130);
const search = option("--search");
const frames = Math.round(seconds * FPS);
const plan = route(routeName, seconds);

const describe = log => log.map(({ frame, what }) => `${(frame / FPS).toFixed(2)}s ${what}`).join("; ");

async function dryRun(session, seed) {
    await session.enterTake({ mode: plan.mode, seed });
    await session.evaluate(`ironroostCapture.startTake(${JSON.stringify(plan)})`, false);
    let status;
    for (let played = 0; played < frames; played += 120) {
        status = await session.evaluate(`ironroostCapture.advance(${Math.min(120, frames - played)})`);
        if (status.runOver) break;
    }
    return status;
}

async function capture(session, seed) {
    await session.enterTake({ mode: plan.mode, seed });
    const canvas = await session.evaluate(`ironroostCapture.startTake(${JSON.stringify(plan)})`, false);
    console.log(`${routeName} is live at ${canvas.width}x${canvas.height}, seed ${seed}; capturing ${frames} frames…`);

    const framesDir = fs.mkdtempSync(path.join(os.tmpdir(), `ironroost-${format}-${routeName}-`));
    const began = Date.now();
    let captured = 0;
    while (captured < frames) {
        const png = await session.evaluate("ironroostCapture.captureFrame()");
        captured++;
        fs.writeFileSync(path.join(framesDir, `${String(captured).padStart(6, "0")}.png`), Buffer.from(png, "base64"));
        if (captured % (FPS * 5) === 0) console.log(`  ${captured / FPS}s of ${seconds}s`);
        // A lost run is the end of the take: what comes after it is the run-over screen.
        if (captured % FPS === 0 && (await session.evaluate("ironroostCapture.status()", false)).runOver) {
            console.log(`the run ended; keeping the ${captured / FPS}s before it`);
            break;
        }
    }
    const status = await session.evaluate("ironroostCapture.status()", false);
    fs.writeFileSync(
        path.join(__dirname, `capture-${format}-${routeName}.json`),
        JSON.stringify({ dir: framesDir, fps: FPS, frames: captured, seed, ...canvas, log: status.log }, null, 2)
    );
    console.log(`captured ${captured} frames in ${((Date.now() - began) / 1000).toFixed(0)}s`);
    console.log(`log: ${describe(status.log)}`);
    console.log(`frames in ${framesDir}`);
}

(async () => {
    const session = await launch({ format, port, dry: Boolean(search) });
    try {
        if (search) {
            const [from, to] = search.split("-").map(Number);
            for (let seed = from; seed <= (to ?? from); seed++) {
                const status = await dryRun(session, seed);
                console.log(`seed ${seed}: ${status.runOver ? "LOST" : "alive"} — ${describe(status.log)}`);
            }
        } else {
            await capture(session, Number(option("--seed") ?? 1));
        }
    } finally {
        session.close();
    }
})().catch(error => {
    console.error(error);
    process.exit(1);
});
