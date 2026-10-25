
/* =========================================================
   MÚSICA DA TELA DE LOGIN
   Tenta iniciar automaticamente. Caso o navegador/CEF bloqueie
   autoplay, o primeiro clique/toque/tecla libera a reprodução.
   ========================================================= */

const loginBackgroundMusic =
    document.getElementById("loginBackgroundMusic");

if (loginBackgroundMusic) {
    loginBackgroundMusic.volume = 0.42;
    loginBackgroundMusic.loop = true;

    function startConnexionBackgroundMusic() {
        if (!loginBackgroundMusic.paused) {
            return;
        }

        const playResult =
            loginBackgroundMusic.play();

        if (
            playResult &&
            typeof playResult.catch === "function"
        ) {
            playResult.catch(() => {});
        }
    }

    startConnexionBackgroundMusic();

    [
        "pointerdown",
        "mousedown",
        "touchstart",
        "keydown"
    ].forEach((eventName) => {
        window.addEventListener(
            eventName,
            (event) => {
                const target = event.target;

                if (
                    target &&
                    typeof target.closest === "function" &&
                    target.closest(".revolt-music-player")
                ) {
                    return;
                }

                startConnexionBackgroundMusic();
            },
            {
                capture: true,
                once: true
            }
        );
    });
}


/* =========================================================
   REVOLT CITY - AUTENTICAÇÃO
   Camada de interface para CEF / WebView.
   ========================================================= */

const CONFIG = {
    socialLinks: {
        discord: "https://discord.gg/revolt",
        website: "https://revolt.city",
        youtube: "https://youtube.com/"
    }
};

const tabs = document.querySelectorAll(".auth-tab");
const screens = document.querySelectorAll(".auth-screen");

const loginForm = document.getElementById("loginForm");
const registerForm = document.getElementById("registerForm");
const recoverForm = document.getElementById("recoverForm");
const requestCodeButton = document.getElementById("requestCodeButton");

const notificationContainer = document.getElementById("notificationContainer");

const authTabsContainer = document.querySelector(".auth-tabs");
if (authTabsContainer) {
    authTabsContainer.dataset.active = "login";
}

let screenTransitionLocked = false;

function showScreen(screenName) {
    if (screenTransitionLocked) {
        return;
    }

    const nextScreen = document.getElementById(`screen-${screenName}`);
    const currentScreen = document.querySelector(".auth-screen.active");
    const tabContainer = document.querySelector(".auth-tabs");

    if (!nextScreen || nextScreen === currentScreen) {
        return;
    }

    screenTransitionLocked = true;

    tabs.forEach((tab) => {
        tab.classList.toggle(
            "active",
            tab.dataset.screen === screenName
        );
    });

    if (tabContainer) {
        tabContainer.dataset.active = screenName;
    }

    if (currentScreen) {
        currentScreen.classList.remove("screen-enter");
        currentScreen.classList.add("screen-exit");
    }

    setTimeout(() => {
        if (currentScreen) {
            currentScreen.classList.remove(
                "active",
                "screen-exit"
            );
        }

        nextScreen.classList.add("active", "screen-enter");

        setTimeout(() => {
            nextScreen.classList.remove("screen-enter");
            screenTransitionLocked = false;
        }, 430);
    }, 205);
}

tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
        showScreen(tab.dataset.screen);
    });
});

document.querySelectorAll(".password-toggle").forEach((button) => {
    button.addEventListener("click", () => {
        const targetId = button.dataset.passwordTarget;
        const input = document.getElementById(targetId);

        if (!input) {
            return;
        }

        input.type = input.type === "password"
            ? "text"
            : "password";

        button.style.opacity = input.type === "text"
            ? "0.82"
            : "";
    });
});


const NOTIFICATION_SOUND_FILES = {
    success: "assets/sounds/success.mp3",
    error: "assets/sounds/error.wav",
    info: "assets/sounds/info.mp3",
    warning: "assets/sounds/warning.mp3"
};

const notificationSoundPool = {};

Object.entries(NOTIFICATION_SOUND_FILES).forEach(([type, src]) => {
    const audio = new Audio(src);

    audio.preload = "auto";
    audio.volume = 1.0;

    try {
        audio.load();
    } catch (error) {}

    notificationSoundPool[type] = audio;
});

let notificationAudioUnlocked = false;

