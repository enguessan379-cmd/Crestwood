(() => {
  const root = document.querySelector(".hud-root");
  const cornerAction = document.querySelector(".corner-action");
  const chatAction = document.querySelector(".chat-action");
  const chatComposer = document.querySelector(".floating-chat-composer");
  const chatInput = chatComposer?.querySelector("input");
  const radialMenu = document.querySelector(".radial-menu");
  const gameNotificationStack = document.querySelector(".game-notification-stack");
  const speedFineBanner = document.querySelector(".speed-fine-banner");
  const speedFineSpeed = document.querySelector("[data-speed-fine-speed]");
  const radarBotAlert = document.querySelector(".radarbot-alert");
  const radarBotLimit = document.querySelector("[data-radarbot-limit]");
  const radarBotDistance = document.querySelector("[data-radarbot-distance]");
  const phoneNotificationLayer = document.querySelector(".phone-notification-layer");
  const phoneNotificationSound = document.querySelector("#phoneNotificationSound");
  const idLayer = document.querySelector(".id-layer");
  const idCameraLayer = document.querySelector(".id-camera-layer");
  const idSignLayer = document.querySelector(".id-sign-layer");
  const idPhotoButton = document.querySelector("#idPhotoButton");
  const idPhoto = document.querySelector("#idPhoto");
  const idPhotoHint = document.querySelector("#idPhotoHint");
  const idName = document.querySelector("#idName");
  const idRg = document.querySelector("#idRg");
  const idSex = document.querySelector("#idSex");
  const idBirth = document.querySelector("#idBirth");
  const idExpiry = document.querySelector("#idExpiry");
  const idIssued = document.querySelector("#idIssued");
  const idSignButton = document.querySelector("#idSignButton");
  const idSignatureImage = document.querySelector("#idSignatureImage");
  const idSignatureHint = document.querySelector("#idSignatureHint");
  const idCameraFrame = document.querySelector("#idCameraFrame");
  const idCameraStatus = document.querySelector("#idCameraStatus");
  const idCameraCancel = document.querySelector("#idCameraCancel");
  const idCameraShot = document.querySelector("#idCameraShot");
  const idSignCanvas = document.querySelector("#idSignCanvas");
  const idSignClear = document.querySelector("#idSignClear");
  const idSignCancel = document.querySelector("#idSignCancel");
  const idSignSave = document.querySelector("#idSignSave");
  const firstStepsPanel = document.querySelector("#firstStepsPanel");
  const firstStepsProgress = document.querySelector("#firstStepsProgress");
  const firstStepsToggle = document.querySelector("#firstStepsToggle");
  const firstStepsStart = document.querySelector("#firstStepsStart");
  const firstStepsHint = document.querySelector("#firstStepsHint");
  const firstStepsTrackFill = document.querySelector("#firstStepsTrackFill");
  const capitalFlowLayer = document.querySelector("#capitalFlowLayer");
  const capitalFlowCard = capitalFlowLayer?.querySelector(".capital-flow-card");
  const capitalFlowContent = document.querySelector("#capitalFlowContent");
  const infoFields = new Map(
    Array.from(document.querySelectorAll("[data-info]")).map((item) => [item.dataset.info, item])
  );
  const wantedStars = Array.from(document.querySelectorAll(".wanted-star"));
  const weaponIcon = document.querySelector(".weapon-icon");
  const statusItems = new Map();
  Array.from(document.querySelectorAll("[data-status]")).forEach((item) => {
    const name = item.dataset.status;
    if (!statusItems.has(name)) statusItems.set(name, []);
    statusItems.get(name).push(item);
  });
  const radioButton = document.querySelector(".radio-talk-button");
  const engineButton = document.querySelector(".engine-toggle-button");
  const vehicleLockButton = document.querySelector(".vehicle-lock-button");
  const vehicleLockIcon = vehicleLockButton?.querySelector("img");
  const animationsButton = document.querySelector(".animations-toggle-button");
  const radioFrequencyText = document.querySelector("[data-radio-frequency]");
  const radioFrequencyLayer = document.querySelector(".radio-frequency-layer");
  const radioFrequencyInput = document.querySelector("[data-radio-input]");
  const radioKeypad = document.querySelector("[data-radio-keypad]");
  const radioFrequencyConfirm = document.querySelector("[data-radio-confirm]");
  const radioFrequencyCancel = document.querySelector("[data-radio-cancel]");
  const hudEditorLayer = document.querySelector(".hud-editor-layer");
  const hudEditorSave = document.querySelector("[data-hud-save]");
  const hudEditorReset = document.querySelector("[data-hud-reset]");
  const hudScaleInput = document.querySelector("[data-hud-scale]");
  const movableItems = Array.from(document.querySelectorAll("[data-hud-key]"));
  const statusBars = document.querySelector(".literal-status-bars");
  const vehicleSpeedometer = document.querySelector(".vehicle-speedometer");
  const vehicleSpeedometerFrame = document.querySelector(".literal-speedometer-frame");

  const weaponNames = {
    0: "Sans arme",
    1: "Poing américain",
    3: "Matraque",
    4: "Couteau",
    16: "Grenade",
    22: "Colt 45",
    23: "Pistolet silencieux",
    24: "Desert Eagle",
    25: "Shotgun",
    27: "Combat Shotgun",
    28: "Micro Uzi",
    29: "MP5",
    30: "AK-47",
    31: "M4",
    32: "Tec-9",
    34: "Sniper",
    43: "Appareil photo",
    46: "Parachute"
  };
  const weaponImageIds = new Set(Object.keys(weaponNames).map(Number));
  const bridge = window.SampHud || null;
  const state = {
    health: 100,
    hunger: 100,
    thirst: 100,
    energy: 100,
    armour: 0,
    inVehicle: false,
    speed: 0,
    vehicleHealth: 100,
    fuel: 100,
    time: "00:00",
    diamonds: 0,
    account: 0,
    phone: "000-000",
    money: 0,
    bank: 0,
    location: "Los Santos",
    wanted: 0,
    weaponid: 0,
    ammoClip: 0,
    ammoTotal: 0,
    playerName: "Joueur",
    gender: "male",
    registered: "",
    chatOpen: false,
    menuOpen: false,
    idOpen: false,
    idCameraOpen: false,
    idSigning: false,
    radioVisible: false,
    radioFrequency: 0,
    radioTalking: false,
    engineVisible: false,
    engineOn: false,
    vehicleKeyVisible: false,
    vehicleKeyLocked: false,
    vehicleKeyVehicle: 0,
    hudEditing: false,
    identityPhoto: "",
    identitySignature: "",
    firstStepsMask: 0,
    firstStepsClaimed: true,
    firstStepsClaimPending: false,
    firstStepsExpanded: false,
    selectedFirstStep: 0,
    activeFirstStep: 0,
    flowView: "",
    flowData: {},
    shopCart: {},
    shopSearch: "",
    shopPending: false,
    shopNotice: "",
    shopNoticeKind: ""
  };

  const departmentStoreProducts = [
    { id: "burger", name: "Hamburger", description: "Repas rapide pour calmer la faim.", image: "assets/store/burger.png", priceKey: "burgerprice", fallbackPrice: 40, stackable: true },
    { id: "radio", name: "Radio", description: "Communication par fréquence avec les autres joueurs.", image: "assets/store/radio.png", priceKey: "radioprice", fallbackPrice: 500, unique: true, ownedKey: "ownradio" },
    { id: "water", name: "Eau", description: "Bouteille d'eau pour étancher la soif.", image: "assets/store/water.png", priceKey: "waterprice", fallbackPrice: 20, stackable: true }
  ];
  let phoneNotificationTimer = 0;
  let gameNotificationId = 0;
  const gameNotifications = [];
  let speedFineTimer = 0;
  let radarBotTimer = 0;
  let radarBotCountdownTimer = 0;
  let radarBotEstimate = 0;
  let radarBotLastStamp = 0;
  let radarBotSpeedMps = 0;
  let radarBotLiveVisible = false;
  let signatureDrawing = false;
  let signatureHasInk = false;
  let signatureCanvasReady = false;
  let signatureStrokes = [];
  let activeSignatureStroke = null;
  let identitySignatureChunks = [];
  let idCameraDragging = false;
  let idCameraLastX = 0;
  let idCameraLastY = 0;

  function clampPercent(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return 0;
    return Math.max(0, Math.min(100, Math.round(number)));
  }

  function clampInt(value, min = 0, max = Number.MAX_SAFE_INTEGER) {
    const number = Number(value);
    if (!Number.isFinite(number)) return min;
    return Math.max(min, Math.min(max, Math.trunc(number)));
  }

  function pad2(value) {
    return String(clampInt(value, 0, 99)).padStart(2, "0");
  }

  function formatNumber(value) {
    return clampInt(value).toLocaleString("fr-FR");
  }

  function formatMoney(value) {
    return `${formatNumber(value)} FCFA`;
  }

  function formatBrasiliaTime() {
    const now = new Date();
    try {
      const formatted = new Intl.DateTimeFormat("fr-FR", {
        timeZone: "Africa/Abidjan",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false
      }).format(now);
      return formatted.replace("24:", "00:");
    } catch (_) {
      const utcMs = now.getTime() + now.getTimezoneOffset() * 60000;
      const brasilia = new Date(utcMs);
      return `${pad2(brasilia.getUTCHours())}:${pad2(brasilia.getUTCMinutes())}`;
    }
  }

  let hudClockTimer = 0;

  function tickBrasiliaClock(force = false) {
    const nextTime = formatBrasiliaTime();
    if (!force && state.time === nextTime) return;
    state.time = nextTime;
    setInfo("time", state.time);
  }

  function startBrasiliaClock() {
    tickBrasiliaClock(true);
    if (hudClockTimer) window.clearInterval(hudClockTimer);
    hudClockTimer = window.setInterval(() => tickBrasiliaClock(false), 1000);
  }

  function formatBankMoney(value) {
    const cents = clampInt(value, 0);
    return `${cents.toLocaleString("fr-FR")} FCFA`;
  }

  function isValidPhone(value) {
    return /^\d{3}-\d{3}$/.test(String(value || "")) && String(value) !== "000-000";
  }

  function setStatus(name, value) {
    const items = statusItems.get(name);
    if (!items) return;
    const level = clampPercent(value);
    // Palier d'alerte : sert au CSS (pulsation + valeur affichee quand c'est bas).
    // Le gilet n'a pas d'alerte, il est simplement masque quand il est vide.
    const tier = name === "armour"
      ? (level <= 0 ? "empty" : "ok")
      : (level <= 15 ? "critical" : level <= 30 ? "low" : "ok");
    items.forEach((item) => {
      item.style.setProperty("--status-fill", `${level}%`);
      item.setAttribute("aria-valuenow", String(level));
      item.dataset.level = tier;
      item.dataset.value = String(level);
    });
  }

  function renderDrivingHud() {
    const showBothForEditor = state.hudEditing;
    if (statusBars) statusBars.hidden = state.inVehicle && !showBothForEditor;
    if (vehicleSpeedometer) {
      vehicleSpeedometer.hidden = !state.inVehicle && !showBothForEditor;
    }
    vehicleSpeedometerFrame?.contentWindow?.postMessage({
      hud: true,
      vehicle: state.inVehicle || showBothForEditor,
      speed: clampInt(state.speed, 0, 999),
      fuel: clampPercent(state.fuel),
      engine: clampPercent(state.vehicleHealth),
      rpm: 0,
      health: clampPercent(state.health),
      armour: clampPercent(state.armour),
      hunger: clampPercent(state.hunger),
      thirst: clampPercent(state.thirst),
      stress: clampPercent(state.energy),
      oxigen: 0,
      showbelt: false,
      seatbelt: false,
      direction: "",
      street: ""
    }, "*");
    root?.classList.toggle("vehicle-hud-active", state.inVehicle);
  }

  vehicleSpeedometerFrame?.addEventListener("load", renderDrivingHud);

  function setInfo(name, value) {
    const item = infoFields.get(name);
    if (item) item.textContent = value;
  }

  function updateWantedStars() {
    wantedStars.forEach((star, index) => {
      star.classList.toggle("active", index < state.wanted);
    });
  }

  function updateWeaponDisplay() {
    const weaponid = weaponImageIds.has(state.weaponid) ? state.weaponid : 0;
    if (weaponIcon) {
      weaponIcon.src = `assets/weapons/GTASA_Weapon${weaponid}.png`;
      weaponIcon.alt = weaponNames[weaponid] || "Arme";
    }

    const ammoReserve = Math.max(0, state.ammoTotal - state.ammoClip);
    setInfo("ammoClip", String(state.ammoClip));
    setInfo("ammoReserve", String(ammoReserve));
  }

  function renderInfoPanel() {
    setInfo("time", state.time);
    setInfo("coin", formatNumber(state.diamonds));
    setInfo("account", formatNumber(state.account));
    setInfo("phone", state.phone);
    setInfo("money", formatMoney(state.money));
    setInfo("bank", formatBankMoney(state.bank));
    setInfo("location", state.location || "Los Santos");
    updateWantedStars();
    updateWeaponDisplay();
  }

  function updateBridgeInteraction() {
    bridge?.setInteractionState?.(state.chatOpen, state.menuOpen);
  }

  function setHudEditor(open) {
    state.hudEditing = Boolean(open);
    root?.classList.toggle("hud-editing", state.hudEditing);
    if (hudEditorLayer) hudEditorLayer.hidden = !state.hudEditing;
    if (radarBotAlert) {
      radarBotAlert.classList.toggle("hud-edit-preview", state.hudEditing);
      if (state.hudEditing) {
        radarBotAlert.hidden = false;
        radarBotAlert.classList.add("visible");
        radarBotAlert.classList.remove("danger");
        if (radarBotLimit) radarBotLimit.textContent = radarBotLimit.textContent || "80";
        if (radarBotDistance) radarBotDistance.textContent = radarBotDistance.textContent || "45";
      } else if (!radarBotLiveVisible) {
        radarBotAlert.classList.remove("visible", "danger");
        radarBotAlert.hidden = true;
      }
    }
    bridge?.setHudEditorOpen?.(state.hudEditing);
    renderDrivingHud();
    reframeHud();
    syncTouchBounds();
    renderRadio();
    renderEngine();
    renderVehicleLock();
  }

  function closeInteractive() {
    const hadChat = state.chatOpen;
    const hadMenu = state.menuOpen;
    const hadIdentity = state.idOpen || state.idCameraOpen || state.idSigning;
    state.chatOpen = false;
    state.menuOpen = false;
    chatComposer?.classList.remove("open");
    chatAction?.classList.remove("chat-open");
    chatAction?.setAttribute("aria-label", "Ouvrir le chat");
    radialMenu?.classList.remove("open");
    cornerAction?.classList.remove("menu-open");
    cornerAction?.setAttribute("aria-label", "Ouvrir le menu rapide");
    if (hadIdentity) hideIdentity();
    if (hadChat) hideKeyboard();
    if (hadChat || hadMenu) updateBridgeInteraction();
    syncTouchBounds();
  }

  function scheduleHudSync() {
    applyHudLayout();
    reframeHud();
    renderDrivingHud();
    renderRadio();
    renderEngine();
    renderVehicleLock();
    syncTouchBounds();
    [0, 80, 180, 420, 800].forEach((delay) => {
      window.setTimeout(() => {
        if (!hudDrag) { applyHudLayout(); reframeHud(); }
        syncTouchBounds();
      }, delay);
    });
  }

  // v4 descarta coordenadas absolutas gravadas antes da viewport adaptativa.
  // Elas eram a causa de o HUD menor continuar nas posicoes antigas da tela.
  const hudLayoutKey = "crestwood.hud.layout.v1";
  let hudDrag = null;
  let activeHudItem = movableItems[0] || null;

  function clampScale(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return 1;
    return Math.max(0.7, Math.min(1.3, number));
  }

  function hudItemScale(item) {
    return clampScale(item?.dataset.hudScale || item?.style.getPropertyValue("--hud-item-scale") || 1);
  }

  function setHudItemScale(item, scale) {
    if (!item) return;
    const next = clampScale(scale);
    item.dataset.hudScale = String(next);
    item.style.setProperty("--hud-item-scale", String(next));
  }

  function selectHudItem(item) {
    if (!item) return;
    activeHudItem?.classList.remove("hud-edit-selected");
    activeHudItem = item;
    activeHudItem.classList.add("hud-edit-selected");
    if (hudScaleInput) hudScaleInput.value = String(Math.round(hudItemScale(activeHudItem) * 100));
  }

  function loadHudLayout() {
    try {
      const data = JSON.parse(localStorage.getItem(hudLayoutKey) || "{}") || {};
      if (data.radial) {
        delete data.radial;
        localStorage.setItem(hudLayoutKey, JSON.stringify(data));
      }
      return data;
    } catch (_) {
      return {};
    }
  }

  function saveHudLayout() {
    const data = {};
    const viewportWidth = Math.max(1, window.innerWidth);
    const viewportHeight = Math.max(1, window.innerHeight);
    movableItems.forEach((item) => {
      const key = item.dataset.hudKey;
      if (!key) return;
      const rect = item.getBoundingClientRect();
      data[key] = {
        xRatio: Number((rect.left / viewportWidth).toFixed(6)),
        yRatio: Number((rect.top / viewportHeight).toFixed(6)),
        scale: Number(hudItemScale(item).toFixed(2))
      };
    });
    localStorage.setItem(hudLayoutKey, JSON.stringify(data));
    syncTouchBounds();
  }

  function applyHudLayout() {
    const data = loadHudLayout();
    movableItems.forEach((item) => {
      const key = item.dataset.hudKey;
      const pos = data[key];
      if (!pos) return;
      const xRatio = Number(pos.xRatio);
      const yRatio = Number(pos.yRatio);
      if (!Number.isFinite(xRatio) || !Number.isFinite(yRatio)) return;
      const rect = item.getBoundingClientRect();
      const maxX = Math.max(0, window.innerWidth - rect.width);
      const maxY = Math.max(0, window.innerHeight - rect.height);
      const x = Math.max(0, Math.min(maxX, xRatio * window.innerWidth));
      const y = Math.max(0, Math.min(maxY, yRatio * window.innerHeight));
      item.style.left = `${x}px`;
      item.style.top = `${y}px`;
      item.style.right = "auto";
      item.style.bottom = "auto";
      item.style.transform = "none";
      item.style.translate = "";
      setHudItemScale(item, pos.scale || 1);
    });
    selectHudItem(activeHudItem || movableItems[0]);
    window.setTimeout(syncTouchBounds, 0);
  }

  function resetHudLayout() {
    localStorage.removeItem(hudLayoutKey);
    movableItems.forEach((item) => {
      item.style.left = "";
      item.style.top = "";
      item.style.right = "";
      item.style.bottom = "";
      item.style.transform = "";
      item.style.translate = "";
      item.dataset.hudScale = "1";
      item.style.removeProperty("--hud-item-scale");
    });
    selectHudItem(movableItems[0]);
    reframeHud();
    syncTouchBounds();
  }

  // Recadrage : garde chaque element du HUD entierement visible dans l'ecran (marge de securite),
  // quelle que soit la taille / le ratio / l'encoche de l'appareil. Utilise la propriete CSS "translate"
  // (independante de transform et scale) et ne touche pas aux positions enregistrees par l'editeur.
  const REFRAME_MARGIN = 6;
  function reframeHud() {
    if (state.hudEditing) return;
    const viewW = Math.max(1, window.innerWidth);
    const viewH = Math.max(1, window.innerHeight);
    movableItems.forEach((item) => {
      if (item.dataset.hudKey === "speedometer") return; // sa zone transparente deborde volontairement du bas
      if (item.style.left) return; // position choisie dans l'editeur : deja bornee par applyHudLayout()
      item.style.translate = "";
      if (item.hidden) return;
      const rect = item.getBoundingClientRect();
      if (rect.width < 2 || rect.height < 2) return;
      let dx = 0;
      let dy = 0;
      if (rect.left < REFRAME_MARGIN) dx = REFRAME_MARGIN - rect.left;
      else if (rect.right > viewW - REFRAME_MARGIN) dx = viewW - REFRAME_MARGIN - rect.right;
      if (rect.top < REFRAME_MARGIN) dy = REFRAME_MARGIN - rect.top;
      else if (rect.bottom > viewH - REFRAME_MARGIN) dy = viewH - REFRAME_MARGIN - rect.bottom;
      if (dx || dy) item.style.translate = `${Math.round(dx)}px ${Math.round(dy)}px`;
    });
  }

  function syncTouchBounds() {
    if (!bridge?.setTouchBounds) return;
    const entries = [`viewport=${Math.round(window.innerWidth)},${Math.round(window.innerHeight)}`];
    const add = (name, element) => {
      if (!element || element.hidden) return;
      const rect = element.getBoundingClientRect();
      if (rect.width < 2 || rect.height < 2) return;
      entries.push(`${name}=${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.right)},${Math.round(rect.bottom)}`);
    };
    add("radio", radioButton);
    add("engine", engineButton);
    add("vehicleLock", vehicleLockButton);
    add("animations", animationsButton);
    add("quick", document.querySelector(".quick-actions"));
    add("weapon", document.querySelector(".weapon-panel"));
    add("steps", firstStepsPanel);
    if (state.chatOpen) add("chat", chatComposer);
    bridge.setTouchBounds(entries.join(";"));
  }

  function eventPoint(event) {
    const touch = event.changedTouches && event.changedTouches[0] ? event.changedTouches[0] : null;
    return {
      x: touch ? touch.clientX : event.clientX,
      y: touch ? touch.clientY : event.clientY
    };
  }

  function hideKeyboard() {
    chatInput?.blur();
    bridge?.hideAndroidKeyboard?.();
  }

  function focusChatInput() {
    if (!chatInput || !state.chatOpen) return;
    try {
      chatInput.focus({ preventScroll: true });
    } catch (_) {
      chatInput.focus();
    }
    const end = chatInput.value.length;
    try {
      chatInput.setSelectionRange(end, end);
    } catch (_) {}
  }

  function showKeyboard() {
    bridge?.showAndroidKeyboard?.();
  }

  function setRadialMenu(open) {
    state.menuOpen = Boolean(open);
    radialMenu?.classList.toggle("open", state.menuOpen);
    cornerAction?.classList.toggle("menu-open", state.menuOpen);
    cornerAction?.setAttribute("aria-label", state.menuOpen ? "Fermer le menu rapide" : "Ouvrir le menu rapide");
    updateBridgeInteraction();
  }

  function setChatComposer(open) {
    state.chatOpen = Boolean(open);
    chatComposer?.classList.toggle("open", state.chatOpen);
    chatAction?.classList.toggle("chat-open", state.chatOpen);
    chatAction?.setAttribute("aria-label", state.chatOpen ? "Fermer le chat" : "Ouvrir le chat");
    updateBridgeInteraction();

    if (state.chatOpen) {
      setRadialMenu(false);
      showKeyboard();
    } else {
      hideKeyboard();
    }
  }

  function renderRadio() {
    if (!radioButton) return;
    radioButton.hidden = !(state.radioVisible || state.hudEditing);
    radioButton.classList.toggle("talking", state.radioTalking);
    if (radioFrequencyText) {
      radioFrequencyText.textContent = state.radioFrequency > 0 ? String(state.radioFrequency) : "OFF";
    }
    window.setTimeout(syncTouchBounds, 0);
    window.setTimeout(syncTouchBounds, 120);
    window.setTimeout(syncTouchBounds, 420);
  }

  function renderEngine() {
    if (!engineButton) return;
    engineButton.hidden = !(state.engineVisible || state.hudEditing);
    engineButton.classList.toggle("engine-on", state.engineOn);
    engineButton.setAttribute("aria-label", state.engineOn ? "Éteindre le moteur" : "Démarrer le moteur");
    window.setTimeout(syncTouchBounds, 0);
    window.setTimeout(syncTouchBounds, 120);
  }

  function renderVehicleLock() {
    if (!vehicleLockButton) return;
    vehicleLockButton.hidden = !(state.vehicleKeyVisible || state.hudEditing);
    vehicleLockButton.classList.toggle("locked", state.vehicleKeyLocked);
    vehicleLockButton.setAttribute("aria-label", state.vehicleKeyLocked ? "Déverrouiller le véhicule" : "Verrouiller le véhicule");
    if (vehicleLockIcon) {
      vehicleLockIcon.src = state.vehicleKeyLocked
        ? "assets/vehicle-locked.png"
        : "assets/vehicle-unlocked.png";
    }
    window.setTimeout(syncTouchBounds, 0);
    window.setTimeout(syncTouchBounds, 120);
  }

  function openRadioFrequency() {
    if (!radioFrequencyLayer) return;
    radioFrequencyLayer.hidden = false;
    radioFrequencyInput.value = state.radioFrequency > 0 ? String(state.radioFrequency) : "";
    window.setTimeout(() => radioFrequencyInput?.focus(), 0);
    bridge?.setHudEditorOpen?.(true);
    syncTouchBounds();
  }

  function closeRadioFrequency() {
    if (radioFrequencyLayer) radioFrequencyLayer.hidden = true;
    bridge?.setHudEditorOpen?.(state.hudEditing);
    syncTouchBounds();
  }

  function sendRadioAction(action, value = "") {
    if (bridge?.radioAction) {
      bridge.radioAction(action, String(value || ""));
      return;
    }
    sendMessage(`/radioact ${action}${value ? ` ${value}` : ""}`);
  }

  function sendEngineAction(action = "toggle") {
    if (state.hudEditing || !state.engineVisible) return;
    if (bridge?.engineAction) {
      bridge.engineAction(action);
      return;
    }
    sendMessage("/motor");
  }

  function startRadioTalk() {
    if (state.hudEditing || !state.radioVisible) return;
    if (state.radioTalking) return;
    if (state.radioFrequency < 1) {
      openRadioFrequency();
      return;
    }
    state.radioTalking = true;
    renderRadio();
    sendRadioAction("start");
  }

  function stopRadioTalk() {
    if (!state.radioTalking) return;
    state.radioTalking = false;
    renderRadio();
    sendRadioAction("stop");
  }

  function updateRadioFrequencyInput(key) {
    if (!radioFrequencyInput) return;
    const current = String(radioFrequencyInput.value || "");
    if (key === "clear") {
      radioFrequencyInput.value = "";
      return;
    }
    if (key === "back") {
      radioFrequencyInput.value = current.slice(0, -1);
      return;
    }
    const next = `${current}${key}`.replace(/^0+/, "").slice(0, 3);
    radioFrequencyInput.value = next;
  }

  function saveRadioFrequency() {
    const rawValue = String(radioFrequencyInput?.value || "").trim();
    const parsedValue = Number(rawValue);
    if (!rawValue || !Number.isFinite(parsedValue)) return;
    const value = clampInt(parsedValue, 1, 300);
    state.radioFrequency = value;
    renderRadio();
    sendRadioAction("freq", value);
    closeRadioFrequency();
  }

  function beginHudDrag(event, element) {
    if (!state.hudEditing) return;
    selectHudItem(element);
    const point = eventPoint(event);
    const rect = element.getBoundingClientRect();
    hudDrag = {
      element,
      offsetX: point.x - rect.left,
      offsetY: point.y - rect.top
    };
    element.setPointerCapture?.(event.pointerId);
    event.preventDefault();
  }

  function moveHudDrag(event) {
    if (!hudDrag) return;
    const point = eventPoint(event);
    const rect = hudDrag.element.getBoundingClientRect();
    const x = Math.max(0, Math.min(window.innerWidth - rect.width, point.x - hudDrag.offsetX));
    const y = Math.max(0, Math.min(window.innerHeight - rect.height, point.y - hudDrag.offsetY));
    hudDrag.element.style.left = `${x}px`;
    hudDrag.element.style.top = `${y}px`;
    hudDrag.element.style.right = "auto";
    hudDrag.element.style.bottom = "auto";
    hudDrag.element.style.transform = "none";
    hudDrag.element.style.translate = "";
    syncTouchBounds();
    event.preventDefault();
  }

  function endHudDrag(event) {
    if (!hudDrag) return;
    hudDrag.element.releasePointerCapture?.(event.pointerId);
    hudDrag = null;
    syncTouchBounds();
  }

  function parsePayload(payload) {
    if (!payload) return {};
    if (typeof payload === "object") return payload;

    const text = String(payload).trim();
    if (!text) return {};

    try {
      return JSON.parse(text);
    } catch (_) {
      const data = {};
      text.split(/[;&\s]+/).forEach((entry) => {
        const [key, value] = entry.split("=");
        if (!key || value === undefined) return;
        data[key.trim()] = value.trim();
      });
      return data;
    }
  }

  function decodePayload(value) {
    const raw = String(value || "");
    const normalized = raw.replace(/#([0-9A-Fa-f]{2})/g, "%$1");
    try { return decodeURIComponent(normalized); } catch (_) {}
    try { return decodeURIComponent(raw); } catch (_) {}
    return raw;
  }

  function escapeText(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function previewText(value) {
    const text = String(value || "").replace(/\s+/g, " ").trim();
    if (text.length <= 58) return text;
    return `${text.slice(0, 56)}...`;
  }

  function firstName(value) {
    return String(value || "Joueur").trim().split(/\s+/)[0] || "Joueur";
  }

  function normalizeName(value) {
    return String(value || "Joueur").replace(/_/g, " ").replace(/\s+/g, " ").trim() || "Joueur";
  }

  function getStoredIdValue(suffix) {
    if (suffix === "photo") return state.identityPhoto || "";
    if (suffix === "signature") return state.identitySignature || "";
    return "";
  }

  function setStoredIdValue(suffix, value) {
    const cleanValue = String(value || "");
    if (suffix === "photo") {
      state.identityPhoto = cleanValue;
      bridge?.saveIdentityPhoto?.(cleanValue);
      return;
    }
    if (suffix === "signature") {
      state.identitySignature = cleanValue;
      bridge?.saveIdentitySignature?.(cleanValue);
    }
  }

  function formatIdentityDate(value) {
    const raw = decodePayload(value || "").trim();
    if (/^\d{2}\/\d{2}\/\d{4}$/.test(raw)) return raw;
    const iso = raw.match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (iso) return `${iso[3]}/${iso[2]}/${iso[1]}`;
    return new Date().toLocaleDateString("fr-FR");
  }

  function genderLetter(value) {
    return /^f/i.test(String(value || "")) ? "F" : "M";
  }

  function setBridgeIdentityOpen(open) {
    bridge?.setIdentityOpen?.(Boolean(open));
  }

  function renderIdentity() {
    const name = normalizeName(state.playerName);
    const registered = formatIdentityDate(state.registered);
    const photoUrl = getStoredIdValue("photo");
    const signatureUrl = signatureToImageSrc(getStoredIdValue("signature"));

    if (idName) idName.textContent = name;
    if (idRg) idRg.textContent = state.account > 0 ? String(state.account).padStart(3, "0") : "000";
    if (idSex) idSex.textContent = genderLetter(state.gender);
    if (idBirth) idBirth.textContent = registered;
    if (idExpiry) idExpiry.textContent = "31/12/2099";
    if (idIssued) idIssued.textContent = `Délivrée le ${registered}`;

    if (idPhoto) {
      idPhoto.src = photoUrl || "assets/rg/boneco.png";
      idPhotoButton?.classList.toggle("has-photo", Boolean(photoUrl));
    }
    if (idPhotoHint) idPhotoHint.textContent = photoUrl ? "Mettre à jour la photo" : "Touche pour ajouter une photo";

    if (idSignatureImage) {
      if (signatureUrl) {
        idSignatureImage.src = signatureUrl;
        idSignatureImage.hidden = false;
      } else {
        idSignatureImage.hidden = true;
        idSignatureImage.removeAttribute("src");
      }
    }
    if (idSignatureHint) {
      idSignatureHint.textContent = signatureUrl ? "Mettre à jour la signature" : "Signer la carte d'identité";
      idSignatureHint.hidden = Boolean(signatureUrl);
    }
  }

  function showIdentity(requestAssets = true) {
    closeSignature();
    closeIdCamera(false);
    setChatComposer(false);
    setRadialMenu(false);
    state.idOpen = true;
    renderIdentity();
    if (idLayer) idLayer.hidden = false;
    setBridgeIdentityOpen(true);
    if (requestAssets) bridge?.requestIdentityAssets?.();
  }

  function hideIdentity() {
    closeSignature();
    closeIdCamera(true);
    state.idOpen = false;
    if (idLayer) idLayer.hidden = true;
    setBridgeIdentityOpen(false);
  }

  let phoneCameraMode = false;
  const phoneCameraHint = "Cadre ta photo puis appuie sur « Prendre la photo »";

  function savePhonePhoto(url) {
    try {
      const key = "crestwoodPhonePhotos";
      let list = [];
      try { list = JSON.parse(localStorage.getItem(key) || "[]"); } catch (_) { list = []; }
      if (!Array.isArray(list)) list = [];
      list.unshift(url);
      while (list.length > 24) list.pop();
      for (;;) {
        try {
          localStorage.setItem(key, JSON.stringify(list));
          break;
        } catch (_) {
          if (list.length <= 1) break;
          list.pop();
        }
      }
      localStorage.setItem("crestwoodPhoneOpenApp", "photos");
    } catch (_) {}
  }

  function openPhoneCamera() {
    if (!idCameraLayer) return;
    phoneCameraMode = true;
    document.body.classList.add("phone-camera-mode");
    state.idCameraOpen = true;
    idCameraLayer.hidden = false;
    if (idCameraStatus) idCameraStatus.textContent = phoneCameraHint;
    bridge?.openRgCamera?.();
  }

  function openIdCamera() {
    if (!state.idOpen || !idCameraLayer) return;
    state.idCameraOpen = true;
    idCameraLayer.hidden = false;
    if (idLayer) {
      idLayer.classList.add("camera-active");
      idLayer.hidden = true;
    }
    if (idCameraStatus) idCameraStatus.textContent = "Glisse vers le bas pour cadrer le visage";
    setBridgeIdentityOpen(true);
    bridge?.openRgCamera?.();
  }

  function closeIdCamera(notifyNative = true) {
    if (phoneCameraMode) {
      if (notifyNative) bridge?.phoneCameraDone?.(false);
      return;
    }
    if (!state.idCameraOpen && idCameraLayer?.hidden !== false) return;
    state.idCameraOpen = false;
    if (idCameraLayer) idCameraLayer.hidden = true;
    if (idLayer) {
      idLayer.classList.remove("camera-active");
      if (state.idOpen) idLayer.hidden = false;
    }
    if (idCameraStatus) idCameraStatus.textContent = "Glisse vers le bas pour cadrer le visage";
    if (notifyNative) bridge?.closeRgCamera?.();
  }

  function captureIdPhoto() {
    if (!idCameraFrame) return;
    const rect = idCameraFrame.getBoundingClientRect();
    if (idCameraStatus) idCameraStatus.textContent = "Capture en cours...";
    bridge?.captureRgPhoto?.(
      Math.round(rect.left),
      Math.round(rect.top),
      Math.round(rect.width),
      Math.round(rect.height),
      Math.round(window.innerWidth),
      Math.round(window.innerHeight)
    );
  }

  function adjustIdCamera(dx, dy) {
    bridge?.adjustRgCamera?.(Math.round(dx), Math.round(dy));
  }

  function setupSignatureCanvas() {
    if (!idSignCanvas) return null;
    const rect = idSignCanvas.getBoundingClientRect();
    const dpr = Math.max(1, Math.min(2, window.devicePixelRatio || 1));
    const width = Math.max(320, Math.round(rect.width * dpr));
    const height = Math.max(110, Math.round(rect.height * dpr));
    if (idSignCanvas.width !== width || idSignCanvas.height !== height) {
      idSignCanvas.width = width;
      idSignCanvas.height = height;
    }
    const ctx = idSignCanvas.getContext("2d");
    if (!ctx) return null;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, rect.width, rect.height);
    ctx.lineWidth = Math.max(2.4, rect.width * 0.006);
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.strokeStyle = "#132238";
    signatureCanvasReady = true;
    signatureHasInk = false;
    signatureStrokes = [];
    activeSignatureStroke = null;
    return ctx;
  }

  function pointerOnCanvas(event) {
    const rect = idSignCanvas.getBoundingClientRect();
    return {
      x: Math.max(0, Math.min(rect.width, event.clientX - rect.left)),
      y: Math.max(0, Math.min(rect.height, event.clientY - rect.top))
    };
  }

  function openSignature() {
    if (!idSignLayer) return;
    state.idSigning = true;
    idSignLayer.hidden = false;
    bridge?.setIdentitySigning?.(true);
    window.requestAnimationFrame(setupSignatureCanvas);
    setBridgeIdentityOpen(true);
  }

  function closeSignature() {
    state.idSigning = false;
    signatureDrawing = false;
    if (idSignLayer) idSignLayer.hidden = true;
    bridge?.setIdentitySigning?.(false);
  }

  function saveSignature() {
    if (!idSignCanvas || !signatureHasInk) {
      closeSignature();
      return;
    }
    setStoredIdValue("signature", buildSignaturePayload());
    closeSignature();
    renderIdentity();
  }

  function buildSignaturePayload() {
    if (!idSignCanvas || !signatureStrokes.length) return "";
    const rect = idSignCanvas.getBoundingClientRect();
    const width = Math.max(320, Math.round(rect.width));
    const height = Math.max(110, Math.round(rect.height));
    const strokes = signatureStrokes
      .filter((stroke) => stroke.length > 1)
      .slice(-6)
      .map((stroke) => {
        const step = Math.max(1, Math.ceil(stroke.length / 28));
        return stroke
          .filter((_, index) => index % step === 0 || index === stroke.length - 1)
          .slice(0, 34)
          .map((point) => `${Math.round(point.x)},${Math.round(point.y)}`)
          .join(".");
      })
      .filter(Boolean)
      .join(";");
    return strokes ? `sigv1:${width}:${height}:${strokes}` : "";
  }

  function signatureToImageSrc(value) {
    const raw = String(value || "");
    if (!raw) return "";
    if (!raw.startsWith("sigv1:")) return raw;

    const parts = raw.split(":");
    const width = clampInt(parts[1], 1, 2000) || 480;
    const height = clampInt(parts[2], 1, 1000) || 160;
    const strokes = parts.slice(3).join(":").split(";").map((stroke) => stroke.trim()).filter(Boolean);
    const paths = strokes
      .map((stroke) => {
        const points = stroke.split(".")
          .filter((point) => /^\d+,\d+$/.test(point))
          .join(" ");
        if (!points) return "";
        return `<polyline points="${points}" fill="none" stroke="#132238" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/>`;
      })
      .join("");
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}">${paths}</svg>`;
    return `data:image/svg+xml,${encodeURIComponent(svg)}`;
  }

  function rgCameraNativeStatus(status, url, message) {
    const cleanStatus = String(status || "");
    if (idCameraStatus) {
      if (cleanStatus === "capturing") idCameraStatus.textContent = "Capture en cours...";
      else if (cleanStatus === "uploading") idCameraStatus.textContent = "Enregistrement de la photo...";
      else if (message) idCameraStatus.textContent = message;
    }

    if (cleanStatus === "captured" && url && phoneCameraMode) {
      savePhonePhoto(url);
      bridge?.phoneCameraDone?.(true);
      return;
    }
    if (cleanStatus === "captured" && url) {
      setStoredIdValue("photo", url);
      closeIdCamera(false);
      renderIdentity();
    } else if (cleanStatus === "error") {
      if (idCameraStatus) idCameraStatus.textContent = message || "Impossible d'enregistrer.";
      window.setTimeout(() => {
        if (state.idCameraOpen && idCameraStatus) idCameraStatus.textContent = phoneCameraMode ? phoneCameraHint : "Glisse vers le bas pour cadrer le visage";
      }, 1800);
    } else if (cleanStatus === "closed") {
      closeIdCamera(false);
    }
  }

  function renderFirstSteps() {
    if (!firstStepsPanel) return;
    firstStepsPanel.hidden = state.firstStepsClaimed;
    if (state.firstStepsClaimed) {
      syncTouchBounds();
      return;
    }
    const bits = [1, 2, 4, 8, 16];
    const completed = bits.filter((bit) => (state.firstStepsMask & bit) === bit).length;
    const allCompleted = completed === bits.length;
    const firstIncomplete = bits.find((bit) => (state.firstStepsMask & bit) !== bit) || 0;
    if (!state.selectedFirstStep || (state.firstStepsMask & state.selectedFirstStep) === state.selectedFirstStep) {
      state.selectedFirstStep = firstIncomplete;
    }
    if (state.activeFirstStep && (state.firstStepsMask & state.activeFirstStep) === state.activeFirstStep) {
      state.activeFirstStep = 0;
    }

    firstStepsPanel.classList.toggle("expanded", state.firstStepsExpanded);
    firstStepsPanel.setAttribute("aria-expanded", String(state.firstStepsExpanded));
    firstStepsToggle?.setAttribute("aria-expanded", String(state.firstStepsExpanded));
    if (firstStepsProgress) firstStepsProgress.textContent = `${completed}/${bits.length}`;
    if (firstStepsTrackFill) {
      const progress = completed <= 0 ? 0 : ((completed - 1) / (bits.length - 1)) * 100;
      firstStepsTrackFill.style.width = `${progress}%`;
    }

    firstStepsPanel.querySelectorAll("[data-step-node]").forEach((node) => {
      const bit = Number(node.dataset.stepNode) || 0;
      node.classList.toggle("done", (state.firstStepsMask & bit) === bit);
      node.classList.toggle("current", bit === state.selectedFirstStep && (state.firstStepsMask & bit) !== bit);
    });

    firstStepsPanel.querySelectorAll("[data-first-step]").forEach((button) => {
      const bit = Number(button.dataset.firstStep) || 0;
      const done = (state.firstStepsMask & bit) === bit;
      const selected = bit === state.selectedFirstStep && !done;
      button.classList.toggle("done", done);
      button.classList.toggle("selected", selected);
      button.classList.toggle("active", bit === state.activeFirstStep && !done);
      button.classList.toggle("recommended", bit === firstIncomplete && !done);
      button.setAttribute("aria-selected", String(selected));
      button.setAttribute("aria-disabled", String(done));
    });

    const selectedCard = firstStepsPanel.querySelector(`[data-first-step="${state.selectedFirstStep}"] .first-step-copy strong`);
    if (firstStepsHint) {
      firstStepsHint.textContent = allCompleted
        ? "Tout est terminé. Récupère ta récompense."
        : selectedCard
          ? `${selectedCard.textContent}. Lance l'objectif pour marquer la destination.`
          : "Sélectionne le prochain objectif.";
    }
    if (firstStepsStart) {
      const hasSelection = Boolean(state.selectedFirstStep) && (state.firstStepsMask & state.selectedFirstStep) !== state.selectedFirstStep;
      firstStepsStart.disabled = state.firstStepsClaimPending || (!allCompleted && !hasSelection);
      const label = firstStepsStart.querySelector("b");
      if (label) {
        label.textContent = allCompleted
          ? (state.firstStepsClaimPending ? "RESGATANDO..." : "RÉCUPÉRER LA RÉCOMPENSE")
          : state.activeFirstStep === state.selectedFirstStep ? "OBJECTIF EN COURS" : "LANCER L'OBJECTIF";
      }
    }

    syncTouchBounds();
    [120, 300, 520].forEach((delay) => window.setTimeout(syncTouchBounds, delay));
  }

  function flowIcon(kind) {
    if (kind === "documents") return `<svg viewBox="0 0 24 24"><path d="M6 3h9l3 3v15H6z"/><path d="M15 3v4h4M9 11h6M9 15h6"/></svg>`;
    if (kind === "phone_shop") return `<svg viewBox="0 0 24 24"><rect x="7" y="2.5" width="10" height="19" rx="2"/><path d="M10 5h4M11 18.5h2"/></svg>`;
    if (kind === "bike") return `<svg viewBox="0 0 24 24"><circle cx="6" cy="17" r="4"/><circle cx="18" cy="17" r="4"/><path d="m6 17 4-8h4l4 8M9 12h7M11 7h3"/></svg>`;
    return `<svg viewBox="0 0 24 24"><path d="m12 3 2.6 5.2 5.7.8-4.1 4 1 5.7-5.2-2.8-5.2 2.8 1-5.7-4.1-4 5.7-.8z"/></svg>`;
  }

  function storeCartIcon() {
    return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 4h2l2.1 10.1a2 2 0 0 0 2 1.6h7.8a2 2 0 0 0 2-1.6L20 7H6M9 20h.01M17 20h.01"/></svg>`;
  }

  function storePrice(product) {
    return Math.max(0, clampInt(state.flowData?.[product.priceKey] ?? product.fallbackPrice));
  }

  function storeOwned(product) {
    return Boolean(product.unique && clampInt(state.flowData?.[product.ownedKey], 0, 1));
  }

  function storeCartEntries() {
    return departmentStoreProducts
      .map((product) => ({ product, quantity: clampInt(state.shopCart[product.id], 0, 99) }))
      .filter((entry) => entry.quantity > 0);
  }

  function storeCartTotal() {
    return storeCartEntries().reduce((total, entry) => total + storePrice(entry.product) * entry.quantity, 0);
  }

  function storeCartPayload() {
    return storeCartEntries().map((entry) => `${entry.product.id}:${entry.quantity}`).join(",");
  }

  function setStoreQuantity(itemId, nextQuantity) {
    const product = departmentStoreProducts.find((item) => item.id === itemId);
    if (!product) return;
    if (storeOwned(product)) {
      state.shopNotice = `Tu possèdes déjà ${product.name.toLowerCase()}.`;
      state.shopNoticeKind = "error";
      renderCapitalFlow();
      return;
    }
    const maximum = product.unique ? 1 : 99;
    const quantity = clampInt(nextQuantity, 0, maximum);
    if (quantity <= 0) delete state.shopCart[itemId];
    else state.shopCart[itemId] = quantity;
    state.shopNotice = "";
    state.shopNoticeKind = "";
    renderCapitalFlow();
  }

  function renderDepartmentStore() {
    const search = String(state.shopSearch || "").trim().toLocaleLowerCase("fr-FR");
    const products = departmentStoreProducts.filter((product) => !search || product.name.toLocaleLowerCase("fr-FR").includes(search));
    const entries = storeCartEntries();
    const itemCount = entries.reduce((total, entry) => total + entry.quantity, 0);
    const total = storeCartTotal();
    const cash = clampInt(state.flowData?.cash ?? state.money);
    const productMarkup = products.length ? products.map((product) => {
      const owned = storeOwned(product);
      const inCart = clampInt(state.shopCart[product.id], 0, 99);
      return `
        <button class="store-product${owned ? " owned" : ""}${inCart ? " selected" : ""}" type="button" data-shop-add="${product.id}" ${owned ? 'aria-disabled="true"' : ""}>
          <span class="store-product-price">${formatNumber(storePrice(product))} FCFA</span>
          <img src="${product.image}" alt="">
          <strong>${product.name}</strong>
          <small>${product.description}</small>
          <em>${owned ? "DÉJÀ POSSÉDÉ" : inCart ? `${inCart} DANS LE PANIER` : "AJOUTER"}</em>
        </button>`;
    }).join("") : `<div class="store-empty-products">Aucun article trouvé.</div>`;

    const cartMarkup = entries.length ? entries.map(({ product, quantity }) => `
      <article class="store-cart-row">
        <img src="${product.image}" alt="">
        <div class="store-cart-copy"><strong>${product.name}</strong><small>${formatNumber(storePrice(product))} FCFA l'unité</small></div>
        <div class="store-quantity" aria-label="Quantité de ${product.name}">
          <button type="button" data-shop-qty="${product.id}" data-delta="-1" ${product.unique ? "disabled" : ""}>−</button>
          <span>Qté : ${quantity}</span>
          <button type="button" data-shop-qty="${product.id}" data-delta="1" ${product.unique || quantity >= 99 ? "disabled" : ""}>+</button>
        </div>
        <b>${formatNumber(storePrice(product) * quantity)} FCFA</b>
        <button class="store-trash" type="button" data-shop-remove="${product.id}" aria-label="Retirer ${product.name}">
          <svg viewBox="0 0 24 24"><path d="M4 7h16M9 7V4h6v3m3 0-1 14H7L6 7m4 4v6m4-6v6"/></svg>
        </button>
      </article>`).join("") : `
      <div class="store-cart-empty">
        <span>${storeCartIcon()}</span>
        <strong>Ton panier est vide</strong>
        <small>Sélectionne les produits que tu veux acheter.</small>
      </div>`;

    return `
      <div class="department-store">
        <section class="store-panel store-catalog-panel">
          <header class="store-panel-heading">
            <span>${storeCartIcon()}</span>
            <div><strong>BOUTIQUE</strong><small>Catalogue des articles disponibles.</small></div>
            <em>EN POCHE : ${formatNumber(cash)} FCFA</em>
          </header>
          <div class="store-catalog-copy"><span>Catalogue des articles disponibles.</span><label><svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg><input id="departmentStoreSearch" type="search" value="${escapeText(state.shopSearch)}" placeholder="Rechercher un article..." autocomplete="off"></label></div>
          <div class="store-product-grid">${productMarkup}</div>
        </section>
        <section class="store-panel store-cart-panel">
          <header class="store-panel-heading">
            <span>${storeCartIcon()}</span>
            <div><strong>MON PANIER</strong><small>Articles sélectionnés.</small></div>
          </header>
          <div class="store-cart-count">${itemCount} ${itemCount === 1 ? "article" : "articles"} dans le panier</div>
          <div class="store-cart-list">${cartMarkup}</div>
          ${state.shopNotice ? `<div class="store-notice ${state.shopNoticeKind === "error" ? "error" : "success"}">${escapeText(state.shopNotice)}</div>` : ""}
          <footer class="store-checkout">
            <div><span>${storeCartIcon()} Total</span><strong>${formatNumber(total)} FCFA</strong></div>
            <button type="button" data-shop-buy ${!entries.length || state.shopPending ? "disabled" : ""}><span>✓</span>${state.shopPending ? "TRAITEMENT..." : "ACHETER"}</button>
          </footer>
        </section>
      </div>`;
  }

  function closeCapitalFlow(notify = false) {
    state.flowView = "";
    state.flowData = {};
    state.shopCart = {};
    state.shopSearch = "";
    state.shopPending = false;
    state.shopNotice = "";
    state.shopNoticeKind = "";
    if (capitalFlowLayer) capitalFlowLayer.hidden = true;
    capitalFlowCard?.classList.remove("store-flow-card");
    setBridgeIdentityOpen(false);
    if (notify) bridge?.systemAction?.("close", "");
  }

  function renderCapitalFlow() {
    if (!capitalFlowLayer || !capitalFlowContent) return;
    const view = state.flowView;
    const data = state.flowData || {};
    if (!view) {
      capitalFlowLayer.hidden = true;
      return;
    }
    capitalFlowCard?.classList.toggle("store-flow-card", view === "phone_shop");

    let body = "";
    if (view === "documents") {
      body = `
        <div class="flow-heading"><span>${flowIcon(view)}</span><div><small>CITOYENNETÉ</small><h2>Établissement de la carte d'identité</h2></div></div>
        <div class="document-sheet">
          <b>Demande d'établissement de document</b>
          <p>Confirme tes informations pour établir ta carte d'identité Crestwood. Le document reste lié définitivement à ton compte.</p>
          <dl><div><dt>Demandeur</dt><dd>${escapeText(normalizeName(state.playerName))}</dd></div><div><dt>Document</dt><dd>Carte d'identité</dd></div><div><dt>Frais</dt><dd>${formatNumber(data.price || 200)} FCFA</dd></div></dl>
        </div>
        <button class="flow-primary" type="button" data-capital-action="documents_issue">Établir le document</button>`;
    } else if (view === "phone_shop") {
      body = renderDepartmentStore();
    } else if (view === "bike") {
      const active = Number(data.active) > 0;
      body = `
        <div class="flow-heading"><span>${flowIcon(view)}</span><div><small>MOBILITÉ</small><h2>${active ? "Retour du Faggio" : "Location de Faggio"}</h2></div></div>
        <div class="bike-service"><div>${flowIcon(view)}</div><h3>${active ? "Faggio en cours d'utilisation" : "Faggio urbain"}</h3><p>${active ? "Tu peux rendre ton Faggio à n'importe quelle station de la ville." : "Le Faggio est récupéré automatiquement après 5 minutes sans conducteur."}</p>${active ? "" : `<strong>${formatNumber(data.price || 50)} FCFA</strong>`}</div>
        <button class="flow-primary" type="button" data-capital-action="${active ? "bike_return" : "bike_rent"}">${active ? "Rendre le Faggio" : "Louer le Faggio"}</button>`;
    } else if (view === "reward") {
      body = `
        <div class="flow-heading reward"><span>${flowIcon(view)}</span><div><small>MISSION ACCOMPLIE</small><h2>Récompenses des Premiers Pas</h2></div></div>
        <p class="reward-copy">Tu as découvert les services essentiels de Crestwood et récupéré ta récompense.</p>
        <div class="reward-list"><strong>+ ${formatNumber(data.money || 7000)} FCFA</strong><strong>+ ${formatNumber(data.xp || 10)} XP</strong></div>
        <button class="flow-primary" type="button" data-capital-flow-close>Continuer</button>`;
    }
    capitalFlowContent.innerHTML = body;
    capitalFlowLayer.hidden = false;
    setBridgeIdentityOpen(true);
  }

  function receiveIdentityEvent(payload) {
    const data = parsePayload(payload);
    const type = String(data.type || "");
    if (type === "vehicle_key") {
      updateHudInfo({
        vehicleKeyVisible: data.visible,
        vehicleKeyLocked: data.locked,
        vehicleKeyVehicle: data.vehicle
      });
      return;
    }
    if (type === "vehicle_lock_sound") return;
    if (type === "steps") {
      state.firstStepsMask = clampInt(data.mask, 0, 31);
      state.firstStepsClaimed = clampInt(data.claimed, 0, 1) === 1;
      state.firstStepsClaimPending = false;
      renderFirstSteps();
      return;
    }
    if (type === "flow") {
      state.flowView = String(data.view || "");
      state.flowData = data;
      if (state.flowView === "phone_shop") {
        state.shopCart = {};
        state.shopSearch = "";
        state.shopPending = false;
        state.shopNotice = "";
        state.shopNoticeKind = "";
      }
      renderCapitalFlow();
      return;
    }
    if (type === "shop_result") {
      state.shopPending = false;
      state.flowData = { ...state.flowData, ...data };
      state.shopNotice = decodePayload(data.message || "");
      state.shopNoticeKind = String(data.status || "") === "ok" ? "success" : "error";
      if (state.shopNoticeKind === "success") state.shopCart = {};
      renderCapitalFlow();
      return;
    }
    if (type === "flow_close") {
      closeCapitalFlow(false);
      return;
    }
    if (type === "missing") {
      hideIdentity();
      closeCapitalFlow(false);
      receiveGameNotification("type=denied;title=DOCUMENTO;message=Emita#20sua#20identidade#20na#20Central#20de#20Documentos;duration=5000");
      return;
    }
    if (type === "data") {
      if (data.name !== undefined) state.playerName = decodePayload(data.name);
      if (data.rg !== undefined) state.account = clampInt(data.rg);
      if (data.sex !== undefined) state.gender = /^f/i.test(String(data.sex)) ? "female" : "male";
      if (data.birth !== undefined) state.registered = decodePayload(data.birth);
      if (data.photo !== undefined) state.identityPhoto = decodePayload(data.photo);
      closeCapitalFlow(false);
      renderIdentity();
      if (clampInt(data.show) > 0) showIdentity(false);
      return;
    }
    if (type === "photo") {
      state.identityPhoto = decodePayload(data.value || "");
      renderIdentity();
      return;
    }
    if (type === "signature_start") {
      identitySignatureChunks = new Array(clampInt(data.chunks, 0, 100));
      if (identitySignatureChunks.length === 0) state.identitySignature = "";
      return;
    }
    if (type === "signature_chunk") {
      const index = clampInt(data.idx, 0, 100);
      if (index < identitySignatureChunks.length) {
        identitySignatureChunks[index] = decodePayload(data.value || "");
      }
      return;
    }
    if (type === "signature_end") {
      state.identitySignature = identitySignatureChunks.join("");
      identitySignatureChunks = [];
      renderIdentity();
    }
  }

  function playPhoneNotificationSound() {
    if (!phoneNotificationSound) return;
    try {
      phoneNotificationSound.currentTime = 0;
      const result = phoneNotificationSound.play();
      if (result && typeof result.catch === "function") result.catch(() => {});
    } catch (_) {}
  }

  function isPhoneNotificationAllowed(app) {
    try {
      const prefs = JSON.parse(localStorage.getItem("crestwoodPhoneNotificationPrefs") || "{}");
      return !prefs || prefs[app] !== false;
    } catch (_) {
      return true;
    }
  }

  function isGameNotificationAllowed() {
    try {
      const prefs = JSON.parse(localStorage.getItem("crestwoodPhoneNotificationPrefs") || "{}");
      return !prefs || prefs.game !== false;
    } catch (_) {
      return true;
    }
  }

  function showPhoneNotification(payload) {
    const data = parsePayload(payload);
    if (!phoneNotificationLayer || data.direction === "out") return;

    if (data.type === "call" && data.status === "incoming") {
      if (!isPhoneNotificationAllowed("whatsapp")) return;
      const callerName = decodePayload(data.name || data.display || "");
      const callerPhone = decodePayload(data.from || data.phone || "");
      const title = callerName || callerPhone || "Appel";
      if (!callerPhone) return;

      window.clearTimeout(phoneNotificationTimer);
      phoneNotificationLayer.innerHTML = `
        <article class="phone-toast phone-call-toast" role="status">
          <span class="phone-toast-icon"><img src="assets/whatsapp.png" alt="" /></span>
          <span class="phone-toast-body">
            <strong class="phone-toast-title">${escapeText(title)}</strong>
            <span class="phone-toast-text">Décrocher ?</span>
          </span>
          <span class="phone-toast-actions">
            <button class="phone-toast-reject" type="button" data-call-action="reject" data-phone="${escapeText(callerPhone)}" aria-label="Refuser"></button>
            <button class="phone-toast-accept" type="button" data-call-action="accept" data-phone="${escapeText(callerPhone)}" aria-label="Répondre"></button>
          </span>
        </article>
      `;
      const toast = phoneNotificationLayer.querySelector(".phone-toast");
      phoneNotificationTimer = window.setTimeout(() => {
        toast?.classList.add("leaving");
        window.setTimeout(() => {
          if (phoneNotificationLayer.contains(toast)) phoneNotificationLayer.innerHTML = "";
        }, 260);
      }, 30000);
      return;
    }

    if (data.type === "bank_txn") {
      if (!isPhoneNotificationAllowed("bank")) return;
      const sender = firstName(decodePayload(data.label || data.name || "Joueur"));
      const amount = formatBankMoney(data.amount || 0);
      const message = previewText(`Tu as reçu ${amount} de ${sender}`);

      window.clearTimeout(phoneNotificationTimer);
      phoneNotificationLayer.innerHTML = `
        <article class="phone-toast" role="status">
          <span class="phone-toast-icon"><img src="assets/bancologo.png" alt="" /></span>
          <span class="phone-toast-body">
            <strong class="phone-toast-title">Virement reçu</strong>
            <span class="phone-toast-text">${escapeText(message)}</span>
          </span>
        </article>
      `;
      const toast = phoneNotificationLayer.querySelector(".phone-toast");
      playPhoneNotificationSound();
      phoneNotificationTimer = window.setTimeout(() => {
        toast?.classList.add("leaving");
        window.setTimeout(() => {
          if (phoneNotificationLayer.contains(toast)) phoneNotificationLayer.innerHTML = "";
        }, 260);
      }, 6000);
      return;
    }

    if (data.type !== "message") return;
    if (!isPhoneNotificationAllowed("whatsapp")) return;

    const senderName = decodePayload(data.name || data.display || "");
    const senderPhone = decodePayload(data.from || data.phone || "");
    let message = decodePayload(data.text || "");
    if (data.kind === "image") message = "a envoyé une photo";
    if (data.kind === "audio") message = "a envoyé un audio";
    if (data.kind === "location") message = "a envoyé une localisation";
    message = previewText(message);
    const title = senderName || senderPhone || "Message";
    if (!message) return;

    window.clearTimeout(phoneNotificationTimer);
    phoneNotificationLayer.innerHTML = `
      <article class="phone-toast" role="status">
        <span class="phone-toast-icon"><img src="assets/whatsapp.png" alt="" /></span>
        <span class="phone-toast-body">
          <strong class="phone-toast-title">${escapeText(title)}</strong>
          <span class="phone-toast-text">${escapeText(message)}</span>
        </span>
      </article>
    `;
    const toast = phoneNotificationLayer.querySelector(".phone-toast");
    phoneNotificationTimer = window.setTimeout(() => {
      toast?.classList.add("leaving");
      window.setTimeout(() => {
        if (phoneNotificationLayer.contains(toast)) phoneNotificationLayer.innerHTML = "";
      }, 260);
    }, 6000);
  }

  function normalizeGameNotificationType(type) {
    if (type === "success" || type === "denied" || type === "warning" || type === "system" || type === "admin" || type === "restart") return type;
    if (type === "ok") return "success";
    if (type === "error" || type === "fail" || type === "negado") return "denied";
    if (type === "info") return "system";
    return "system";
  }

  function gameNotificationTitle(type, title) {
    const custom = decodePayload(title || "").trim();
    if (custom) return custom;
    if (type === "success") return "Succès";
    if (type === "denied") return "Refusé";
    if (type === "admin") return "Assistance";
    if (type === "restart") return "Redémarrage du serveur";
    if (type === "warning") return "Avertissement";
    return "Système";
  }

  function gameNotificationIcon(type) {
    if (type === "restart") {
      return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M13 2v9h7L11 22v-9H4L13 2zm-2 6.2L8.1 11H13v4.8l2.9-2.8H11V8.2z"/></svg>`;
    }
    if (type === "admin") {
      return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3a8 8 0 0 0-8 8v6a3 3 0 0 0 3 3h2v-7H6v-2a6 6 0 0 1 12 0v2h-3v7h2.2A4.2 4.2 0 0 1 13 23h-2v-2h2a2.2 2.2 0 0 0 2-1h2a3 3 0 0 0 3-3v-6a8 8 0 0 0-8-8z"/></svg>`;
    }
    if (type === "system") {
      return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.5a9.5 9.5 0 1 0 0 19 9.5 9.5 0 0 0 0-19zm0 4.1a1.35 1.35 0 1 1 0 2.7 1.35 1.35 0 0 1 0-2.7zm-1.25 4.2h2.5v6.6h-2.5v-6.6z"/></svg>`;
    }
    if (type === "success") {
      return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.1 16.7 4.9 12.5l1.7-1.7 2.5 2.5 8.4-8.4 1.7 1.7-10.1 10.1z"/></svg>`;
    }
    if (type === "warning") {
      return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3.4 21.2 20H2.8L12 3.4zm-1.1 5.5.3 6.1h1.6l.3-6.1h-2.2zM12 18.2a1.2 1.2 0 1 0 0-2.4 1.2 1.2 0 0 0 0 2.4z"/></svg>`;
    }
    return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 10.3 4.2-4.2 1.7 1.7-4.2 4.2 4.2 4.2-1.7 1.7-4.2-4.2-4.2 4.2-1.7-1.7 4.2-4.2-4.2-4.2 1.7-1.7 4.2 4.2z"/></svg>`;
  }

  function renderGameNotifications() {
    if (!gameNotificationStack) return;
    gameNotificationStack.innerHTML = gameNotifications.map((item) => `
      <article class="game-notify game-notify-${item.type}" data-game-notify="${item.id}" role="status" style="--notify-duration:${item.duration}ms">
        <span class="game-notify-icon">${gameNotificationIcon(item.type)}</span>
        <span class="game-notify-body">
          <strong>${escapeText(item.title)}</strong>
          <span class="game-notify-line" aria-hidden="true"></span>
          <small>${escapeText(item.message)}</small>
        </span>
        <svg class="game-notify-timer" viewBox="0 0 36 36" aria-hidden="true">
          <circle class="timer-track" cx="18" cy="18" r="14"></circle>
          <circle class="timer-progress" cx="18" cy="18" r="14"></circle>
        </svg>
        ${item.type === "restart" ? `<button class="game-notify-close" type="button" data-game-notify-close="${item.id}" aria-label="Fermer l'avis">×</button>` : ""}
      </article>
    `).join("");
  }

  function removeGameNotification(id) {
    const index = gameNotifications.findIndex((item) => item.id === id);
    if (index < 0) return;
    const card = gameNotificationStack?.querySelector(`[data-game-notify="${id}"]`);
    card?.classList.add("leaving");
    window.setTimeout(() => {
      const nextIndex = gameNotifications.findIndex((item) => item.id === id);
      if (nextIndex >= 0) gameNotifications.splice(nextIndex, 1);
      renderGameNotifications();
    }, 220);
  }

  function receiveGameNotification(payload) {
    if (!isGameNotificationAllowed() || !gameNotificationStack) return;
    const data = parsePayload(payload);
    const type = normalizeGameNotificationType(data.type || data.status);
    const message = previewText(decodePayload(data.message || data.text || data.msg || "").replace(/\s+/g, " ").trim());
    if (!message) return;

    const id = ++gameNotificationId;
    const duration = clampInt(data.duration, 2500, 12000) || 6000;
    if (type === "restart") {
      for (let index = gameNotifications.length - 1; index >= 0; index -= 1) {
        if (gameNotifications[index].type === "restart") gameNotifications.splice(index, 1);
      }
    }
    gameNotifications.unshift({
      id,
      type,
      title: gameNotificationTitle(type, data.title),
      message,
      duration
    });
    while (gameNotifications.length > 1) gameNotifications.pop();
    renderGameNotifications();
    window.setTimeout(() => removeGameNotification(id), duration);
  }

  gameNotificationStack?.addEventListener("click", (event) => {
    const close = event.target.closest?.("[data-game-notify-close]");
    if (!close) return;
    removeGameNotification(Number(close.dataset.gameNotifyClose));
  });

  function showSpeedFineNotice(payload) {
    if (!speedFineBanner) return;
    const data = parsePayload(payload);
    const speed = clampInt(data.speed || data.velocity || data.velocidade, 0, 999);
    const duration = clampInt(data.duration, 1800, 8000) || 3500;
    if (speedFineSpeed) speedFineSpeed.textContent = String(speed);

    window.clearTimeout(speedFineTimer);
    speedFineBanner.hidden = false;
    speedFineBanner.classList.remove("leaving");
    speedFineBanner.classList.add("visible");

    speedFineTimer = window.setTimeout(() => {
      speedFineBanner.classList.add("leaving");
      speedFineBanner.classList.remove("visible");
      window.setTimeout(() => {
        if (speedFineBanner.classList.contains("leaving")) {
          speedFineBanner.hidden = true;
          speedFineBanner.classList.remove("leaving");
        }
      }, 220);
    }, duration);
  }

  function stopRadarBotCountdown() {
    window.clearInterval(radarBotCountdownTimer);
    radarBotCountdownTimer = 0;
    radarBotSpeedMps = 0;
  }

  function renderRadarBotDistance() {
    if (!radarBotDistance) return;
    radarBotDistance.textContent = String(Math.max(0, Math.ceil(radarBotEstimate)));
  }

  function startRadarBotCountdown(distance, speedKmh) {
    radarBotEstimate = Math.max(0, Number(distance) || 0);
    radarBotLastStamp = performance.now();
    radarBotSpeedMps = Math.max(0, (Number(speedKmh) || 0) / 3.6);
    renderRadarBotDistance();
    window.clearInterval(radarBotCountdownTimer);
    if (radarBotSpeedMps <= 0.1) return;
    radarBotCountdownTimer = window.setInterval(() => {
      const now = performance.now();
      const elapsed = Math.max(0, (now - radarBotLastStamp) / 1000);
      radarBotLastStamp = now;
      radarBotEstimate = Math.max(0, radarBotEstimate - (radarBotSpeedMps * elapsed));
      renderRadarBotDistance();
    }, 45);
  }

  function showRadarBotAlert(payload) {
    if (!radarBotAlert) return;
    const data = parsePayload(payload);
    const shouldShow = String(data.show ?? "1") !== "0";
    window.clearTimeout(radarBotTimer);

    if (!shouldShow) {
      radarBotLiveVisible = false;
      stopRadarBotCountdown();
      if (state.hudEditing) {
        radarBotAlert.classList.add("hud-edit-preview", "visible");
        radarBotAlert.classList.remove("danger");
        radarBotAlert.hidden = false;
        return;
      }
      radarBotAlert.classList.remove("visible", "danger");
      radarBotAlert.hidden = true;
      return;
    }

    const limit = clampInt(data.limit, 1, 999) || 80;
    const distance = clampInt(data.distance || data.dist, 0, 999) || 0;
    const speed = clampInt(data.speed, 0, 999) || 0;
    const danger = String(data.danger || "0") === "1";
    radarBotLiveVisible = true;
    if (radarBotLimit) radarBotLimit.textContent = String(limit);
    startRadarBotCountdown(distance, speed);

    radarBotAlert.hidden = false;
    radarBotAlert.classList.remove("hud-edit-preview");
    radarBotAlert.classList.toggle("danger", danger);
    radarBotAlert.classList.add("visible");
    radarBotTimer = window.setTimeout(() => {
      radarBotLiveVisible = false;
      stopRadarBotCountdown();
      if (state.hudEditing) {
        radarBotAlert.classList.add("hud-edit-preview", "visible");
        radarBotAlert.classList.remove("danger");
        radarBotAlert.hidden = false;
        return;
      }
      radarBotAlert.classList.remove("visible", "danger");
      radarBotAlert.hidden = true;
    }, 900);
  }

  function updateHudInfo(payload) {
    const data = parsePayload(payload);
    let statusDirty = false;
    let infoDirty = false;
    let radioDirty = false;
    let engineDirty = false;
    let vehicleKeyDirty = false;
    let drivingDirty = false;

    if (data.health !== undefined) { state.health = clampPercent(data.health); statusDirty = true; }
    if (data.hunger !== undefined) { state.hunger = clampPercent(data.hunger); statusDirty = true; }
    if (data.thirst !== undefined) { state.thirst = clampPercent(data.thirst); statusDirty = true; }
    if (data.energy !== undefined || data.oxygen !== undefined) {
      state.energy = clampPercent(data.energy ?? data.oxygen);
      statusDirty = true;
    }
    if (data.armour !== undefined || data.armor !== undefined) {
      state.armour = clampPercent(data.armour ?? data.armor);
      statusDirty = true;
    }
    if (data.inVehicle !== undefined || data.vehicle !== undefined) {
      state.inVehicle = clampInt(data.inVehicle ?? data.vehicle) > 0;
      drivingDirty = true;
    }
    if (data.speed !== undefined || data.speedKmh !== undefined) {
      state.speed = clampInt(data.speed ?? data.speedKmh, 0, 999);
      drivingDirty = true;
    }
    if (data.vehicleHealth !== undefined || data.carHealth !== undefined) {
      state.vehicleHealth = clampPercent(data.vehicleHealth ?? data.carHealth);
      drivingDirty = true;
    }
    if (data.fuel !== undefined || data.gas !== undefined) {
      state.fuel = clampPercent(data.fuel ?? data.gas);
      drivingDirty = true;
    }
    if (data.time !== undefined || data.hour !== undefined || data.minute !== undefined) tickBrasiliaClock(true);
    if (data.coin !== undefined || data.diamonds !== undefined || data.diamond !== undefined || data.diamante !== undefined) {
      state.diamonds = clampInt(data.coin ?? data.diamonds ?? data.diamond ?? data.diamante);
      infoDirty = true;
    }
    if (data.account !== undefined) {
      const account = clampInt(data.account);
      if (account > 0) { state.account = account; infoDirty = true; }
    }
    if (data.accountId !== undefined) {
      const account = clampInt(data.accountId);
      if (account > 0) { state.account = account; infoDirty = true; }
    }
    if (data.phone !== undefined) {
      const nextPhone = String(data.phone || "");
      if (isValidPhone(nextPhone)) { state.phone = nextPhone; infoDirty = true; }
    }
    if (data.name !== undefined) { state.playerName = decodePayload(data.name); infoDirty = true; }
    if (data.playerName !== undefined) { state.playerName = decodePayload(data.playerName); infoDirty = true; }
    if (data.gender !== undefined) state.gender = decodePayload(data.gender);
    if (data.registered !== undefined) { state.registered = decodePayload(data.registered); infoDirty = true; }
    if (data.created !== undefined) { state.registered = decodePayload(data.created); infoDirty = true; }
    if (data.createdAt !== undefined) { state.registered = decodePayload(data.createdAt); infoDirty = true; }
    if (data.money !== undefined || data.cash !== undefined) {
      state.money = clampInt(data.money ?? data.cash);
      infoDirty = true;
    }
    if (data.bank !== undefined || data.bankBalance !== undefined) {
      state.bank = clampInt(data.bank ?? data.bankBalance);
      infoDirty = true;
    }
    if (data.location !== undefined || data.zone !== undefined) {
      state.location = decodePayload(data.location ?? data.zone).replace(/_/g, " ") || "Los Santos";
      infoDirty = true;
    }
    if (data.wanted !== undefined) { state.wanted = clampInt(data.wanted, 0, 6); infoDirty = true; }
    if (data.weaponid !== undefined) { state.weaponid = clampInt(data.weaponid, 0, 99); infoDirty = true; }
    if (data.ammoClip !== undefined) { state.ammoClip = clampInt(data.ammoClip); infoDirty = true; }
    else if (data.ammo !== undefined) { state.ammoClip = clampInt(data.ammo); infoDirty = true; }
    if (data.ammoTotal !== undefined) { state.ammoTotal = clampInt(data.ammoTotal); infoDirty = true; }
    else if (data.ammoinclip !== undefined) { state.ammoTotal = clampInt(data.ammoinclip); infoDirty = true; }
    if (data.radio !== undefined) { state.radioVisible = clampInt(data.radio) > 0; radioDirty = true; }
    if (data.freq !== undefined || data.radioFrequency !== undefined) {
      state.radioFrequency = clampInt(data.freq ?? data.radioFrequency, 0, 300);
      radioDirty = true;
    }
    if (data.engineVisible !== undefined || data.motorVisible !== undefined) {
      state.engineVisible = clampInt(data.engineVisible ?? data.motorVisible) > 0;
      engineDirty = true;
    }
    if (data.engine !== undefined || data.motor !== undefined) {
      state.engineOn = clampInt(data.engine ?? data.motor) > 0;
      engineDirty = true;
    }
    if (data.vehicleKeyVisible !== undefined) {
      state.vehicleKeyVisible = clampInt(data.vehicleKeyVisible) > 0;
      vehicleKeyDirty = true;
    }
    if (data.vehicleKeyLocked !== undefined) {
      state.vehicleKeyLocked = clampInt(data.vehicleKeyLocked) > 0;
      vehicleKeyDirty = true;
    }
    if (data.vehicleKeyVehicle !== undefined) {
      state.vehicleKeyVehicle = clampInt(data.vehicleKeyVehicle);
      vehicleKeyDirty = true;
    }
    if (data.edit !== undefined && clampInt(data.edit) > 0) setHudEditor(true);

    if (statusDirty) {
      setStatus("health", state.health);
      setStatus("hunger", state.hunger);
      setStatus("thirst", state.thirst);
      setStatus("energy", state.energy);
      setStatus("armour", state.armour);
    }
    if (drivingDirty || statusDirty) renderDrivingHud();
    if (infoDirty) renderInfoPanel();
    if (radioDirty) renderRadio();
    if (engineDirty) renderEngine();
    if (vehicleKeyDirty) renderVehicleLock();
  }

  function notifyNativeReady() {
    try {
      bridge?.ready?.();
    } catch (_) {}
  }

  function sendMessage(message) {
    const clean = String(message || "").trim();
    if (!clean) return;
    bridge?.sendChatMessage?.(clean);
  }

  function holdHudTouch(event) {
    if (state.hudEditing) return;
    event.stopPropagation();
  }

  [cornerAction, chatAction, chatComposer].forEach((element) => {
    element?.addEventListener("pointerdown", holdHudTouch);
    element?.addEventListener("touchstart", holdHudTouch, { passive: false });
    element?.addEventListener("mousedown", holdHudTouch);
  });

  cornerAction?.addEventListener("click", (event) => {
    event.preventDefault();
    event.stopPropagation();
    if (state.hudEditing) {
      selectHudItem(cornerAction.closest("[data-hud-key]") || cornerAction);
      return;
    }
    const willOpen = !state.menuOpen;
    if (willOpen) setChatComposer(false);
    setRadialMenu(willOpen);
  });

  chatAction?.addEventListener("click", (event) => {
    event.preventDefault();
    event.stopPropagation();
    if (state.hudEditing) {
      selectHudItem(chatAction.closest("[data-hud-key]") || chatAction);
      return;
    }
    setChatComposer(!state.chatOpen);
  });

  document.querySelectorAll("[data-weapon-action]").forEach((button) => {
    button.addEventListener("click", () => {
      bridge?.weaponAction?.(button.dataset.weaponAction || "");
    });
  });

  chatComposer?.addEventListener("submit", (event) => {
    event.preventDefault();
    sendMessage(chatInput?.value);
    if (chatInput) chatInput.value = "";
    setChatComposer(false);
  });

  chatInput?.addEventListener("touchstart", (event) => {
    event.stopPropagation();
  });

  phoneNotificationLayer?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-call-action]");
    if (!button) return;
    const phone = button.dataset.phone || "";
    const action = button.dataset.callAction === "accept" ? "accept" : "reject";
    bridge?.phoneCallAction?.(action, phone);
    const toast = button.closest(".phone-toast");
    toast?.classList.add("leaving");
    window.setTimeout(() => {
      if (toast && phoneNotificationLayer.contains(toast)) phoneNotificationLayer.innerHTML = "";
    }, 220);
  });

  chatInput?.addEventListener("pointerdown", (event) => {
    event.stopPropagation();
    window.setTimeout(focusChatInput, 0);
  });

  chatInput?.addEventListener("compositionend", () => {
    window.setTimeout(focusChatInput, 0);
  });

  chatInput?.addEventListener("input", () => {
    if (state.chatOpen && document.activeElement !== chatInput) {
      window.setTimeout(focusChatInput, 0);
    }
  });

  radioButton?.addEventListener("pointerdown", (event) => {
    event.preventDefault();
    startRadioTalk();
  });
  radioButton?.addEventListener("touchstart", (event) => {
    event.preventDefault();
    startRadioTalk();
  }, { passive: false });
  radioButton?.addEventListener("mousedown", (event) => {
    event.preventDefault();
    startRadioTalk();
  });
  ["pointerup", "pointercancel", "pointerleave"].forEach((type) => {
    radioButton?.addEventListener(type, stopRadioTalk);
  });
  ["touchend", "touchcancel", "mouseup"].forEach((type) => {
    radioButton?.addEventListener(type, stopRadioTalk);
  });
  document.addEventListener("pointerup", stopRadioTalk);
  document.addEventListener("touchend", stopRadioTalk);
  document.addEventListener("mouseup", stopRadioTalk);
  radioFrequencyConfirm?.addEventListener("click", saveRadioFrequency);
  radioFrequencyCancel?.addEventListener("click", closeRadioFrequency);
  radioFrequencyInput?.addEventListener("keydown", (event) => {
    if (event.key === "Enter") saveRadioFrequency();
  });
  radioKeypad?.addEventListener("click", (event) => {
    const key = event.target?.dataset?.key;
    if (!key) return;
    updateRadioFrequencyInput(key);
  });
  engineButton?.addEventListener("click", (event) => {
    event.preventDefault();
    sendEngineAction("toggle");
  });
  engineButton?.addEventListener("pointerdown", (event) => {
    if (state.hudEditing) return;
    event.preventDefault();
  });
  vehicleLockButton?.addEventListener("click", (event) => {
    event.preventDefault();
    event.stopPropagation();
    if (state.hudEditing) {
      selectHudItem(vehicleLockButton);
      return;
    }
    if (!state.vehicleKeyVisible) return;
    bridge?.vehicleLockAction?.();
  });
  vehicleLockButton?.addEventListener("pointerdown", (event) => {
    if (state.hudEditing) return;
    event.preventDefault();
  });
  animationsButton?.addEventListener("click", (event) => {
    event.preventDefault();
    event.stopPropagation();
    if (state.hudEditing) {
      selectHudItem(animationsButton);
      return;
    }
    bridge?.openAnimations?.();
  });
  animationsButton?.addEventListener("pointerdown", (event) => {
    if (state.hudEditing) return;
    event.preventDefault();
  });
  hudEditorSave?.addEventListener("click", () => {
    saveHudLayout();
    setHudEditor(false);
  });
  hudEditorReset?.addEventListener("click", resetHudLayout);
  hudScaleInput?.addEventListener("input", () => {
    if (!activeHudItem) return;
    hudDefaultResetPending = false;
    setHudItemScale(activeHudItem, Number(hudScaleInput.value || 100) / 100);
    syncTouchBounds();
  });
  movableItems.forEach((item) => {
    item.addEventListener("click", () => {
      if (state.hudEditing) selectHudItem(item);
    });
    item.addEventListener("pointerdown", (event) => beginHudDrag(event, item), true);
    item.addEventListener("pointermove", moveHudDrag);
    item.addEventListener("pointerup", endHudDrag);
    item.addEventListener("pointercancel", endHudDrag);
  });

  radialMenu?.addEventListener("click", (event) => {
    if (event.target === radialMenu) setRadialMenu(false);
  });

  firstStepsPanel?.addEventListener("click", (event) => {
    if (event.target.closest?.("#firstStepsToggle")) {
      state.firstStepsExpanded = !state.firstStepsExpanded;
      renderFirstSteps();
      return;
    }

    const target = event.target.closest?.("[data-first-step]");
    if (target && !target.classList.contains("done")) {
      state.selectedFirstStep = Number(target.dataset.firstStep) || 0;
      renderFirstSteps();
      return;
    }

    if (event.target.closest?.("#firstStepsStart") && state.firstStepsMask === 31 && !state.firstStepsClaimed) {
      state.firstStepsClaimPending = true;
      bridge?.systemAction?.("steps_claim", "");
      renderFirstSteps();
      window.setTimeout(() => {
        if (!state.firstStepsClaimed) {
          state.firstStepsClaimPending = false;
          renderFirstSteps();
        }
      }, 2500);
      return;
    }

    if (event.target.closest?.("#firstStepsStart") && state.selectedFirstStep) {
      state.activeFirstStep = state.selectedFirstStep;
      bridge?.systemAction?.("steps_target", `step=${state.selectedFirstStep}`);
      renderFirstSteps();
    }
  });

  capitalFlowLayer?.addEventListener("click", (event) => {
    const addProduct = event.target.closest?.("[data-shop-add]");
    if (addProduct) {
      const product = departmentStoreProducts.find((item) => item.id === addProduct.dataset.shopAdd);
      if (!product) return;
      if (storeOwned(product)) {
        state.shopNotice = `Tu possèdes déjà ${product.name.toLowerCase()}.`;
        state.shopNoticeKind = "error";
        renderCapitalFlow();
        return;
      }
      const current = clampInt(state.shopCart[product.id], 0, 99);
      if (product.unique && current >= 1) {
        state.shopNotice = `${product.name} n'autorise qu'une seule unité.`;
        state.shopNoticeKind = "error";
        renderCapitalFlow();
        return;
      }
      setStoreQuantity(product.id, current + 1);
      return;
    }
    const quantityButton = event.target.closest?.("[data-shop-qty]");
    if (quantityButton) {
      const itemId = quantityButton.dataset.shopQty;
      const current = clampInt(state.shopCart[itemId], 0, 99);
      setStoreQuantity(itemId, current + Number(quantityButton.dataset.delta || 0));
      return;
    }
    const removeProduct = event.target.closest?.("[data-shop-remove]");
    if (removeProduct) {
      setStoreQuantity(removeProduct.dataset.shopRemove, 0);
      return;
    }
    if (event.target.closest?.("[data-shop-buy]")) {
      const payload = storeCartPayload();
      if (!payload || state.shopPending) return;
      state.shopPending = true;
      state.shopNotice = "";
      state.shopNoticeKind = "";
      renderCapitalFlow();
      bridge?.systemAction?.("shop_buy", payload);
      window.setTimeout(() => {
        if (!state.shopPending) return;
        state.shopPending = false;
        state.shopNotice = "La boutique met trop de temps à répondre. Réessaie.";
        state.shopNoticeKind = "error";
        renderCapitalFlow();
      }, 5000);
      return;
    }
    const confirm = event.target.closest?.("[data-capital-confirm]");
    if (confirm) {
      state.flowView = confirm.dataset.capitalConfirm || state.flowView;
      renderCapitalFlow();
      return;
    }
    const back = event.target.closest?.("[data-capital-back]");
    if (back) {
      state.flowView = back.dataset.capitalBack || "phone_shop";
      renderCapitalFlow();
      return;
    }
    const action = event.target.closest?.("[data-capital-action]");
    if (action) {
      action.disabled = true;
      bridge?.systemAction?.(action.dataset.capitalAction || "", "");
      return;
    }
    if (event.target.closest?.("[data-capital-flow-close]")) closeCapitalFlow(true);
  });

  capitalFlowLayer?.addEventListener("input", (event) => {
    if (event.target?.id !== "departmentStoreSearch") return;
    state.shopSearch = event.target.value || "";
    const cursor = event.target.selectionStart;
    renderCapitalFlow();
    const searchInput = document.querySelector("#departmentStoreSearch");
    if (searchInput) {
      searchInput.focus({ preventScroll: true });
      try { searchInput.setSelectionRange(cursor, cursor); } catch (_) {}
    }
  });

  document.querySelectorAll(".wheel-slot").forEach((slot) => {
    slot.addEventListener("click", () => {
      const action = slot.dataset.slot || "";
      if (action === "id") {
        showIdentity(true);
      } else {
        bridge?.menuAction?.(action);
      }
      setRadialMenu(false);
      window.setTimeout(() => setRadialMenu(false), 90);
    });
  });

  document.querySelectorAll("[data-id-close]").forEach((item) => {
    item.addEventListener("click", hideIdentity);
  });

  idPhotoButton?.addEventListener("click", openIdCamera);
  idCameraCancel?.addEventListener("click", () => closeIdCamera(true));
  idCameraShot?.addEventListener("click", captureIdPhoto);
  idCameraFrame?.addEventListener("pointerdown", (event) => {
    if (!state.idCameraOpen) return;
    idCameraDragging = true;
    idCameraLastX = event.clientX;
    idCameraLastY = event.clientY;
    idCameraFrame.setPointerCapture?.(event.pointerId);
    event.preventDefault();
  });
  idCameraFrame?.addEventListener("pointermove", (event) => {
    if (!idCameraDragging) return;
    const dx = event.clientX - idCameraLastX;
    const dy = event.clientY - idCameraLastY;
    idCameraLastX = event.clientX;
    idCameraLastY = event.clientY;
    adjustIdCamera(dx, dy);
    event.preventDefault();
  });
  ["pointerup", "pointercancel", "pointerleave"].forEach((type) => {
    idCameraFrame?.addEventListener(type, (event) => {
      if (!idCameraDragging) return;
      idCameraDragging = false;
      idCameraFrame.releasePointerCapture?.(event.pointerId);
    });
  });
  idSignButton?.addEventListener("click", openSignature);
  idSignCancel?.addEventListener("click", closeSignature);
  idSignClear?.addEventListener("click", setupSignatureCanvas);
  idSignSave?.addEventListener("click", saveSignature);

  idSignCanvas?.addEventListener("pointerdown", (event) => {
    if (!signatureCanvasReady) setupSignatureCanvas();
    const ctx = idSignCanvas.getContext("2d");
    if (!ctx) return;
    signatureDrawing = true;
    signatureHasInk = true;
    idSignCanvas.setPointerCapture?.(event.pointerId);
    const point = pointerOnCanvas(event);
    activeSignatureStroke = [point];
    signatureStrokes.push(activeSignatureStroke);
    ctx.beginPath();
    ctx.moveTo(point.x, point.y);
    event.preventDefault();
  });

  idSignCanvas?.addEventListener("pointermove", (event) => {
    if (!signatureDrawing) return;
    const ctx = idSignCanvas.getContext("2d");
    if (!ctx) return;
    const point = pointerOnCanvas(event);
    activeSignatureStroke?.push(point);
    ctx.lineTo(point.x, point.y);
    ctx.stroke();
    event.preventDefault();
  });

  ["pointerup", "pointercancel", "pointerleave"].forEach((type) => {
    idSignCanvas?.addEventListener(type, (event) => {
      if (!signatureDrawing) return;
      signatureDrawing = false;
      activeSignatureStroke = null;
      idSignCanvas.releasePointerCapture?.(event.pointerId);
    });
  });

  document.addEventListener("pointerdown", (event) => {
    if (state.chatOpen) {
      if (chatComposer?.contains(event.target) || chatAction?.contains(event.target)) return;
      setChatComposer(false);
      return;
    }

    if (state.menuOpen) {
      const radialWheel = radialMenu?.querySelector(".radial-wheel");
      if (radialWheel?.contains(event.target) || cornerAction?.contains(event.target)) return;
      setRadialMenu(false);
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      if (!radioFrequencyLayer?.hidden) {
        closeRadioFrequency();
        return;
      }
      if (state.hudEditing) {
        applyHudLayout(true);
        setHudEditor(false);
        return;
      }
      if (state.idSigning) {
        closeSignature();
        return;
      }
      if (state.idCameraOpen) {
        closeIdCamera(true);
        return;
      }
      if (state.idOpen) {
        hideIdentity();
        return;
      }
      setChatComposer(false);
      setRadialMenu(false);
    }
  });

  window.HudApp = {
    openFromNative(payload) {
      document.body.classList.add("hud-ready");
      root?.classList.add("ready");
      updateHudInfo(payload);
      scheduleHudSync();
    },
    closeFromNative() {
      closeInteractive();
      root?.classList.remove("ready");
      document.body.classList.remove("hud-ready");
    },
    closeInteractiveFromNative: closeInteractive,
    updateHudInfo,
    setStatus,
    focusChatInput,
    showChat() {
      setChatComposer(true);
    },
    clearChat() {},
    addChatMessage() {},
    showPhoneNotification,
    receiveGameNotification,
    showSpeedFineNotice,
    showRadarBotAlert,
    rgCameraNativeStatus,
    receiveIdentityEvent,
    radioNativeDown: startRadioTalk,
    radioNativeUp: stopRadioTalk,
    updateEngine(payload) {
      updateHudInfo(payload);
    },
    openHudEditorFromNative() {
      document.body.classList.add("hud-ready");
      root?.classList.add("ready");
      setHudEditor(true);
      scheduleHudSync();
    },
    refreshFromNative() {
      document.body.classList.add("hud-ready");
      root?.classList.add("ready");
      scheduleHudSync();
    }
  };

  // Compatibilité avec l'ancien pont natif (HudBridge.setPlayerState / setVisible)
  window.HudBridge = {
    setPlayerState(next) {
      if (typeof next === "string") {
        try { next = JSON.parse(next); } catch (_) { return; }
      }
      if (!next || typeof next !== "object") return;
      const mapped = {};
      if (next.health !== undefined) mapped.health = next.dead ? 0 : next.health;
      if (next.armour !== undefined) mapped.armour = next.armour;
      if (next.thirst !== undefined) mapped.thirst = next.thirst;
      if (next.hunger !== undefined) mapped.hunger = next.hunger;
      if (next.energy !== undefined) mapped.energy = next.exhausted ? 0 : next.energy;
      if (next.inVehicle !== undefined) mapped.inVehicle = next.inVehicle ? 1 : 0;
      if (next.speed !== undefined) mapped.speed = next.speed;
      if (next.vehHealth !== undefined) mapped.vehicleHealth = next.vehHealth;
      if (next.fuel !== undefined) mapped.fuel = next.fuel;
      updateHudInfo(mapped);
    },
    setVisible(visible) { if (root) root.style.display = visible === false ? "none" : ""; },
    setWheelOpen() {},
    setVehicleMode() {},
    reset() {}
  };

  startBrasiliaClock();
  window.addEventListener("focus", () => tickBrasiliaClock(true));
  document.addEventListener("visibilitychange", () => tickBrasiliaClock(true));
  window.addEventListener("resize", () => { reframeHud(); syncTouchBounds(); });
  window.addEventListener("orientationchange", () => window.setTimeout(() => { reframeHud(); syncTouchBounds(); }, 200));
  scheduleHudSync();
  updateHudInfo(state);
  tickBrasiliaClock(true);
  updateBridgeInteraction();
  notifyNativeReady();

  if (new URLSearchParams(window.location.search).get("camera") === "phone") {
    document.body.classList.add("hud-ready");
    root?.classList.add("ready");
    openPhoneCamera();
  }

  const previewMode = new URLSearchParams(window.location.search).get("preview");
  if (previewMode) {
    window.setTimeout(() => {
      const vehiclePreview = previewMode === "vehicle"
        ? ";energy=71;armour=46;inVehicle=1;speed=127;vehicleHealth=63;fuel=100"
        : ";energy=84;armour=46;inVehicle=0;speed=0;vehicleHealth=100;fuel=100";
      window.HudApp.openFromNative(`health=92;hunger=78;thirst=64;money=2485;coin=0;accountId=316;name=Trevor_Vivo;location=Los_Santos;hour=16;minute=4${vehiclePreview}`);
      if (previewMode === "vehicle" || previewMode === "status") return;
      window.HudApp.receiveIdentityEvent(`type=steps;mask=${previewMode === "steps" ? 1 : 31};done=0;claimed=${previewMode === "claimed" ? 1 : 0};phone=0;bank=0;document=0`);
      if (previewMode === "steps" || previewMode === "claim") {
        state.firstStepsExpanded = true;
        renderFirstSteps();
      }
      const notificationPreviews = {
        "notify-success": "type=success;title=Sedex;message=Voce#20colocou#20uma#20caixa#20no#20bau.#20Faltam#209.;duration=9000",
        "notify-denied": "type=denied;title=Negado;message=Voce#20nao#20tem#20permissao.;duration=9000",
        "notify-warning": "type=warning;title=Aviso;message=Veiculos#20sem#20motorista#20serao#20respawnados#20em#201#20minuto.;duration=9000",
        "notify-system": "type=system;title=Sistema;message=Siga#20a#20missao#20de#20Primeiros#20Passos.;duration=9000",
        "notify-admin": "type=admin;title=Atendimento;message=Um#20novo#20atendimento#20foi#20solicitado.;duration=9000"
      };
      window.HudApp.receiveGameNotification(notificationPreviews[previewMode] || notificationPreviews["notify-system"]);
      if (previewMode === "documents") window.HudApp.receiveIdentityEvent("type=flow;view=documents;price=200");
      if (previewMode === "phone") window.HudApp.receiveIdentityEvent("type=flow;view=phone_shop;phoneprice=500;radioprice=500;burgerprice=40;waterprice=20;ownphone=0;ownradio=0;cash=1500");
      if (previewMode === "bike") window.HudApp.receiveIdentityEvent("type=flow;view=bike;price=50;active=0");
      if (previewMode === "reward") window.HudApp.receiveIdentityEvent("type=flow;view=reward;money=7000;xp=10");
    }, 60);
  }
})();
