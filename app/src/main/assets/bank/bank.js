/* ============================================================
   BANCO NEXION — Lógica (bridge com o jogo)
   ============================================================

   window.SampBank é injetado pelo Android (@JavascriptInterface).
   Fluxo:
     JS  --action(tipo, json)-->  nativo  --packet 251-->  servidor
     JS  <--BankApp.openFromNative / onUpdateFromNative--  servidor
*/

const bridge = window.SampBank || null;

let hasPinRegistered = false;
let authenticated = false;      // senha já validada nesta sessão de abertura
let currentRegPin = "";
let currentAuthPin = "";
let activeAction = "";
let pendingAmount = 0;
let pendingTarget = 0;
let authMode = "access";        // "access" (login) | "transaction"

const ICON_DOWN = '<svg class="icon history-item-icon" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M12 7v10M8 13l4 4 4-4"/></svg>';
const ICON_UP   = '<svg class="icon history-item-icon neg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M12 17V7M8 11l4-4 4 4"/></svg>';

// ==============================
// ESCALA RESPONSIVA: mede o tamanho REAL do painel e encaixa
// na tela, sem rolagem e sempre centralizado.
// ==============================
function fitToScreen() {
    const c = document.getElementById("bankContainer");
    if (!c) return;
    const vw = window.innerWidth || 1280;
    const vh = window.innerHeight || 720;

    // offsetWidth/offsetHeight ignoram transform, então não precisa resetar
    const w = Math.max(c.offsetWidth, 1);
    const h = Math.max(c.offsetHeight, 1);

    // 0.93 = painel um pouco menor que o máximo que cabe na tela
    const scale = Math.min(vw / w, vh / h) * 0.93;

    // Guarda na variável CSS --panel-scale: a animação de entrada
    // usa ela junto com o translateY pra subir de baixo pra cima
    c.style.setProperty("--panel-scale", String(Math.max(scale, 0.28)));
}
window.addEventListener("resize", fitToScreen);
window.addEventListener("orientationchange", () => setTimeout(fitToScreen, 120));
document.addEventListener("DOMContentLoaded", fitToScreen);

function showToast(message, type) {
    const toast = document.getElementById("toast");
    toast.innerText = message;
    toast.className = "toast show " + (type || "");
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => toast.classList.remove("show"), 2600);
}

function formatMoney(v) {
    return "$ " + Number(v).toLocaleString("pt-BR");
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[c]);
}

function sendToServer(action, dataJson) {
    if (bridge && typeof bridge.action === "function") {
        bridge.action(action, dataJson || "{}");
    } else {
        showToast("Bridge indisponível (modo preview)", "error");
    }
}

// ==============================
// API chamada pelo NATIVO (C++ -> Java -> JS)
// payload JSON:
// { player, cash, balance, hasPin, ok, msg, history:[{desc,date,amount}] }
// ==============================
window.BankApp = {
    openFromNative(payload) {
        try { payload = typeof payload === "string" ? JSON.parse(payload) : payload; } catch (_) {}
        applyState(payload);

        const container = document.getElementById("bankContainer");
        container.style.display = "block";
        document.body.style.background = "rgba(0,0,0,0.55)";
        authenticated = false;
        setTimeout(fitToScreen, 0);

        // Animação de entrada: sobe de baixo pra cima
        container.classList.remove("animating");
        void container.offsetWidth; // força reflow pra reiniciar a animação
        container.classList.add("animating");

        // Sempre pede a senha ao abrir
        if (!payload.hasPin) {
            currentRegPin = "";
            updatePinDisplays();
            showOverlay("registerOverlay");
        } else {
            openLogin();
        }
        if (payload.msg) showToast(payload.msg, payload.ok ? "success" : "error");
    },

    closeFromNative() {
        closeBank(true);
    },

    // Atualiza saldos/histórico sem reabrir nada
    onUpdateFromNative(payload) {
        try { payload = typeof payload === "string" ? JSON.parse(payload) : payload; } catch (_) {}
        applyState(payload);
        if (payload.msg) showToast(payload.msg, payload.ok ? "success" : "error");

        if (payload.hasPin === true && payload.ok) {
            closeOverlay("registerOverlay");

            if (authMode === "access" && !authenticated) {
                // Login validado pelo servidor -> libera o conteúdo
                authenticated = true;
                currentAuthPin = "";
                updatePinDisplays();
                closeOverlay("authOverlay");
                return;
            }

            // Transação concluída -> fecha modais
            currentAuthPin = "";
            updatePinDisplays();
            closeOverlay("authOverlay");
            closeOverlay("actionModal");
        } else if (!payload.ok) {
            // Senha errada / erro: limpa o PIN pra tentar de novo
            currentAuthPin = "";
            updatePinDisplays();
        }
    },

    notifyFromNative(payload) {
        try { payload = typeof payload === "string" ? JSON.parse(payload) : payload; } catch (_) {}
        if (payload.msg) showToast(payload.msg, payload.ok ? "success" : "error");
    }
};