async function unlockNotificationAudio() {
    if (notificationAudioUnlocked) {
        return;
    }

    for (const audio of Object.values(notificationSoundPool)) {
        try {
            const volume = audio.volume;

            audio.volume = 0;
            audio.currentTime = 0;

            const result = audio.play();

            if (result && typeof result.then === "function") {
                await result;
            }

            audio.pause();
            audio.currentTime = 0;
            audio.volume = volume;
        } catch (error) {}
    }

    notificationAudioUnlocked = true;
}

[
    "pointerdown",
    "mousedown",
    "touchstart",
    "keydown"
].forEach((eventName) => {
    window.addEventListener(
        eventName,
        unlockNotificationAudio,
        {
            capture: true,
            once: true
        }
    );
});

function playNotificationSound(type) {
    const normalizedType =
        type === "alert"
            ? "warning"
            : type;

    const baseAudio =
        notificationSoundPool[normalizedType] ||
        notificationSoundPool.info;

    if (!baseAudio) {
        return;
    }

    try {
        const audio = baseAudio.cloneNode(true);

        audio.volume = 1.0;
        audio.currentTime = 0;

        const result = audio.play();

        if (result && typeof result.catch === "function") {
            result.catch(() => {});
        }
    } catch (error) {}
}


function notify(message, type = "info", options = {}) {
    if (type === "alert") {
        type = "warning";
    }

    const defaults = {
        success: {
            title: "Succès",
            duration: 4500
        },
        error: {
            title: "Erreur",
            duration: 4500
        },
        info: {
            title: "Info",
            duration: 4500
        },
        warning: {
            title: "Alerta",
            duration: 4500
        }
    };

    const config =
        defaults[type] ||
        defaults.info;

    const title =
        options.title ||
        config.title;

    const duration =
        options.duration ||
        config.duration;

    const notification =
        document.createElement("article");

    notification.className =
        `notification ${type}`;

    notification.style.setProperty(
        "--notify-duration",
        `${duration}ms`
    );

    notification.innerHTML = `
        <div class="notification-icon" aria-hidden="true"></div>

        <div class="notification-content">
            <h3 class="notification-title"></h3>
            <p class="notification-message"></p>
        </div>

        <span class="notification-count">x1</span>

        <div class="notification-progress-track">
            <div class="notification-progress"></div>
        </div>
    `;

    notification
        .querySelector(".notification-title")
        .textContent = title;

    notification
        .querySelector(".notification-message")
        .textContent = message;

    playNotificationSound(type);

    /*
     * Cada aviso é uma caixa separada.
     * A nova entra por último, embaixo.
     * O container está preso no bottom,
     * então as anteriores sobem.
     */
    notificationContainer.appendChild(notification);

    setTimeout(() => {
        notification.classList.add("is-leaving");

        setTimeout(() => {
            notification.remove();
        }, 250);
    }, duration);

    return notification;
}

function emitToGame(eventName, payload) {
    const message = JSON.stringify({
        action: eventName,
        payload,
        // Mantemos também os nomes antigos para compatibilidade com bridges CEF.
        event: eventName,
        data: payload
    });

    if (eventName === "auth:login") {
        window.__revoltPendingConnexion = {
            username: payload.username || "",
            password: payload.password || "",
            remember: Boolean(payload.remember)
        };
    }

    // Pont natif Crestwood (SAMP.java -> SampAuthBridge) : envoie /authact au serveur.
    if (
        window.SampAuth &&
        typeof window.SampAuth.action === "function"
    ) {
        try {
            window.SampAuth.action(
                String(eventName).replace(/^auth:/, ""),
                JSON.stringify(payload || {})
            );
            return true;
        } catch (error) {
            console.warn("[Auth] SampAuth.action a échoué :", error);
        }
    }

    if (
        window.Android &&
        typeof window.Android.postMessage === "function"
    ) {
        window.Android.postMessage(message);
        return true;
    }

    if (
        window.DXBridge &&
        typeof window.DXBridge.postMessage === "function"
    ) {
        window.DXBridge.postMessage(message);
        return true;
    }

    if (
        window.cefQuery &&
        typeof window.cefQuery === "function"
    ) {
        window.cefQuery({
            request: message,
            onSuccess: function () {},
            onFailure: function () {}
        });
        return true;
    }

    if (
        window.GameBridge &&
        typeof window.GameBridge.send === "function"
    ) {
        window.GameBridge.send(eventName, JSON.stringify(payload));
        return true;
    }

    console.log("[Revolt Auth]", eventName, payload);
    return false;
}

