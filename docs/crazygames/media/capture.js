/*
 * Runs inside the game page: gets from the menu into a run and plays it from a script.
 *
 * chrome-session.js injects this file into a Chrome instance running on virtual-clock.js, holds
 * the clock and gets from the menu into a live stage; record-gameplay.js then starts the take
 * with a route from routes.js and plays it frame by frame. Nothing here is part of the shipped
 * game — it only presses the same buttons and keys a player would, and reads back what the game
 * drew.
 */
(() => {
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

    /** A click at a point given as fractions of the viewport. */
    function tapAt(target, [x, y]) {
        click(target, Math.round(innerWidth * x), Math.round(innerHeight * y));
    }

    const KEYS = {
        up: ["ArrowUp", "ArrowUp", 38],
        down: ["ArrowDown", "ArrowDown", 40],
        left: ["ArrowLeft", "ArrowLeft", 37],
        right: ["ArrowRight", "ArrowRight", 39],
        fire: ["Space", " ", 32],
        // Co-op splits the keyboard: WASD is player one, the arrows above become player two,
        // and Enter is player two's trigger.
        wasdUp: ["KeyW", "w", 87],
        wasdDown: ["KeyS", "s", 83],
        wasdLeft: ["KeyA", "a", 65],
        wasdRight: ["KeyD", "d", 68],
        enter: ["Enter", "Enter", 13],
        pause: ["KeyP", "p", 80],
        // Moves focus to the next control in the menus, in the order they are built — which,
        // unlike a click position, survives a control moving across the screen.
        tab: ["Tab", "Tab", 9]
    };

    /** Which physical keys a seat drives. Solo, one tank answers to the arrows and Space. */
    const SEATS = {
        solo: { up: "up", down: "down", left: "left", right: "right", fire: "fire" },
        one: { up: "wasdUp", down: "wasdDown", left: "wasdLeft", right: "wasdRight", fire: "fire" },
        two: { up: "up", down: "down", left: "left", right: "right", fire: "enter" }
    };

    /*
     * The routes reach their modes by the menu's own keys, the way a keyboard player would, not
     * by clicking measured points: the menu has been redrawn more than once, and every redraw
     * moved the points. On a fresh menu PLAY holds focus. The first arrow only shows the cursor
     * there; the next moves it. Up from PLAY is the seat choice (1 player, 2 players); down from
     * PLAY is Endless. On the pause overlay, Tab twice reaches Stages, whose dialog opens on the
     * current stage's tile with four tiles a row, so CAMPAIGN_STAGE (6) is one down and one right
     * of stage one.
     *
     * beginRun still checks the stage the engine actually loaded: a key that went astray would
     * start a different run that looks almost right on screen.
     */
    const PICK_TWO_PLAYERS = ["up", "up", "right", "enter"];
    const SEATS_TO_ENDLESS = ["down", "down"];
    const SEATS_TO_PLAY = ["down"];
    const PAUSE_TO_STAGES = ["tab", "tab", "enter"];
    const STAGE_ONE_TO_CAMPAIGN_STAGE = ["down", "right"];

    /** The stage number the engine loads for an endless run; anything else means a wrong click. */
    const ENDLESS_ARENA = 12;

    /**
     * The campaign stage the co-op route plays. Forest-heavy on purpose: next to the brick and
     * steel of the endless arena it shows the other half of the tile set.
     */
    const CAMPAIGN_STAGE = 6;

    function key(target, name, type) {
        const [code, value, keyCode] = KEYS[name];
        target.dispatchEvent(new KeyboardEvent(type, {
            key: value, code, keyCode, which: keyCode,
            bubbles: true, cancelable: true, composed: true
        }));
    }

    /*
     * What happened during the take, by frame. Printed by record-gameplay.js and shown while
     * authoring, so the moments worth cutting to can be found without scrubbing.
     */
    const takeLog = [];
    let takeFrame = null;
    function logTake(what) {
        if (takeFrame !== null) takeLog.push({ frame: takeFrame, what });
    }

    /*
     * The portal bridge is the only honest signal for what the game is doing: gameplayStart and
     * gameplayStop bracket playable time, and setGameContext names the stage. Wrapping them once
     * turns those into something the route can await, instead of sleeping and hoping.
     *
     * submitScore marks the end of an endless run: the game hands the portal its score just
     * before it stops gameplay, so by the time the stop arrives it is known to be a lost run
     * rather than a cleared wave.
     */
    const portalEvents = (() => {
        const portal = window.ironroostPortal;
        const waitingForStart = [];
        let lastStage = null;
        let runOver = false;

        const originalStart = portal.gameplayStart.bind(portal);
        portal.gameplayStart = (...args) => {
            waitingForStart.splice(0).forEach(resolve => resolve());
            cardPick = null;
            logTake("gameplay started");
            return originalStart(...args);
        };
        const originalStop = portal.gameplayStop.bind(portal);
        portal.gameplayStop = (...args) => {
            logTake(runOver ? "run over" : "gameplay stopped (wave or stage cleared)");
            if (!runOver && script.autoCards && takeFrame !== null) {
                cardPick = { from: takeFrame + CARDS_ON_SCREEN_FRAMES, probe: 0 };
            }
            return originalStop(...args);
        };
        const originalContext = portal.setGameContext.bind(portal);
        portal.setGameContext = stage => {
            lastStage = Number(stage);
            return originalContext(stage);
        };
        const originalSubmit = portal.submitScore.bind(portal);
        portal.submitScore = score => {
            runOver = true;
            return originalSubmit(score);
        };

        return {
            nextStart: () => new Promise(resolve => waitingForStart.push(resolve)),
            get stage() { return lastStage; },
            get runOver() { return runOver; }
        };
    })();

    /** Waits for the game to report that the stage is actually playable. */
    function gameplayStarted() {
        return portalEvents.nextStart();
    }

    const within = (promise, ms) =>
        Promise.race([promise.then(() => true), sleep(ms).then(() => false)]);

    /*
     * New game has focus the moment the menu opens, so Enter starts the campaign at any layout
     * and any window size, with no position to measure. The stage it lands on is checked all the
     * same: Enter on anything else would start something else.
     */
    async function startGame(target) {
        const started = gameplayStarted();
        key(target, "enter", "keydown");
        key(target, "enter", "keyup");
        if (!await within(started, 12000)) return false;
        if (portalEvents.stage !== 1) {
            throw new Error(`Enter on the menu landed in stage ${portalEvents.stage}, not a new game; New game lost focus`);
        }
        return true;
    }

    /*
     * The seat choice outlives the menu, so a solo run first makes sure it is one. With the
     * keyboard hidden the menu greys out "2 players" and drops back to one seat — the same thing
     * it does on a phone. Only the menu reads the flag, polling it once a second, hence the wait;
     * it is put back as soon as the run is under way.
     */
    async function withoutKeyboard(action) {
        const portal = window.ironroostPortal;
        const own = Object.getOwnPropertyDescriptor(portal, "hasPhysicalKeyboard");
        Object.defineProperty(portal, "hasPhysicalKeyboard", { get: () => false, configurable: true });
        try {
            await sleep(1100);
            return await action();
        } finally {
            Object.defineProperty(portal, "hasPhysicalKeyboard", own);
        }
    }

    /** Presses each key in turn, giving the UI a quarter of a second after each. */
    async function pressAll(target, names) {
        for (const name of names) {
            key(target, name, "keydown");
            key(target, name, "keyup");
            await sleep(250);
        }
    }

    /** Two seats picked on the menu, focus left on the seat choice, or an error saying why not. */
    async function pickTwoPlayers(target) {
        // Without a keyboard the menu does not offer the second seat at all, the keys below
        // would land on something else, and the take would quietly be a solo run.
        if (window.ironroostPortal?.hasPhysicalKeyboard !== true) {
            throw new Error("this browser reports no keyboard, so co-op is not on offer");
        }
        await pressAll(target, PICK_TWO_PLAYERS);
    }

    /** Presses Enter on whatever has focus and waits for the run it starts. */
    async function enterAndWait(target, what) {
        const started = gameplayStarted();
        key(target, "enter", "keydown");
        key(target, "enter", "keyup");
        if (!await within(started, 12000)) throw new Error(`${what} never started; a menu key went astray`);
    }

    /**
     * Gets from the menu to a playable stage; the take starts once this resolves.
     *
     * `campaign` is a solo run of stage one, the run a first visit starts with. `endless-coop`
     * is two tanks on one keyboard in the endless arena, with its waves and upgrade cards. `campaign-coop` is the same two
     * tanks on CAMPAIGN_STAGE, for the other half of the game.
     */
    async function beginRun(mode) {
        const target = await waitForCanvas();

        if (mode === "endless-coop") {
            await pickTwoPlayers(target);
            await pressAll(target, SEATS_TO_ENDLESS);
            await enterAndWait(target, "the endless run");
            // A key that landed on PLAY would start the campaign and look almost right on
            // screen, so the stage the engine actually loaded is checked rather than assumed.
            if (portalEvents.stage !== ENDLESS_ARENA) {
                throw new Error(`landed in stage ${portalEvents.stage}, not the endless arena; a menu key went astray`);
            }
        } else if (mode === "campaign-coop") {
            // New game always opens on stage one; the stage list lives on the pause overlay.
            await pickTwoPlayers(target);
            await pressAll(target, SEATS_TO_PLAY);
            await enterAndWait(target, "the campaign");
            await sleep(500);
            await pressAll(target, ["pause"]);
            await sleep(350);
            await pressAll(target, PAUSE_TO_STAGES);
            await sleep(350);
            await pressAll(target, STAGE_ONE_TO_CAMPAIGN_STAGE);
            await enterAndWait(target, "the chosen stage");
            if (portalEvents.stage !== CAMPAIGN_STAGE) {
                throw new Error(`landed in stage ${portalEvents.stage}, not stage ${CAMPAIGN_STAGE}; a stage-list key went astray`);
            }
        } else if (mode === "campaign") {
            if (!await withoutKeyboard(() => startGame(target))) throw new Error("could not get past the menu");
        } else {
            throw new Error(`unknown route mode ${mode}`);
        }
        return { stage: portalEvents.stage };
    }

    /*
     * The run is played from a script: events keyed to frame numbers of the take, each either
     * a seat's direction or trigger changing or a click on the board. With the clock and
     * Math.random both fixed by virtual-clock.js, the same script on the same seed plays the
     * same run in either format, so a route is played once and recorded twice.
     *
     * A held trigger is tapped rather than held down — pressed for FIRE_HELD frames of every
     * FIRE_PERIOD — which is how a player fires and reads better on screen as spaced shells.
     */
    const FIRE_PERIOD = 22;
    const FIRE_HELD = 5;

    /*
     * Endless holds the board still between waves while the upgrade cards are up, and which frame
     * that happens on depends on the run, so a script cannot name it. With autoCards set, the
     * cards stay up long enough to read, then a probe down the centre column takes one — the
     * cards are painted on the canvas, so there is nothing to query. Clicks land on fixed frames
     * after the stop, which keeps the pick as reproducible as everything else.
     */
    const CARDS_ON_SCREEN_FRAMES = 90;
    const CARD_PROBE_EVERY = 13;
    const CARD_PROBES = [...Array(23).keys()].map(step => 0.34 + step * 0.02);
    let cardPick = null;

    const script = { events: [], next: 0, seats: {}, autoCards: false };

    function seatState(name) {
        return script.seats[name] ??= { dir: null, fire: false, since: 0, down: false };
    }

    function applyFrame(target) {
        const frame = takeFrame;
        while (script.next < script.events.length && script.events[script.next].frame <= frame) {
            const event = script.events[script.next++];
            if (event.click) {
                tapAt(target, event.click);
                continue;
            }
            const seat = SEATS[event.seat];
            const state = seatState(event.seat);
            if ("dir" in event && event.dir !== state.dir) {
                if (state.dir) key(target, seat[state.dir], "keyup");
                if (event.dir) key(target, seat[event.dir], "keydown");
                state.dir = event.dir;
            }
            if ("fire" in event && event.fire !== state.fire) {
                state.fire = event.fire;
                state.since = frame;
            }
        }
        for (const [name, state] of Object.entries(script.seats)) {
            const want = state.fire && (frame - state.since) % FIRE_PERIOD < FIRE_HELD;
            if (want !== state.down) {
                key(target, SEATS[name].fire, want ? "keydown" : "keyup");
                state.down = want;
            }
        }
        if (cardPick && frame >= cardPick.from && (frame - cardPick.from) % CARD_PROBE_EVERY === 0) {
            const fraction = CARD_PROBES[cardPick.probe++];
            if (fraction === undefined) cardPick = null;
            else tapAt(target, [0.5, fraction]);
        }
    }

    let grabber = null;

    /** Moves the game on one frame, applying the script, and hands the frame to [grab]. */
    async function frame(grab) {
        const { clock, target } = grabber;
        applyFrame(target);
        await clock.step(() => grab?.(target));
        takeFrame++;
    }

    function encode(off, type, quality) {
        return off.convertToBlob({ type, quality }).then(blob => new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result.slice(reader.result.indexOf(",") + 1));
            reader.onerror = () => reject(reader.error);
            reader.readAsDataURL(blob);
        }));
    }

    window.ironroostCapture = {
        /** Takes time away from the wall clock and fixes the dice, before anything that counts. */
        hold(seed) {
            const clock = window.__ironroostClock;
            if (!clock) throw new Error("the page is not on the virtual clock; load virtual-clock.js first");
            clock.hold(seed);
            grabber = { clock, target: canvas() };
            return true;
        },

        /** Steps [frames] frames outside the take, for the menu and the stage card. */
        async idle(frames) {
            for (let i = 0; i < frames; i++) await grabber.clock.step();
        },

        /** Taps one of KEYS the way a player would: the pause key, say. */
        press(name) {
            key(grabber.target, name, "keydown");
            key(grabber.target, name, "keyup");
            return true;
        },

        /** Starts getting to the stage; poll [beginState] while idling the clock forward. */
        begin(mode) {
            this.beginState = { done: false };
            beginRun(mode).then(
                result => { this.beginState = { done: true, ...result }; },
                error => { this.beginState = { done: true, error: String(error?.message ?? error) }; }
            );
            return true;
        },
        beginState: { done: false },

        /** Frame zero of the take, played from a route's [events]; see routes.js. */
        startTake({ events = [], autoCards = false } = {}) {
            const { target } = grabber;
            const off = new OffscreenCanvas(target.width, target.height);
            const ctx = off.getContext("2d", { alpha: false });
            // Each frame replaces the last outright, so nothing from the previous one shows through.
            ctx.globalCompositeOperation = "copy";
            grabber.off = off;
            grabber.ctx = ctx;
            script.events = [...events].sort((a, b) => a.frame - b.frame);
            script.next = 0;
            script.autoCards = autoCards;
            takeFrame = 0;
            return { width: target.width, height: target.height };
        },

        /** Plays [frames] frames of the take without keeping them: a dry run of a route. */
        async advance(frames) {
            for (let i = 0; i < frames; i++) await frame();
            return status();
        },

        /** Moves the game on by one frame of the take and hands back what it drew, as base64 PNG. */
        async captureFrame() {
            await frame(source => grabber.ctx.drawImage(source, 0, 0));
            return await encode(grabber.off, "image/png");
        },

        status,
        get events() { return script.events; }
    };

    function status() {
        return { frame: takeFrame, runOver: portalEvents.runOver, stage: portalEvents.stage, log: takeLog };
    }
})();