function applyState(p) {
    if (!p) return;
    if (p.player !== undefined) document.getElementById("playerName").innerText = p.player;
    if (p.cash !== undefined) document.getElementById("playerCash").innerText = formatMoney(p.cash);
    if (p.balance !== undefined) document.getElementById("bankBalance").innerText = formatMoney(p.balance);
    if (p.cardLast !== undefined) document.getElementById("cardNumber").innerHTML = "&bull;&bull;&bull;&bull; &bull;&bull;&bull;&bull; " + p.cardLast;
    if (p.hasPin !== undefined) hasPinRegistered = !!p.hasPin;
    if (Array.isArray(p.history)) renderHistory(p.history);

    // Conteúdo mudou de altura -> reencaixa o painel na tela
    setTimeout(fitToScreen, 0);
}

function renderHistory(items) {
    const list = document.getElementById("historyList");
    list.innerHTML = "";
    if (!items.length) {
        list.innerHTML = '<div class="empty-history">Nenhuma movimentação recente</div>';
        return;
    }
    items.forEach((it) => {
        const positive = Number(it.amount) >= 0;
        const div = document.createElement("div");
        div.className = "history-item";

        const icon = positive ? ICON_DOWN : ICON_UP;
        const amountHtml =
            '<div class="history-item-amount' + (positive ? "" : " neg") + '">' +
            (positive ? "+ " : "- ") + formatMoney(Math.abs(Number(it.amount))) + '</div>';

        div.innerHTML =
            '<div class="history-item-info">' + icon +
            '<div><span>' + escapeHtml(it.desc || "") + '</span>' +
            '<div class="history-item-date">' + escapeHtml(it.date || "") + '</div></div></div>' +
            amountHtml;
        list.appendChild(div);
    });
}

// ==============================
// Login (senha obrigatória ao abrir)
// ==============================
function openLogin() {
    authMode = "access";
    document.getElementById("authSubtitle").innerText = "Insira sua Senha para Acessar";
    document.getElementById("btnAuthConfirm").innerText = "Acessar Conta";
    document.getElementById("btnAuthCancel").style.display = "none";
    currentAuthPin = "";
    updatePinDisplays();
    showOverlay("authOverlay");
}

function confirmAuth() {
    if (currentAuthPin.length !== 4) {
        showToast("Digite os 4 dígitos da senha!", "error");
        return;
    }
    const pin = parseInt(currentAuthPin, 10);
    if (authMode === "access") {
        // O SERVIDOR valida a senha de acesso
        sendToServer("verify_pin", JSON.stringify({ pin: pin }));
        showToast("Verificando...");
    } else {
        validateTransaction(pin);
    }
}

function cancelAuth() {
    closeOverlay("authOverlay");
}

// ==============================
// PIN (teclado)
// ==============================
function updatePinDisplays() {
    document.getElementById("regPinDisplay").innerText = currentRegPin.padEnd(4, "\u2022");
    document.getElementById("authPinDisplay").innerText = currentAuthPin.padEnd(4, "\u2022");
}

function addPin(num, type) {
    if (type === 'reg' && currentRegPin.length < 4) {
        currentRegPin += num;
    } else if (type === 'auth' && currentAuthPin.length < 4) {
        currentAuthPin += num;
    }
    updatePinDisplays();
}