loginForm.addEventListener("submit", (event) => {
    event.preventDefault();

    const username = document.getElementById("loginUsername").value.trim();
    const password = document.getElementById("loginPassword").value;
    const remember = document.getElementById("rememberConnexion").checked;

    if (!username || !password) {
        notify("Veuillez saisir votre pseudo et votre mot de passe.", "error");
        return;
    }

    if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
        notify("O nickname deve ter de 3 a 20 caracteres e usar apenas letras, números ou _.", "error");
        return;
    }

    if (password.length < 1 || password.length > 100 || !/^[\x21-\x7E]+$/.test(password) || /[\\/]/.test(password)) {
        notify("Mot de passe inválida. Use até 100 caracteres e não utilize espaços.", "error");
        return;
    }

    emitToGame("auth:login", { username, password, remember });
    notify("Solicitação de login enviada.", "info");
});

registerForm.addEventListener("submit", (event) => {
    event.preventDefault();

    const username = document.getElementById("registerUsername").value.trim();
    const email = document.getElementById("registerEmail").value.trim().toLowerCase();
    const password = document.getElementById("registerPassword").value;
    const confirmPassword = document.getElementById("registerConfirmPassword").value;
    const acceptedTerms = document.getElementById("acceptTerms").checked;

    if (!username || !email || !password || !confirmPassword) {
        notify("Veuillez remplir tous les champs du formulaire d’inscription.", "error");
        return;
    }

    if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
        notify("O nickname deve ter de 3 a 20 caracteres e usar apenas letras, números ou _.", "error");
        return;
    }

    if (!/^[A-Za-z0-9._+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/.test(email) || email.length > 63) {
        notify("Digite um e-mail válido.", "error");
        return;
    }

    if (password !== confirmPassword) {
        notify("Les mots de passe ne correspondent pas.", "error");
        return;
    }

    if (password.length < 4 || password.length > 100 || !/^[\x21-\x7E]+$/.test(password) || /[\\/]/.test(password)) {
        notify("Le mot de passe doit contenir entre 4 et 100 caractères et ne doit pas comporter d’espaces.", "error");
        return;
    }

    if (!acceptedTerms) {
        notify("Você precisa aceitar as regras du serveur.", "error");
        return;
    }

    emitToGame("auth:register", { username, email, password, confirmPassword });
    notify("Demande d’inscription envoyée.", "info");
});

requestCodeButton.addEventListener("click", () => {
    const username = document.getElementById("recoverUsername").value.trim();

    if (!username) {
        notify("Digite seu nickname primeiro.", "error");
        return;
    }

    if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
        notify("Nickname inválido.", "error");
        return;
    }

    emitToGame("auth:requestRecoveryCode", { username });
    notify("Solicitação de código enviada. Aguarde a confirmação du serveur.", "info");
});

recoverForm.addEventListener("submit", (event) => {
    event.preventDefault();

    const username = document.getElementById("recoverUsername").value.trim();
    const code = document.getElementById("recoverCode").value.trim();
    const newPassword = document.getElementById("recoverNewPassword").value;

    if (!username || !code || !newPassword) {
        notify("Veuillez saisir votre pseudo, le code et le nouveau mot de passe.", "error");
        return;
    }

    if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
        notify("Nickname inválido.", "error");
        return;
    }

    if (!/^\d{7,18}$/.test(code)) {
        notify("O código deve ter entre 7 e 18 números.", "error");
        return;
    }

    if (newPassword.length < 4 || newPassword.length > 100 || !/^[\x21-\x7E]+$/.test(newPassword) || /[\\/]/.test(newPassword)) {
        notify("Le nouveau mot de passe doit contenir entre 4 et 100 caractères et ne doit pas comporter d’espaces.", "error");
        return;
    }

    emitToGame("auth:recover", { username, code, newPassword });
    notify("Demande de modification du mot de passe envoyée.", "info");
});

document.querySelectorAll(".social-button").forEach((button) => {
    button.addEventListener("click", () => {
        const social = button.dataset.social;
        const url = CONFIG.socialLinks[social];

        emitToGame("auth:openLink", {
            social,
            url
        });
    });
});

/*
 * Funções que o Java / CEF pode chamar de volta.
 *
 * Exemplos:
 * window.RevoltAuth.notify("Connexion efetuado.", "success");
 * window.RevoltAuth.openScreen("register");
 * window.RevoltAuth.setRememberedConnexion("BrunoX", "senha");
 */

