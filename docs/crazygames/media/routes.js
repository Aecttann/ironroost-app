/*
 * The input scripts the preview takes are played from, as frame-keyed events for capture.js.
 *
 * Two tanks share a base their own shells can destroy, and a script cannot see where anyone is,
 * so every co-op route here keeps one rule: a seat never fires facing down or towards the middle.
 * Player one spawns left of the base and fires only up or left, player two spawns right and fires
 * only up or right. A shell of theirs could reach the eagle only along the bottom row towards the
 * middle, which is exactly the way neither of them ever fires. The first co-op takes, which fired
 * every way, shot their own eagle inside four seconds.
 */
const FPS = 60;

/** Turns [steps] of { dir, frames, fire } into events for [seat], starting at [from]. */
function seatEvents(seat, steps, from = 0) {
    const events = [];
    let frame = from;
    for (const { dir, frames, fire } of steps) {
        events.push({ frame, seat, dir, fire });
        frame += frames;
    }
    return { events, end: frame };
}

/**
 * Faces up and fires from wherever the tank has stopped. The direction is only tapped, so the
 * tank turns without driving off, and re-tapped every second in case something turned it.
 */
function holdFacingUp(from, until) {
    const steps = [];
    for (let frame = from; frame < until; frame += FPS) {
        steps.push({ dir: "up", frames: 2, fire: true }, { dir: null, frames: FPS - 2, fire: true });
    }
    return steps;
}

/*
 * Endless arena (stage 12). Enemies spawning in the two top corners are walled in by steel on
 * the inside and can only leave straight down the edge column, so a tank parked on that column
 * facing up fires into the one way out. Each leg of the way there ends against something solid —
 * the brick above the spawn, the steel beside the channel, the steel capping it, the board's
 * edge — so the tank arrives on the half-cell whatever a frame or two of timing does.
 *
 *   player one: up to the brick, left to the steel, up the channel, left to the edge, then up
 *   player two: the same, mirrored
 */
function endlessCoop(seconds) {
    const until = Math.round(seconds * FPS);
    const approach = side => [
        { dir: null, frames: 10, fire: false },
        { dir: "up", frames: 20, fire: false },
        { dir: side, frames: 40, fire: true },
        { dir: "up", frames: 90, fire: true },
        { dir: side, frames: 40, fire: true }
    ];
    const seat = (name, side) => {
        const path = seatEvents(name, approach(side));
        return [...path.events, ...seatEvents(name, holdFacingUp(path.end, until), path.end).events];
    };
    return { mode: "endless-coop", autoCards: true, events: [...seat("one", "left"), ...seat("two", "right")] };
}

/*
 * Forest stage (6). Both tanks dig straight up their spawn columns, firing into the brick ahead
 * and at whatever comes down, with short side-steps outwards for variety — never down, and only
 * ever outwards sideways.
 */
function campaignCoop(seconds) {
    const until = Math.round(seconds * FPS);
    const pattern = side => [
        { dir: "up", frames: 110, fire: true },
        { dir: side, frames: 14, fire: true },
        { dir: "up", frames: 70, fire: true },
        { dir: null, frames: 30, fire: true },
        { dir: side, frames: 10, fire: true },
        { dir: "up", frames: 90, fire: true }
    ];
    const seat = (name, side) => {
        const steps = [{ dir: null, frames: 10, fire: false }];
        let length = 10;
        while (length < until) {
            for (const step of pattern(side)) {
                steps.push(step);
                length += step.frames;
            }
        }
        return seatEvents(name, steps).events;
    };
    return { mode: "campaign-coop", autoCards: false, events: [...seat("one", "left"), ...seat("two", "right")] };
}

const ROUTES = { "endless-coop": endlessCoop, "campaign-coop": campaignCoop };

function route(name, seconds) {
    const build = ROUTES[name];
    if (!build) throw new Error(`unknown route ${name}; expected ${Object.keys(ROUTES).join(" or ")}`);
    return build(seconds);
}

module.exports = { route, ROUTES };
