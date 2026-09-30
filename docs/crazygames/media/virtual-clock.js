/*
 * Puts the game page on a clock the recorder owns.
 *
 * record-gameplay.js installs this before any of the page's own scripts run, so from the first
 * line of the game onwards requestAnimationFrame, performance.now, Date.now and every timer with
 * a real delay read this clock instead of the wall clock. Until the take starts it follows the
 * wall clock and nothing behaves differently. For the take it is held and moved on by exactly
 * one sixtieth of a second per captured frame, however long the browser needs to draw and
 * encode that frame — so the video holds every frame the game drew, evenly spaced, instead of
 * the fraction a real-time grab can keep up with.
 *
 * Zero-delay timers pass straight through to the browser. They mean "as soon as possible", not
 * "after some time", and holding them for a frame would add a frame of lag to every coroutine
 * hop inside the game.
 *
 * Math.random is replaced too, by a seeded generator. The game draws each run's seed from it, so
 * with time and randomness both fixed a run is a pure function of the inputs it is given: the
 * same scripted inputs replay the same run, frame for frame, in either capture format.
 */
(() => {
    let randomState = 0x2545f491;
    // mulberry32: small, fast, and plenty for dice rolls nobody is trying to predict.
    Math.random = () => {
        randomState = (randomState + 0x6d2b79f5) | 0;
        let t = Math.imul(randomState ^ (randomState >>> 15), 1 | randomState);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };

    const FRAME_MS = 1000 / 60;
    // A stall while following the wall clock (the game compiling, say) moves time on by this
    // much at most, the way the game's own frame loop clamps a long gap.
    const MAX_FOLLOW_MS = 250;

    const real = {
        requestAnimationFrame: window.requestAnimationFrame.bind(window),
        setTimeout: window.setTimeout.bind(window),
        clearTimeout: window.clearTimeout.bind(window),
        performanceNow: performance.now.bind(performance),
        dateNow: Date.now.bind(Date)
    };

    // Where held time starts, so two sessions agree on the clock's reading as well as its rate.
    const HELD_ORIGIN_MS = 1_000_000;
    // A tour can move the calendar a few days either way, for the daily reward's states: the shift
    // sits in this tab's sessionStorage, so it survives the reload that makes the game see it.
    const calendarShiftDays = (() => {
        try { return Number(sessionStorage.getItem("__ironroostCalendarShiftDays")) || 0; }
        catch (_) { return 0; }
    })();
    const HELD_EPOCH_MS = Date.UTC(2026, 0, 1) + calendarShiftDays * 86_400_000;

    let now = real.performanceNow();
    // The calendar is fixed from the first script on, not only once the clock is held. The game
    // saves the day it last saw as the menu opens; had that been today's real date, the held
    // clock would read as months behind it, and the daily reward would sit in "clock behind".
    let epochOffset = HELD_EPOCH_MS - now;
    // Far above the browser's own ids, so a clear can tell which kind of timer it was handed.
    let nextId = 1_000_000_000;
    const timers = new Map();
    let frameCallbacks = new Map();
    let held = false;

    const report = error => real.setTimeout(() => { throw error; }, 0);

    performance.now = () => now;
    Date.now = () => Math.floor(now + epochOffset);

    window.setTimeout = (handler, ms, ...args) => {
        const delay = Number(ms) || 0;
        if (delay <= 0) return real.setTimeout(handler, 0, ...args);
        const id = nextId++;
        timers.set(id, { at: now + delay, handler, args, every: 0 });
        return id;
    };
    window.setInterval = (handler, ms, ...args) => {
        const every = Math.max(4, Number(ms) || 0);
        const id = nextId++;
        timers.set(id, { at: now + every, handler, args, every });
        return id;
    };
    window.clearTimeout = window.clearInterval = id => {
        if (!timers.delete(id)) real.clearTimeout(id);
    };
    window.requestAnimationFrame = callback => {
        const id = nextId++;
        frameCallbacks.set(id, callback);
        return id;
    };
    window.cancelAnimationFrame = id => { frameCallbacks.delete(id); };

    function nextDue(until) {
        let found = null;
        for (const [id, timer] of timers) {
            if (timer.at > until) continue;
            if (!found || timer.at < found.timer.at) found = { id, timer };
        }
        return found;
    }

    /** Fires every timer due by [until] in order, letting the promises each one settles run. */
    async function runTimers(until) {
        for (let guard = 0; guard < 10_000; guard++) {
            const due = nextDue(until);
            if (!due) break;
            const { id, timer } = due;
            if (timer.at > now) now = timer.at;
            if (timer.every) timer.at += timer.every; else timers.delete(id);
            try {
                if (typeof timer.handler === "function") timer.handler(...timer.args);
            } catch (error) {
                report(error);
            }
            await Promise.resolve();
        }
        if (until > now) now = until;
    }

    function runFrame() {
        const callbacks = frameCallbacks;
        frameCallbacks = new Map();
        for (const callback of callbacks.values()) {
            try {
                callback(now);
            } catch (error) {
                report(error);
            }
        }
    }

    let lastReal = real.performanceNow();
    async function followWallClock() {
        if (held) return;
        const realNow = real.performanceNow();
        const elapsed = Math.min(MAX_FOLLOW_MS, Math.max(0, realNow - lastReal));
        lastReal = realNow;
        await runTimers(now + elapsed);
        runFrame();
        real.requestAnimationFrame(followWallClock);
    }
    real.requestAnimationFrame(followWallClock);

    window.__ironroostClock = {
        /**
         * Stops following the wall clock; from here on only [step] moves time. The clock jumps to
         * a fixed reading, pending timers keeping their distance from it, and Math.random
         * restarts from [seed] — so whatever happens next happens the same way every time.
         */
        hold(seed) {
            held = true;
            const shift = HELD_ORIGIN_MS - now;
            for (const timer of timers.values()) timer.at += shift;
            now = HELD_ORIGIN_MS;
            epochOffset = HELD_EPOCH_MS - now;
            randomState = seed | 0;
        },

        /**
         * Moves time on by one frame: the timers due in it, then the frame callbacks, which is
         * where Compose draws, then [afterFrame]. That last one runs in the same task as the
         * drawing, while the frame is still in the canvas's drawing buffer.
         */
        async step(afterFrame) {
            await runTimers(now + FRAME_MS);
            runFrame();
            return afterFrame?.();
        }
    };
})();