window.RevoltAuth = {
    notify,

    openScreen(screenName) {
        if (
            screenName === "login" ||
            screenName === "register" ||
            screenName === "recover"
        ) {
            showScreen(screenName);
        }
    },

    setRememberedConnexion(username, password) {
        document.getElementById("loginUsername").value =
            username || "";

        document.getElementById("loginPassword").value =
            password || "";

        document.getElementById("rememberConnexion").checked =
            Boolean(username || password);
    },

    clearFields() {
        document
            .querySelectorAll(".auth-screen input")
            .forEach((input) => {
                if (
                    input.type === "checkbox"
                ) {
                    input.checked = false;
                    return;
                }

                input.value = "";
            });
    }
};


const musicToggle =
    document.getElementById("musicToggle");

function updateMusicToggleState() {
    if (!musicToggle || !loginBackgroundMusic) {
        return;
    }

    const paused =
        loginBackgroundMusic.paused;

    musicToggle.classList.toggle(
        "is-paused",
        paused
    );

    musicToggle.setAttribute(
        "aria-label",
        paused
            ? "Tocar música"
            : "Pausar música"
    );

    musicToggle.title =
        paused
            ? "Tocar música"
            : "Pausar música";
}

if (musicToggle && loginBackgroundMusic) {
    /*
     * O botão resolve também o bloqueio de autoplay:
     * se a música não tiver começado, clicar nele inicia.
     */
    musicToggle.addEventListener("click", () => {
        if (loginBackgroundMusic.paused) {
            loginBackgroundMusic.volume = 0.55;

            const result =
                loginBackgroundMusic.play();

            if (
                result &&
                typeof result.then === "function"
            ) {
                result
                    .then(() => {
                        loginMusicStarted = true;
                        updateMusicToggleState();
                    })
                    .catch(() => {
                        updateMusicToggleState();
                    });
            } else {
                loginMusicStarted = true;
                updateMusicToggleState();
            }
        } else {
            loginBackgroundMusic.pause();
            loginMusicStarted = false;
            updateMusicToggleState();
        }
    });

    loginBackgroundMusic.addEventListener(
        "play",
        updateMusicToggleState
    );

    loginBackgroundMusic.addEventListener(
        "pause",
        updateMusicToggleState
    );

    loginBackgroundMusic.addEventListener(
        "canplay",
        updateMusicToggleState
    );

    updateMusicToggleState();
}


/* =========================================================
   REVOLT - PLAYER DE MÚSICA COMPACTO (FIX)
   ========================================================= */
