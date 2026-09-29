(() => {
    "use strict";

    const state = {
        ready: false,
        started: false,
        startupFailed: false,
        enabled: false,
        gameplayActive: false,
        audioMuted: false,
        environment: "disabled",
        pending: [],
        pageVisible: !document.hidden,
        sdkGameplayActive: false,
        cloudSave: false,
        storage: new Map(),
        audio: {
            enabled: true,
            engineRequested: false,
            sources: new Map(),
            encoded: new Map(),
            buffers: new Map(),
            voices: new Set(),
            engine: null,
            context: null,
            effects: null,
            music: {
                encoded: new Map(),
                buffers: new Map(),
                loops: new Map(),
                wanted: null,
                playing: null,
                source: null,
                gain: null
            }
        }
    };

    const EffectsVolume = 0.7;
    // The tracks are levelled to about -18 LUFS; this sits them under the effects rather than
    // across them.
    const MusicVolume = 0.5;

    const sdk = () => window.CrazyGames?.SDK;

    // The web release ships English only. The translations belong to the store builds, where the
    // device language is the player's own setting; in a portal iframe the locale is whatever the
    // host page inherited, and a player who came for an English listing should not land in Korean.
    // Compose Resources reads navigator.language, so the page pins it before the module boots —
    // defer scripts run in document order, and this file is ahead of the bundle.
    function pinLanguage(tag) {
        const languages = Object.freeze([tag]);
        try {
            Object.defineProperty(navigator, "language", { get: () => tag, configurable: true });
            Object.defineProperty(navigator, "languages", { get: () => languages, configurable: true });
        } catch (_) {
            // A browser that refuses the override falls back to its own locale, which still
            // resolves to the default English strings for every language the game does not ship.
        }
        document.documentElement.lang = tag;
    }

    pinLanguage("en-US");

    function execute(action) {
        if (!state.ready) {
            state.pending.push(action);
            return;
        }
        if (!state.enabled) return;

        try {
            action(sdk());
        } catch (error) {
            console.warn("CrazyGames SDK action failed", error);
        }
    }

    function updateAudioMute() {
        const muted = Boolean(sdk()?.game?.settings?.muteAudio);
        if (state.audioMuted === muted) return;
        state.audioMuted = muted;
        reconcileAudio();
        window.dispatchEvent(new CustomEvent("ironroost-audio-mute", { detail: muted }));
    }

    function reconcileGameplay() {
        execute(currentSdk => {
            const shouldBeActive = state.gameplayActive;
            if (shouldBeActive === state.sdkGameplayActive) return;
            state.sdkGameplayActive = shouldBeActive;
            if (shouldBeActive) currentSdk.game.gameplayStart();
            else currentSdk.game.gameplayStop();
        });
    }

    function audioCanPlay() {
        return state.audio.enabled && !state.audioMuted && state.pageVisible;
    }

    function reconcileAudio() {
        reconcileMusic();
        const engine = state.audio.engine;
        if (!engine) return;
        // The game asks every frame while a tank moves. Only a change reaches the element: a
        // play() on one already playing still makes a promise and queues a task, sixty a second.
        const wanted = state.audio.engineRequested && audioCanPlay();
        if (wanted && engine.paused) {
            engine.play().catch(() => {});
        } else if (!wanted && !engine.paused) {
            engine.pause();
        }
    }

    // Effects play through Web Audio where the browser has it. A fresh <audio> element per shot
    // builds a media player and decodes a data URL for every shell, which a desktop shrugs off
    // and an iPad does not; a decoded buffer costs one node per play. The element path stays as
    // the fallback, and covers the first sounds while the buffers are still decoding.
    //
    // The context is made on the first touch, click or key rather than at load: browsers hold a
    // context created without one suspended, and Chrome logs a warning for it.
    function unlockAudio() {
        if (!state.audio.context) {
            const Context = window.AudioContext || window.webkitAudioContext;
            if (!Context) return;
            try {
                const context = new Context();
                const effects = context.createGain();
                effects.gain.value = EffectsVolume;
                effects.connect(context.destination);
                const music = context.createGain();
                music.gain.value = 0;
                music.connect(context.destination);
                state.audio.context = context;
                state.audio.effects = effects;
                state.audio.music.gain = music;
            } catch (_) {
                return;
            }
            state.audio.encoded.forEach((base64, name) => decodeClip(name, base64));
            state.audio.music.encoded.forEach((base64, name) => decodeMusic(name, base64));
        }
        if (state.audio.context.state !== "running") {
            // Older WebKit's resume() returns nothing rather than a promise.
            try { Promise.resolve(state.audio.context.resume()).catch(() => {}); } catch (_) {}
        }
    }

    ["pointerdown", "pointerup", "touchend", "keydown"].forEach(type =>
        window.addEventListener(type, unlockAudio, { capture: true, passive: true })
    );

    function decodeClip(name, base64) {
        const context = state.audio.context;
        if (!context) return;
        try {
            const binary = atob(base64);
            const bytes = new Uint8Array(binary.length);
            for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            context.decodeAudioData(bytes.buffer).then(
                buffer => state.audio.buffers.set(name, buffer),
                () => { /* The element fallback still plays this clip. */ }
            );
        } catch (_) {
            // Same: an undecodable clip keeps playing through the element.
        }
    }

    // ------------------------------------------------------------------- music
    //
    // One looping track at a time, through Web Audio only: an <audio loop> element stops for a
    // beat at every seam, and music is the one sound where that is heard. A browser without Web
    // Audio simply has no music; the effects still have their element fallback.
    //
    // Nothing plays before the first touch, click or key — the browser would not allow it — and
    // the music's gain follows audioCanPlay(), so the SDK's mute, a hidden tab and the game's own
    // sound switch all silence it the moment they change.

    function base64Bytes(base64) {
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return bytes;
    }

    function decodeMusic(name, base64) {
        const context = state.audio.context;
        if (!context) return;
        try {
            context.decodeAudioData(base64Bytes(base64).buffer).then(
                buffer => {
                    state.audio.music.buffers.set(name, buffer);
                    reconcileMusic();
                },
                () => { /* Undecodable: the game plays on without music. */ }
            );
        } catch (_) {
            // Same.
        }
    }

    function stopMusicSource() {
        const music = state.audio.music;
        if (music.source) {
            try { music.source.stop(); } catch (_) { /* Already stopped. */ }
            music.source.disconnect();
        }
        music.source = null;
        music.playing = null;
    }

    function reconcileMusic() {
        const music = state.audio.music;
        const context = state.audio.context;
        if (music.gain) music.gain.gain.value = audioCanPlay() ? MusicVolume : 0;
        if (!context || music.playing === music.wanted) return;
        stopMusicSource();
        const buffer = music.wanted && music.buffers.get(music.wanted);
        // No buffer yet: decodeMusic calls back in here when it has one.
        if (!buffer) return;
        const source = context.createBufferSource();
        source.buffer = buffer;
        source.loop = true;
        // Each file is several copies of one seamless loop, and the loop runs from the second
        // copy to the second-last, well clear of the padding MP3 leaves at either end. See
        // TanksMusic in the game code.
        const loop = music.loops.get(music.wanted);
        if (loop && loop[1] > loop[0] && loop[1] <= buffer.duration) {
            source.loopStart = loop[0];
            source.loopEnd = loop[1];
        }
        source.connect(music.gain);
        source.start();
        music.source = source;
        music.playing = music.wanted;
    }

    function silenceVoices() {
        state.audio.voices.forEach(voice => {
            try {
                if (voice.stop) voice.stop();
                else voice.pause();
            } catch (_) { /* Already finished. */ }
        });
        state.audio.voices.clear();
    }

    // ------------------------------------------------------------------- saved data
    //
    // Progress lives in two stores. localStorage always works — signed out, and on a domain where
    // the SDK is disabled. CrazyGames' data module is the one that follows a signed-in player
    // between devices, which is what the platform's progress requirement is actually about. Writes
    // go to both, and the cloud copy wins a disagreement because it is the one that can be carrying
    // a save from another machine.
    //
    // The data module cannot list its own keys, so the page keeps an index next to them.

    const StoragePrefix = "ironroost.";
    const StorageIndexKey = `${StoragePrefix}__keys`;
    // The earliest browser builds predate the index. Keeping the known keys here makes the first
    // Data-enabled release a real migration instead of only migrating saves written afterwards.
    const LegacyStorageKeys = [
        "tanks_highest_completed_stage",
        "tanks_attempts",
        "tanks_stage_clears",
        "tanks_sound_enabled",
        "tanks_meta_save_v1"
    ].map(key => `${StoragePrefix}${key}`);

    function localGet(key) {
        try { return window.localStorage.getItem(key); }
        catch (_) { return null; }
    }

    function localSet(key, value) {
        try { window.localStorage.setItem(key, value); }
        catch (_) { /* Storage can be unavailable in privacy-restricted iframes. */ }
    }

    function cloudGet(key) {
        if (!state.cloudSave) return null;
        try { return sdk().data.getItem(key); }
        catch (_) { return null; }
    }

    function cloudSet(key, value) {
        if (!state.cloudSave) return;
        try { sdk().data.setItem(key, value); }
        catch (error) { console.warn("CrazyGames data write failed", error); }
    }

    function readIndex(raw) {
        if (!raw) return [];
        try {
            const parsed = JSON.parse(raw);
            return Array.isArray(parsed) ? parsed.filter(key => typeof key === "string") : [];
        } catch (_) {
            return [];
        }
    }

    /**
     * Merges the two stores once the SDK is up: the cloud copy replaces the local one, and a key
     * only this device has is pushed up, so signing in carries a guest save with it.
     *
     * The WebAssembly bundle is injected only after this returns. That ordering matters because
     * the Kotlin repositories read their saves during composition and do not hot-reload when a
     * later browser callback replaces a storage value.
     */
    function adoptCloudSave() {
        if (!state.enabled || !sdk()?.data) return;

        let remoteIndex;
        try {
            remoteIndex = sdk().data.getItem(StorageIndexKey);
            state.cloudSave = true;
        } catch (error) {
            // Basic submissions must opt into the Data Module. A disabled module should leave
            // the standalone local save usable instead of turning every write into an exception.
            state.cloudSave = false;
            console.info("CrazyGames Data Module is unavailable; using local progress.", error);
            return;
        }

        const keys = new Set([
            ...LegacyStorageKeys,
            ...readIndex(localGet(StorageIndexKey)),
            ...readIndex(remoteIndex)
        ]);
        const adoptedKeys = [];

        keys.forEach(key => {
            const remote = cloudGet(key);
            if (remote !== null && remote !== undefined) {
                state.storage.set(key, remote);
                localSet(key, remote);
                adoptedKeys.push(key);
                return;
            }
            const local = localGet(key);
            if (local !== null) {
                state.storage.set(key, local);
                cloudSet(key, local);
                adoptedKeys.push(key);
            }
        });

        const index = JSON.stringify(adoptedKeys);
        localSet(StorageIndexKey, index);
        cloudSet(StorageIndexKey, index);
    }

    function rememberKey(key) {
        const keys = readIndex(localGet(StorageIndexKey));
        if (keys.includes(key)) return;
        const index = JSON.stringify([...keys, key]);
        localSet(StorageIndexKey, index);
        cloudSet(StorageIndexKey, index);
    }

    // ------------------------------------------------------------------ leaderboard
    //
    // CrazyGames only enables the leaderboard for invited games, and the encryption key it needs
    // comes from the developer portal. Both are stamped into the page at build time, so this stays
    // dormant — and the game's own local table stays the board — until the invite arrives.

    function leaderboardKey() {
        const stamped = document.querySelector('meta[name="leaderboard-key"]')?.content?.trim();
        // An unstamped template still carries the token, which is not a key.
        return !stamped || stamped.startsWith("@") ? "" : stamped;
    }

    /**
     * AES-GCM over the score as the platform specifies: a 12-byte random IV prepended to the
     * ciphertext, the pair base64-encoded. The key ships inside the page, so this raises the price
     * of forging a score rather than settling it; CrazyGames validates server-side.
     */
    async function encryptScore(score, encryptionKey) {
        const iv = window.crypto.getRandomValues(new Uint8Array(12));
        const algorithm = { name: "AES-GCM", iv };
        const keyBytes = Uint8Array.from(atob(encryptionKey), character => character.charCodeAt(0));
        const cryptoKey = await window.crypto.subtle.importKey(
            "raw",
            keyBytes,
            algorithm,
            false,
            ["encrypt"]
        );
        const encrypted = await window.crypto.subtle.encrypt(
            algorithm,
            cryptoKey,
            new TextEncoder().encode(score.toString())
        );

        const combined = new Uint8Array(iv.length + encrypted.byteLength);
        combined.set(iv);
        combined.set(new Uint8Array(encrypted), iv.length);
        return btoa(String.fromCharCode(...combined));
    }

    // ------------------------------------------------------------------- keyboard
    //
    // The second co-op seat is keyboard-only, so the menu has to know whether this device has
    // keys before it offers the mode. The web has no API that answers that, so this is two
    // signals: a device reporting a fine pointer has a mouse or trackpad and effectively always
    // has a keyboard too, and any real key press settles it outright. The press is what makes a
    // wrong guess self-correcting — a keyboard the media query missed announces itself the
    // moment it is used.

    let keyboardSeen = false;
    window.addEventListener(
        "keydown",
        event => { if (event.isTrusted) keyboardSeen = true; },
        { capture: true, passive: true }
    );

    function hasPhysicalKeyboard() {
        if (keyboardSeen) return true;
        try {
            return window.matchMedia("(any-pointer: fine)").matches;
        } catch (_) {
            // Never hide a mode because the probe itself broke; a wrong yes costs a confusing
            // run, a wrong no costs the feature outright.
            return true;
        }
    }

    // The mirror image, for the on-screen stick and trigger. `pointer: coarse` asks what the
    // *primary* pointer is, not whether a touchscreen exists at all — so a touchscreen laptop,
    // where the trackpad leads, starts without the pad and gets it the moment a finger lands.

    let touchSeen = false;
    window.addEventListener(
        "pointerdown",
        event => { if (event.isTrusted && event.pointerType === "touch") touchSeen = true; },
        { capture: true, passive: true }
    );

    function usesTouchControls() {
        if (touchSeen) return true;
        try {
            return window.matchMedia("(pointer: coarse)").matches;
        } catch (_) {
            // A broken probe must not strand a touch player with no controls at all.
            return true;
        }
    }

    // ----------------------------------------------------------------- dev unlock
    //
    // Stage locks make the campaign reveal itself in order, which is no help when the thing you
    // need to look at is stage 31. So a page served from a development machine opens all of them.
    //
    // This is deliberately keyed off the page's own origin and nothing else — no build flag, no
    // query parameter, no stored setting. A build flag would be the obvious way to do it and is
    // the more dangerous one: it would create an unlocked distribution sitting in the same folder
    // that packageCrazyGamesBasic zips, and the mistake would ship silently. Tying it to the
    // origin means there is only ever one build, and the portal can never be one of these hosts.
    //
    // `localhost` also covers a phone reached over `adb reverse`, which serves the desktop's port
    // as the device's own localhost — so a real handset gets every stage too.

    const LoopbackHosts = ["localhost", "127.0.0.1", "[::1]", "::1"];

    function unlocksAllStages() {
        try {
            return LoopbackHosts.includes(location.hostname);
        } catch (_) {
            // Anything unreadable is treated as "not a dev machine", which is the safe answer.
            return false;
        }
    }

    const portal = {
        get environment() { return state.environment; },
        get isAudioMuted() { return state.audioMuted; },
        get isLeaderboardAvailable() { return Boolean(leaderboardKey()); },
        get hasPhysicalKeyboard() { return hasPhysicalKeyboard(); },
        get usesTouchControls() { return usesTouchControls(); },
        get unlocksAllStages() { return unlocksAllStages(); },

        gameplayStart() {
            if (state.gameplayActive) return;
            state.gameplayActive = true;
            reconcileGameplay();
        },

        gameplayStop() {
            if (!state.gameplayActive) return;
            state.gameplayActive = false;
            reconcileGameplay();
        },

        setGameContext(stage) {
            execute(currentSdk => currentSdk.game.setGameContext({ level: Number(stage) }));
        },

        clearGameContext() {
            execute(currentSdk => currentSdk.game.clearGameContext());
        },

        reportProgress(completedStage, totalStages) {
            const denominator = Math.max(1, Number(totalStages) || 1);
            const percent = Math.max(0, Math.min(100, Number(completedStage) * 100 / denominator));
            execute(currentSdk => currentSdk.game.reportGameCompletedPercentage(percent));
            return percent;
        },

        audioLoad(name, base64) {
            const source = `data:audio/wav;base64,${base64}`;
            state.audio.sources.set(name, source);
            if (name !== "EngineLoop") {
                state.audio.encoded.set(name, base64);
                decodeClip(name, base64);
            }
            if (name === "EngineLoop") {
                state.audio.engine?.pause();
                const engine = new Audio(source);
                engine.loop = true;
                engine.preload = "auto";
                engine.volume = 0.3;
                state.audio.engine = engine;
                reconcileAudio();
            }
        },

        audioSetEnabled(enabled) {
            state.audio.enabled = Boolean(enabled);
            if (!audioCanPlay()) silenceVoices();
            reconcileAudio();
        },

        audioPlay(name) {
            if (!audioCanPlay() || name === "EngineLoop") return;
            const context = state.audio.context;
            const buffer = state.audio.buffers.get(name);
            // Only a running context: one still suspended would bank the sound and play every
            // banked one at once when it wakes.
            if (context && buffer && context.state === "running") {
                const voice = context.createBufferSource();
                voice.buffer = buffer;
                voice.connect(state.audio.effects);
                state.audio.voices.add(voice);
                voice.addEventListener("ended", () => state.audio.voices.delete(voice), { once: true });
                voice.start();
                return;
            }
            const source = state.audio.sources.get(name);
            if (!source) return;
            const voice = new Audio(source);
            voice.volume = EffectsVolume;
            state.audio.voices.add(voice);
            const cleanup = () => state.audio.voices.delete(voice);
            voice.addEventListener("ended", cleanup, { once: true });
            voice.addEventListener("error", cleanup, { once: true });
            voice.play().catch(cleanup);
        },

        audioSetEngineRunning(running) {
            state.audio.engineRequested = Boolean(running);
            reconcileAudio();
        },

        audioRelease() {
            silenceVoices();
            state.audio.engine?.pause();
            state.audio.engine = null;
            state.audio.sources.clear();
            state.audio.encoded.clear();
            state.audio.buffers.clear();
            state.audio.engineRequested = false;
            const music = state.audio.music;
            stopMusicSource();
            music.wanted = null;
            music.encoded.clear();
            music.buffers.clear();
            music.loops.clear();
        },

        /** A music track's MP3, and the window of it to loop, in seconds. */
        musicLoad(name, base64, loopStart, loopEnd) {
            const music = state.audio.music;
            music.encoded.set(name, base64);
            music.loops.set(name, [Number(loopStart), Number(loopEnd)]);
            decodeMusic(name, base64);
        },

        /** Loops the named track, replacing any other; null or an empty name stops the music. */
        musicPlay(name) {
            state.audio.music.wanted = name || null;
            reconcileMusic();
        },

        storageGet(key) {
            const scoped = `${StoragePrefix}${key}`;
            if (state.storage.has(scoped)) return state.storage.get(scoped);
            return localGet(scoped);
        },

        storageSet(key, value) {
            const scoped = `${StoragePrefix}${key}`;
            const text = String(value);
            state.storage.set(scoped, text);
            localSet(scoped, text);
            cloudSet(scoped, text);
            rememberKey(scoped);
        },

        /** Sends a run to the CrazyGames board. A no-op until the game is invited to one. */
        submitScore(score) {
            const value = Number(score);
            if (!Number.isFinite(value) || value <= 0) return;
            const key = leaderboardKey();
            if (!key || !state.enabled) return;

            encryptScore(value, key)
                .then(encryptedScore => execute(currentSdk =>
                    currentSdk.user.submitScore({ encryptedScore, score: value })
                ))
                .catch(error => console.warn("Score submission failed", error));
        },

        get appVersion() {
            return document.querySelector('meta[name="app-version"]')?.content || "1.0.0";
        },

        hideLoading() {
            state.started = true;
            document.getElementById("loading")?.classList.add("is-hidden");
        }
    };

    window.ironroostPortal = portal;

    // The game runs on WebAssembly GC. Browsers without it, and a bundle that fails to load at
    // all, would otherwise leave the player looking at the spinner forever.
    function reportStartupFailure(reason) {
        if (state.started || state.startupFailed) return;
        state.startupFailed = true;
        document.getElementById("loading")?.classList.add("is-hidden");
        const panel = document.getElementById("unsupported");
        if (!panel) return;
        const line = document.getElementById("unsupported-reason");
        if (line && reason) line.textContent = reason;
        panel.hidden = false;
    }

    function supportsWasmGc() {
        try {
            // A type section holding one field-less struct (0x5f): rejected by every pre-GC engine.
            return WebAssembly.validate(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 3, 1, 0x5f, 0]));
        } catch (_) {
            // Never block a browser this probe cannot classify; a real failure still surfaces below.
            return true;
        }
    }

    const canStartGame = typeof WebAssembly !== "undefined" && supportsWasmGc();
    if (!canStartGame) {
        reportStartupFailure("This browser does not support WebAssembly GC, which the game needs.");
    } else {
        window.addEventListener("error", event => {
            reportStartupFailure(event.message ? `The game failed to load: ${event.message}` : undefined);
        });
        window.addEventListener("unhandledrejection", () => reportStartupFailure());
    }

    // ------------------------------------------------------------------ render scale
    //
    // Compose sizes its canvas by window.devicePixelRatio and redraws all of it every frame, so
    // the ratio decides how many pixels each frame costs. Touch devices draw at 1x and let the
    // browser scale the picture up: at its native 2x an iPad in the CrazyGames app ran the game
    // as a slideshow, at 1x it runs smoothly, and the art is pixel art that survives the upscale.
    // Desktops keep their native density.

    const pixelRatioProperty = Object.getOwnPropertyDescriptor(window, "devicePixelRatio") ??
        Object.getOwnPropertyDescriptor(Object.getPrototypeOf(window), "devicePixelRatio");

    function nativePixelRatio() {
        try {
            return (pixelRatioProperty?.get ? pixelRatioProperty.get.call(window) : window.devicePixelRatio) || 1;
        } catch (_) {
            return 1;
        }
    }

    function isTouchDevice() {
        try {
            return window.matchMedia("(pointer: coarse)").matches;
        } catch (_) {
            return false;
        }
    }

    // A browser that keeps the ratio somewhere this cannot reach draws at its native density.
    if (typeof pixelRatioProperty?.get === "function" && isTouchDevice()) {
        try {
            Object.defineProperty(window, "devicePixelRatio", {
                get: () => Math.min(nativePixelRatio(), 1),
                configurable: true
            });
        } catch (_) {
            // Same: without the override the device draws at its native density.
        }
    }

    // The renderer reads the device pixel ratio when it starts and again on every window resize.
    // Dragging the window to a display with a different density changes the ratio without
    // changing the window, which would otherwise leave the board drawn at the wrong scale.
    function watchPixelRatio() {
        const ratio = nativePixelRatio();
        if (!ratio || typeof window.matchMedia !== "function") return;
        window.matchMedia(`(resolution: ${ratio}dppx)`).addEventListener(
            "change",
            () => {
                window.dispatchEvent(new Event("resize"));
                watchPixelRatio();
            },
            { once: true }
        );
    }

    watchPixelRatio();

    /**
     * Starts Kotlin only after SDK initialization and Data Module adoption have finished. Compose
     * creates the repositories during its first frame, so loading the bundle earlier can lock a
     * whole session to a stale local save even if the cloud copy arrives milliseconds later.
     */
    function loadGameBundle() {
        if (!canStartGame || state.startupFailed) return;
        const script = document.createElement("script");
        script.src = "ironroost.js";
        script.async = true;
        script.addEventListener("error", () => reportStartupFailure("The game bundle could not be loaded."), {
            once: true
        });
        document.head.appendChild(script);
    }

    // Arrows, space and the paging keys scroll the host page by default, which drags the game
    // out of view inside a portal iframe.
    const heldByTheGame = new Set([
        "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight",
        "Space", "PageUp", "PageDown", "Home", "End"
    ]);

    // Only while a run is on. Swallowing Space everywhere also swallowed it in the menus,
    // where the nickname field needs it, and the page cannot scroll behind a menu anyway.
    window.addEventListener("keydown", event => {
        if (state.gameplayActive && heldByTheGame.has(event.code)) event.preventDefault();
    }, { passive: false });

    document.addEventListener("visibilitychange", () => {
        state.pageVisible = !document.hidden;
        reconcileAudio();
    });

    window.addEventListener("blur", () => {
        state.pageVisible = false;
        reconcileAudio();
    });

    window.addEventListener("focus", () => {
        state.pageVisible = !document.hidden;
        reconcileAudio();
    });

    (async () => {
        try {
            const currentSdk = sdk();
            if (!currentSdk) throw new Error("CrazyGames SDK script was not loaded");
            await currentSdk.init();
            state.environment = currentSdk.environment ?? "disabled";
            state.enabled = state.environment === "crazygames" || state.environment === "local";
            currentSdk.game.addSettingsChangeListener?.(updateAudioMute);
            updateAudioMute();
        } catch (error) {
            state.environment = "disabled";
            state.enabled = false;
            console.info("CrazyGames SDK is unavailable; running in standalone mode.", error);
        } finally {
            state.ready = true;
            adoptCloudSave();
            const pending = state.pending.splice(0);
            pending.forEach(action => execute(action));
            reconcileGameplay();
            loadGameBundle();
        }
    })();
})();
