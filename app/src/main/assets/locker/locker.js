/* Vestiaire de faction Crestwood (police).
   Serveur -> page : LockerApp.openFromNative(json) / LockerApp.onUpdateFromNative(json)
   Page -> serveur : SampLocker.action(type, {arg}) puis /lockeract (voir SAMP.java)
   Le serveur (basicgm.pwn, module FACTIONS + VESTIAIRES) re-verifie la faction et la distance
   a chaque action : cette page ne fait que l'affichage. */
(function () {
  "use strict";
  const bridge = window.SampLocker || null;
  const state = { fac: 0, facName: "Police", rank: 1, rankName: "", uniform: 0, female: 0, belt: 1, vest: 1, hat: 0 };

  const PIECES = [
    { k: "belt", label: "Ceinture", sub: "Équipement" },
    { k: "vest", label: "Gilet", sub: "Pare-balles" },
    { k: "hat",  label: "Casquette", sub: "Tenue officier" }
  ];
  const PRESETS = [
    ["Patrouille",      { belt: 1, vest: 1, hat: 0 }],
    ["Cérémonie",       { belt: 1, vest: 0, hat: 1 }],
    ["Tenue complète",  { belt: 1, vest: 1, hat: 1 }],
    ["Sobre",           { belt: 0, vest: 0, hat: 0 }]
  ];

  const $ = (id) => document.getElementById(id);
  function el(tag, cls, text) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined) n.textContent = text;
    return n;
  }
  function parse(raw) {
    try { return JSON.parse(raw || "{}") || {}; } catch (_) { return {}; }
  }

  function send(type, arg) {
    const payload = JSON.stringify({ arg: arg == null ? "" : String(arg) });
    if (bridge && typeof bridge.action === "function") {
      try { bridge.action(type, payload); } catch (_) {}
    } else {
      console.log("[locker] action", type, payload);
    }
  }

  let toastTimer = null;
  function toast(msg, ok) {
    if (!msg) return;
    const t = $("lkToast");
    t.textContent = msg;
    t.classList.toggle("err", !ok);
    t.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add("hidden"), 3200);
  }

  function renderToggles() {
    const box = $("lkToggles");
    box.replaceChildren();
    PIECES.forEach((p) => {
      const on = !!state[p.k];
      const b = el("button", "lk-toggle" + (on ? " on" : ""));
      b.type = "button";
      b.appendChild(document.createTextNode(p.label));
      b.appendChild(el("small", "", on ? "Portée" : p.sub));
      b.addEventListener("click", () => { state[p.k] = on ? 0 : 1; render(); });
      box.appendChild(b);
    });
  }

  function renderPresets() {
    const box = $("lkPresets");
    box.replaceChildren();
    PRESETS.forEach(([label, v]) => {
      const c = el("button", "lk-chip", label);
      c.type = "button";
      c.addEventListener("click", () => { state.belt = v.belt; state.vest = v.vest; state.hat = v.hat; render(); });
      box.appendChild(c);
    });
  }

  function renderFigure() {
    $("lkBelt").classList.toggle("off", !state.belt);
    $("lkVest").classList.toggle("off", !state.vest);
    $("lkHat").classList.toggle("off", !state.hat);
    $("lkPony").classList.toggle("off", !state.female);
    $("lkHair").style.display = state.hat ? "none" : "";
    const worn = [state.belt ? "ceinture" : "", state.vest ? "gilet" : "", state.hat ? "casquette" : ""].filter(Boolean);
    $("lkPreviewNote").textContent = (state.female ? "Femme" : "Homme") + " · " + (worn.length ? worn.join(", ") : "tenue simple");
  }

  function render() {
    $("lkFac").textContent = state.facName || "Police";
    $("lkRank").textContent = "Grade : " + (state.rankName || "—");
    const st = $("lkState");
    st.textContent = state.uniform ? "En tenue de service" : "Tenue civile";
    st.classList.toggle("on", !!state.uniform);
    Array.prototype.forEach.call(document.querySelectorAll(".lk-seg-btn"), (b) => b.classList.toggle("on", Number(b.dataset.v) === state.female));
    $("lkCivil").classList.toggle("locked", !state.uniform);
    $("lkWear").textContent = state.uniform ? "Mettre à jour la tenue" : "Enfiler la tenue de service";
    renderToggles();
    renderFigure();
  }

  function apply(d) {
    state.fac = Number(d.fac) || 0;
    state.facName = d.facName || "Police";
    state.rank = Number(d.rank) || 1;
    state.rankName = d.rankName || "";
    state.uniform = Number(d.uniform) ? 1 : 0;
    state.female = Number(d.female) ? 1 : 0;
    state.belt = Number(d.belt) ? 1 : 0;
    state.vest = Number(d.vest) ? 1 : 0;
    state.hat = Number(d.hat) ? 1 : 0;
    render();
  }

  Array.prototype.forEach.call(document.querySelectorAll(".lk-seg-btn"), (b) =>
    b.addEventListener("click", () => { state.female = Number(b.dataset.v); render(); }));
  $("lkWear").addEventListener("click", () => send("wear", [state.female, state.belt, state.vest, state.hat].join(" ")));
  $("lkCivil").addEventListener("click", () => { if (state.uniform) send("civil", ""); });
  $("lkClose").addEventListener("click", () => {
    if (bridge && typeof bridge.close === "function") { try { bridge.close(); } catch (_) {} }
  });

  window.LockerApp = {
    openFromNative(raw) { apply(parse(raw)); },
    onUpdateFromNative(raw) {
      const d = parse(raw);
      apply(d);
      if (d.msg) toast(d.msg, d.ok === 1 || d.ok === true);
    }
  };

  renderPresets();
  render();

  /* Apercu navigateur (hors jeu) : donnees fictives */
  if (!bridge) {
    window.LockerApp.openFromNative(JSON.stringify({ fac: 1, facName: "Police", rank: 2, rankName: "Officier", uniform: 0, female: 0, belt: 1, vest: 1, hat: 0 }));
  }
  try { if (bridge && typeof bridge.ready === "function") bridge.ready(); } catch (_) {}
})();