(() => {
    const player = document.getElementById("revoltMusicPlayer");
    const toggle = document.getElementById("musicToggle");
    const mute = document.getElementById("musicMute");
    const volume = document.getElementById("musicVolume");
    const progress = document.getElementById("musicProgress");
    const current = document.getElementById("musicCurrentTime");
    const duration = document.getElementById("musicDuration");
    const status = document.getElementById("musicStatus");

    if (
        !player ||
        !toggle ||
        !mute ||
        !volume ||
        !progress ||
        !current ||
        !duration ||
        !status
    ) {
        return;
    }

    /*
     * Usa UMA única instância de áudio.
     * Se a música antiga já existir no projeto, reutiliza ela.
     * Caso contrário, cria uma nova usando o arquivo conhecido.
     */
    let music = null;

    try {
        if (
            typeof loginBackgroundMusic !== "undefined" &&
            loginBackgroundMusic &&
            typeof loginBackgroundMusic.play === "function"
        ) {
            music = loginBackgroundMusic;
        }
    } catch (error) {}

    if (!music) {
        music = new Audio("assets/audio/login_music.mp3");
        music.preload = "auto";
        music.loop = true;
    }

    music.volume = 0.55;
    music.loop = true;

    let isSeeking = false;

    function formatTime(value) {
        if (!Number.isFinite(value) || value < 0) {
            return "0:00";
        }

        const minutes = Math.floor(value / 60);
        const seconds = Math.floor(value % 60);

        return `${minutes}:${String(seconds).padStart(2, "0")}`;
    }

    function setToggleState() {
        const paused = music.paused;

        toggle.classList.toggle("is-paused", paused);
        toggle.setAttribute(
            "aria-label",
            paused ? "Tocar música" : "Pausar música"
        );

        status.textContent = paused ? "pausada" : "En lecture";
    }

    function updateTimeUI() {
        const total =
            Number.isFinite(music.duration) && music.duration > 0
                ? music.duration
                : 0;

        const now =
            Number.isFinite(music.currentTime) && music.currentTime >= 0
                ? music.currentTime
                : 0;

        current.textContent = formatTime(now);
        duration.textContent = formatTime(total);

        if (!isSeeking) {
            if (total > 0) {
                progress.value = Math.round((now / total) * 1000);
            } else {
                progress.value = 0;
            }
        }
    }

    function updateVolumeUI() {
        volume.value = Math.round(music.volume * 100);

        const icon = mute.querySelector("span");

        if (icon) {
            icon.textContent =
                music.muted || music.volume <= 0
                    ? "🔇"
                    : "🔊";
        }
    }

    function syncUI() {
        setToggleState();
        updateTimeUI();
        updateVolumeUI();
    }

    function playMusic() {
        try {
            music.loop = true;

            if (music.volume <= 0) {
                music.volume = 0.55;
                volume.value = 55;
            }

            music.muted = false;

            const result = music.play();

            if (result && typeof result.then === "function") {
                result
                    .then(() => {
                        syncUI();
                    })
                    .catch(() => {
                        syncUI();
                    });
            } else {
                syncUI();
            }
        } catch (error) {
            syncUI();
        }
    }

    function pauseMusic() {
        try {
            music.pause();
        } catch (error) {}

        syncUI();
    }

    let musicButtonBusy = false;

    toggle.addEventListener("click", async (event) => {
        event.preventDefault();
        event.stopPropagation();

        if (musicButtonBusy) {
            return;
        }

        musicButtonBusy = true;

        try {
            if (music.paused) {
                const result = music.play();

                if (
                    result &&
                    typeof result.then === "function"
                ) {
                    await result;
                }
            } else {
                music.pause();
            }
        } catch (error) {
            console.warn("[Revolt Music] Falha no play/pause:", error);
        }

        syncUI();

        setTimeout(() => {
            musicButtonBusy = false;
        }, 120);
    });

    mute.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();

        music.muted = !music.muted;
        updateVolumeUI();
    });

    volume.addEventListener("input", () => {
        const nextVolume =
            Math.max(
                0,
                Math.min(
                    1,
                    Number(volume.value) / 100
                )
            );

        music.volume = nextVolume;

        if (nextVolume > 0) {
            music.muted = false;
        }

        updateVolumeUI();
    });

    progress.addEventListener("pointerdown", () => {
        isSeeking = true;
    });

    progress.addEventListener("input", () => {
        const total =
            Number.isFinite(music.duration) && music.duration > 0
                ? music.duration
                : 0;

        if (total <= 0) {
            return;
        }

        const nextTime =
            (Number(progress.value) / 1000) * total;

        current.textContent =
            formatTime(nextTime);
    });

    progress.addEventListener("change", () => {
        const total =
            Number.isFinite(music.duration) && music.duration > 0
                ? music.duration
                : 0;

        if (total > 0) {
            music.currentTime =
                (Number(progress.value) / 1000) * total;
        }

        isSeeking = false;
        updateTimeUI();
    });

    window.addEventListener("pointerup", () => {
        if (!isSeeking) {
            return;
        }

        const total =
            Number.isFinite(music.duration) && music.duration > 0
                ? music.duration
                : 0;

        if (total > 0) {
            music.currentTime =
                (Number(progress.value) / 1000) * total;
        }

        isSeeking = false;
        updateTimeUI();
    });

    [
        "play",
        "playing",
        "pause",
        "loadedmetadata",
        "durationchange",
        "volumechange"
    ].forEach((eventName) => {
        music.addEventListener(
            eventName,
            syncUI
        );
    });

    music.addEventListener(
        "timeupdate",
        updateTimeUI
    );

    /*
     * Atualização visual extra para deixar o relógio/progresso suave,
     * mesmo em CEFs que disparam timeupdate mais devagar.
     */
    setInterval(() => {
        updateTimeUI();
        setToggleState();
    }, 250);

    window.addEventListener("load", () => {
        playMusic();
        syncUI();
    });

    [
        "pointerdown",
        "mousedown",
        "touchstart",
        "keydown"
    ].forEach((eventName) => {
        window.addEventListener(
            eventName,
            () => {
                if (music.paused) {
                    playMusic();
                }
            },
            {
                capture: true,
                once: true
            }
        );
    });

    syncUI();
})();


/* =========================================================
   REVOLT AUTH V6 - RETORNO DA GM + LEMBRAR DADOS
   ========================================================= */
