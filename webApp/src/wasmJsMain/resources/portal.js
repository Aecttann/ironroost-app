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
            voices: new Set(),
            engine: null
        }
    };

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
        window.dispatchEvent(new CustomEvent("steel-eagle-audio-mute", { detail: muted }));
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
        const engine = state.audio.engine;
        if (!engine) return;
        if (state.audio.engineRequested && audioCanPlay()) {
            engine.play().catch(() => {});
        } else {
            engine.pause();
        }
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

    const StoragePrefix = "steel-eagle.";
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

    const portal = {
        get environment() { return state.environment; },
        get isAudioMuted() { return state.audioMuted; },
        get isLeaderboardAvailable() { return Boolean(leaderboardKey()); },

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
            if (!audioCanPlay()) {
                state.audio.voices.forEach(voice => voice.pause());
                state.audio.voices.clear();
            }
            reconcileAudio();
        },

        audioPlay(name) {
            if (!audioCanPlay() || name === "EngineLoop") return;
            const source = state.audio.sources.get(name);
            if (!source) return;
            const voice = new Audio(source);
            voice.volume = 0.7;
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
            state.audio.voices.forEach(voice => voice.pause());
            state.audio.voices.clear();
            state.audio.engine?.pause();
            state.audio.engine = null;
            state.audio.sources.clear();
            state.audio.engineRequested = false;
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

    window.steelEaglePortal = portal;

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

    // The renderer reads the device pixel ratio when it starts and again on every window resize.
    // Dragging the window to a display with a different density changes the ratio without
    // changing the window, which would otherwise leave the board drawn at the wrong scale.
    function watchPixelRatio() {
        const ratio = window.devicePixelRatio;
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
        script.src = "steel-eagle.js";
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
