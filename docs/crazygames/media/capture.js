/*
 * Runs inside the game page and records a real run off the game's own canvas.
 *
 * record-gameplay.js injects this file into a Chrome instance it launched, then awaits
 * captureGameplay(). Nothing here is part of the shipped game — it only presses the same buttons
 * and keys a player would, and hands the resulting WebM to serve.js.
 */
(() => {
    const PROBE_X = 0.5;          // Menu buttons are centred, so the middle column always hits them.
    const PROBE_FROM = 0.40;      // Probing downwards means the first button hit is the top one.
    const PROBE_TO = 0.66;
    const PROBE_STEP = 0.012;

    const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

    function canvas() {
        const host = document.getElementById("webApp")?.firstElementChild?.firstElementChild;
        return host?.shadowRoot?.querySelector("canvas") ?? null;
    }

    async function waitForCanvas(timeoutMs = 60000) {
        const deadline = Date.now() + timeoutMs;
        while (Date.now() < deadline) {
            const found = canvas();
            if (found) return found;
            await sleep(100);
        }
        throw new Error("the game canvas never appeared");
    }

    /*
     * Compose reads offsetX/offsetY off pointer events, which a constructed event leaves at zero,
     * so every synthetic click has to carry them explicitly.
     */
    function pointer(target, type, x, y, buttons) {
        const event = new PointerEvent(type, {
            clientX: x, clientY: y, screenX: x, screenY: y,
            bubbles: true, cancelable: true, composed: true, view: window,
            pointerId: 1, pointerType: "mouse", isPrimary: true, button: 0, buttons
        });
        Object.defineProperty(event, "offsetX", { value: x });
        Object.defineProperty(event, "offsetY", { value: y });
        target.dispatchEvent(event);
    }

    function click(target, x, y) {
        pointer(target, "pointermove", x, y, 0);
        pointer(target, "pointerdown", x, y, 1);
        pointer(target, "pointerup", x, y, 0);
    }

    const KEYS = {
        up: ["ArrowUp", "ArrowUp", 38],
        down: ["ArrowDown", "ArrowDown", 40],
        left: ["ArrowLeft", "ArrowLeft", 37],
        right: ["ArrowRight", "ArrowRight", 39],
        fire: ["Space", " ", 32]
    };

    function key(target, name, type) {
        const [code, value, keyCode] = KEYS[name];
        target.dispatchEvent(new KeyboardEvent(type, {
            key: value, code, keyCode, which: keyCode,
            bubbles: true, cancelable: true, composed: true
        }));
    }

    /** Waits for the game to report that the stage is actually playable. */
    function gameplayStarted() {
        return new Promise(resolve => {
            const portal = window.steelEaglePortal;
            const original = portal.gameplayStart.bind(portal);
            portal.gameplayStart = () => { resolve(); return original(); };
        });
    }

    /*
     * A fixed run, so re-recording after an art change gives a comparable take. It plays the way
     * a cautious player does: short moves, never sitting still, strafing across the lower half
     * and facing up into the lanes the enemies come down rather than charging into them. Driving
     * deep into the spawn corners loses all three lives in about ten seconds.
     *
     * Fire is tapped throughout rather than held; the cannon has a cooldown either way and the
     * shells read better spaced out.
     */
    const RUN = [
        ["up", 600], ["right", 500], ["up", 400], ["left", 700],
        ["up", 500], ["down", 400], ["left", 600], ["up", 500],
        ["right", 800], ["up", 400], ["right", 500], ["down", 500],
        ["left", 700], ["up", 600], ["left", 500], ["down", 400],
        ["right", 600], ["up", 700], ["right", 400], ["up", 500],
        ["left", 800], ["down", 500], ["left", 400], ["up", 600],
        ["right", 700], ["up", 400], ["down", 600], ["right", 500],
        ["up", 800], ["left", 600], ["down", 400], ["left", 500]
    ];

    async function drive(target, seconds) {
        const deadline = Date.now() + seconds * 1000;
        let firing = true;
        (async () => {
            while (firing) {
                key(target, "fire", "keydown");
                await sleep(90);
                key(target, "fire", "keyup");
                await sleep(280);
            }
        })();

        let index = 0;
        let held = null;
        while (Date.now() < deadline) {
            const [direction, ms] = RUN[index % RUN.length];
            index++;
            if (held) key(target, held, "keyup");
            key(target, direction, "keydown");
            held = direction;
            await sleep(Math.min(ms, Math.max(0, deadline - Date.now())));
        }
        if (held) key(target, held, "keyup");
        firing = false;
    }

    async function startGame(target) {
        const started = gameplayStarted();
        for (let fraction = PROBE_FROM; fraction <= PROBE_TO; fraction += PROBE_STEP) {
            click(target, Math.round(innerWidth * PROBE_X), Math.round(innerHeight * fraction));
            const hit = await Promise.race([started.then(() => true), sleep(400).then(() => false)]);
            if (hit) return true;
        }
        return await Promise.race([started.then(() => true), sleep(10000).then(() => false)]);
    }

    /** Gets from the menu to a playable stage. The driver starts capturing once this returns. */
    window.beginRun = async () => {
        const target = await waitForCanvas();
        await sleep(3000); // The menu paints a beat after the canvas exists.
        if (!await startGame(target)) throw new Error("could not get past the menu");
        await sleep(600); // Let the spawn settle so the take does not open on an empty board.
        return { width: target.width, height: target.height };
    };

    /*
     * Frames are grabbed and encoded here in the page rather than by MediaRecorder or the
     * debugger's screencast: on a headless GPU the first stops feeding its stream after two or
     * three seconds and the second after seven, while the game itself paints at sixty. Copying
     * the canvas each frame and encoding a JPEG keeps up and never stalls.
     *
     * The take is posted as one blob: a four byte frame count, then per frame a four byte length,
     * an eight byte timestamp and the JPEG itself.
     */
    function pack(frames) {
        const header = 4 + frames.length * 12;
        const total = frames.reduce((sum, frame) => sum + frame.data.byteLength, header);
        const out = new Uint8Array(total);
        const view = new DataView(out.buffer);
        view.setUint32(0, frames.length, true);
        let cursor = 4;
        for (const frame of frames) {
            view.setUint32(cursor, frame.data.byteLength, true);
            view.setFloat64(cursor + 4, frame.at, true);
            out.set(new Uint8Array(frame.data), cursor + 12);
            cursor += 12 + frame.data.byteLength;
        }
        return out;
    }

    window.recordRun = async ({ seconds, name, quality = 0.92 }) => {
        const target = canvas();
        const off = new OffscreenCanvas(target.width, target.height);
        const ctx = off.getContext("2d");
        const frames = [];

        let capturing = true;
        let inFlight = 0;
        let painted = 0;
        const startedAt = performance.now();

        const grab = () => {
            painted++;
            if (capturing) requestAnimationFrame(grab);
            if (inFlight > 2) return; // Drop a frame rather than fall behind the game.
            inFlight++;
            const at = performance.now();
            ctx.drawImage(target, 0, 0);
            off.convertToBlob({ type: "image/jpeg", quality })
                .then(blob => blob.arrayBuffer())
                .then(data => { frames.push({ data, at }); })
                .catch(() => {})
                .finally(() => { inFlight--; });
        };
        requestAnimationFrame(grab);

        await drive(target, seconds);
        capturing = false;
        while (inFlight > 0) await sleep(50);

        frames.sort((a, b) => a.at - b.at);
        const elapsed = (performance.now() - startedAt) / 1000;
        const response = await fetch(`/save/${name}`, { method: "POST", body: pack(frames) });
        return {
            bytes: Number(await response.text()),
            frames: frames.length,
            span: +((frames.at(-1).at - frames[0].at) / 1000).toFixed(1),
            pageFps: +(painted / elapsed).toFixed(1)
        };
    };
})();