(() => {
    const STORAGE_USER = "revolt_auth_username";
    const STORAGE_PASS = "revolt_auth_password";

    function loadRememberedConnexion() {
        try {
            const username = localStorage.getItem(STORAGE_USER) || "";
            const password = localStorage.getItem(STORAGE_PASS) || "";
            if (username || password) {
                window.RevoltAuth.setRememberedConnexion(username, password);
            }
        } catch (error) {}
    }

    function saveRememberedConnexion(username, password) {
        try {
            localStorage.setItem(STORAGE_USER, username || "");
            localStorage.setItem(STORAGE_PASS, password || "");
        } catch (error) {}
    }

    function clearRememberedConnexion() {
        try {
            localStorage.removeItem(STORAGE_USER);
            localStorage.removeItem(STORAGE_PASS);
        } catch (error) {}
    }

    function handleServerResponse(raw) {
        let response = raw;
        try {
            if (typeof raw === "string") response = JSON.parse(raw);
        } catch (error) {
            notify("O servidor enviou uma resposta inválida.", "error");
            return;
        }

        if (!response || typeof response !== "object") return;

        const typeMap = {
            success: "success",
            danger: "error",
            error: "error",
            warning: "warning",
            info: "info"
        };

        const type = typeMap[String(response.type || "info").toLowerCase()] || "info";
        const title = response.title || undefined;
        const message = response.message || "Resposta recebida du serveur.";

        notify(message, type, { title });

        if (response.screen) {
            window.RevoltAuth.openScreen(response.screen);
        }

        const pending = window.__revoltPendingConnexion;
        if (type === "success" && response.close && pending) {
            if (pending.remember) {
                saveRememberedConnexion(pending.username, pending.password);
            } else {
                clearRememberedConnexion();
            }
            window.__revoltPendingConnexion = null;
        }

        if (type === "success" && response.screen === "login") {
            const registeredName = document.getElementById("registerUsername")?.value?.trim() || "";
            if (registeredName) {
                document.getElementById("loginUsername").value = registeredName;
            }
        }
    }

    window.DXAuth = {
        handleResponse: handleServerResponse,
        showConnexion() {
            if (document.getElementById("screen-login")) showScreen("login");
            loadRememberedConnexion();
        },
        startMusic(force = false) {
            if (!loginBackgroundMusic) return;
            if (force || loginBackgroundMusic.paused) {
                const result = loginBackgroundMusic.play();
                if (result && typeof result.catch === "function") result.catch(() => {});
            }
        },
        stopMusic() {
            if (loginBackgroundMusic) loginBackgroundMusic.pause();
        },
        forceCloseFromGame() {
            if (loginBackgroundMusic) loginBackgroundMusic.pause();
        },
        setRememberedConnexion(username, password) {
            window.RevoltAuth.setRememberedConnexion(username, password);
        },
        clearRememberedConnexion
    };

    loadRememberedConnexion();
})();


/* =========================================================
   CRESTWOOD - LIAISON ANDROID
   Appelé par SAMP.java (openFromNative / handleResponse) et
   prévient l'application quand la page est affichée.
   ========================================================= */
(() => {
    function parse(raw) {
        if (raw && typeof raw === "object") return raw;
        try { return JSON.parse(raw || "{}") || {}; } catch (error) { return {}; }
    }

    // Ouverture demandée par le serveur : {"screen":"login|register","name":"Pseudo"}
    window.DXAuth.openFromNative = function (raw) {
        const data = parse(raw);
        const name = String(data.name || "").trim();

        // Données mémorisées (si la case "Lembrar" a été cochée)
        window.DXAuth.showConnexion();

        if (name) {
            const remembered = document.getElementById("loginUsername").value;
            if (remembered && remembered.toLowerCase() !== name.toLowerCase()) {
                document.getElementById("loginPassword").value = "";
                document.getElementById("rememberConnexion").checked = false;
            }
            ["loginUsername", "registerUsername", "recoverUsername"].forEach((id) => {
                const input = document.getElementById(id);
                if (input) input.value = name;
            });
        }

        if (data.screen === "register" || data.screen === "recover") {
            window.RevoltAuth.openScreen(data.screen);
        }
    };

    function notifyReady() {
        try {
            if (window.SampAuth && typeof window.SampAuth.ready === "function") {
                window.SampAuth.ready();
            }
        } catch (error) {}
    }

    if (document.readyState === "complete") {
        notifyReady();
    } else {
        window.addEventListener("load", notifyReady);
    }
})();