function clearPin(type) {
    if (type === 'reg') {
        currentRegPin = currentRegPin.slice(0, -1);
    } else if (type === 'auth') {
        currentAuthPin = currentAuthPin.slice(0, -1);
    }
    updatePinDisplays();
}

function saveInitialPin() {
    if (currentRegPin.length === 4) {
        // O SERVIDOR registra no SQLite. A resposta fecha este overlay.
        sendToServer("register_pin", JSON.stringify({ pin: parseInt(currentRegPin, 10) }));
        showToast("Registrando senha...");
        currentRegPin = "";
        updatePinDisplays();
    } else {
        showToast("Insira uma senha de 4 dígitos!", "error");
    }
}

// ==============================
// Transações
// ==============================
function openActionModal(type) {
    activeAction = type;
    const tag = document.getElementById("modalTag");
    const passInput = document.getElementById("inputPassport");
    const amountInput = document.getElementById("inputAmount");
    const btnConfirm = document.getElementById("btnModalConfirm");

    amountInput.value = "";
    passInput.value = "";

    if (type === 'sacar') {
        tag.innerText = "Sacar Dinheiro";
        passInput.style.display = "none";
        amountInput.placeholder = "Insira o valor que deseja sacar...";
        btnConfirm.innerText = "Sacar";
    } else if (type === 'depositar') {
        tag.innerText = "Depositar Dinheiro";
        passInput.style.display = "none";
        amountInput.placeholder = "Insira o valor que deseja depositar...";
        btnConfirm.innerText = "Depositar";
    } else if (type === 'transferir') {
        tag.innerText = "Transferir Dinheiro";
        passInput.style.display = "block";
        passInput.placeholder = "Insira o passaporte (ID) do jogador...";
        amountInput.placeholder = "Insira o valor que deseja transferir...";
        btnConfirm.innerText = "Transferir";
    }

    showOverlay("actionModal");
}

function proceedToAuth() {
    const amount = parseInt(document.getElementById("inputAmount").value, 10);
    if (!amount || amount <= 0) {
        showToast("Insira um valor válido!", "error");
        return;
    }
    pendingAmount = amount;
    pendingTarget = parseInt(document.getElementById("inputPassport").value, 10) || 0;

    if (activeAction === 'transferir' && pendingTarget <= 0) {
        showToast("Informe o ID do jogador!", "error");
        return;
    }

    closeOverlay('actionModal');

    authMode = "transaction";
    document.getElementById("authSubtitle").innerText = "Confirme com sua Senha";
    document.getElementById("btnAuthConfirm").innerText = "Confirmar Transação";
    document.getElementById("btnAuthCancel").style.display = "block";
    currentAuthPin = "";
    updatePinDisplays();
    showOverlay("authOverlay");
}

function validateTransaction(pin) {
    const actionMap = { sacar: "withdraw", depositar: "deposit", transferir: "transfer" };
    sendToServer(actionMap[activeAction], JSON.stringify({
        amount: pendingAmount,
        pin: pin,
        target: pendingTarget
    }));
    showToast("Processando transação...");
}

// ==============================
// Overlays / fechamento
// ==============================
function showOverlay(id) {
    document.getElementById(id).style.display = "flex";
}

function closeOverlay(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = "none";
}

let closing = false; // evita fechar duas vezes durante a animação de saída

function closeBank(fromNative) {
    ["registerOverlay", "authOverlay", "actionModal"].forEach(closeOverlay);
    const container = document.getElementById("bankContainer");
    container.classList.remove("animating");

    if (fromNative) {
        // O nativo já escondeu o WebView: só limpa o estado
        closing = false;
        container.classList.remove("closing");
        container.style.display = "none";
        document.body.style.background = "transparent";
        return;
    }

    if (closing) return;
    closing = true;

    // Animação de saída: desce suavemente pra baixo e SÓ ENTÃO
    // avisa o nativo pra esconder o WebView e devolver o controle do jogo
    container.classList.add("closing");
    setTimeout(() => {
        container.classList.remove("closing");
        container.style.display = "none";
        document.body.style.background = "transparent";
        closing = false;
        if (bridge && typeof bridge.close === "function") {
            bridge.close();
        }
    }, 400);
}
