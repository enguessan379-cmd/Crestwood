/* Support / report Crestwood (casque du menu rotatif).
   Serveur -> page : ReportApp.openFromNative(json) / ReportApp.onUpdateFromNative(json)
     {"open":1,"id":12,"st":1|2,"c":0,"by":"Staff","tg":"Joueur","msgs":[{"f":0|1|2,"n":"Nom","t":"texte","h":"12:30"}],"staff":2,"ok":1,"msg":""}
     {"open":0,"staff":2,"ok":1,"msg":""}
   Page -> serveur : SampReport.action(type, json) puis /repact (voir SAMP.java)
     new {cat, target, text} | msg {text} | cancel */
(function () {
  "use strict";
  const bridge = window.SampReport || null;
  const $ = (id) => document.getElementById(id);

  const CATS = [
    { k: 0, n: "Question", d: "Une question sur le serveur", icon: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9.3a2.6 2.6 0 0 1 5 .9c0 1.8-2.5 2.2-2.5 3.8"/><path d="M12 17h.01"/>' },
    { k: 1, n: "Bug", d: "Quelque chose ne marche pas", icon: '<rect x="7" y="7" width="10" height="13" rx="5"/><path d="M9 7a3 3 0 0 1 6 0M3 13h4M17 13h4M4 7l3 2M20 7l-3 2M4 19l3-2M20 19l-3-2"/>' },
    { k: 2, n: "Signaler un joueur", d: "Triche, insulte, non-RP…", icon: '<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>' },
    { k: 3, n: "Remboursement", d: "Argent ou objet perdu", icon: '<rect x="3" y="6" width="18" height="12" rx="2"/><circle cx="12" cy="12" r="2.6"/><path d="M6 9v.01M18 15v.01"/>' },
    { k: 4, n: "Autre", d: "Tout le reste", icon: '<circle cx="5.5" cy="12" r="1.3"/><circle cx="12" cy="12" r="1.3"/><circle cx="18.5" cy="12" r="1.3"/>' }
  ];
  const CAT_NAME = ["Question", "Bug", "Signalement", "Remboursement", "Autre"];

  const state = { cat: 0, open: false, data: null, count: 0 };

  function parse(raw) {
    if (raw && typeof raw === "object") return raw;
    try { return JSON.parse(raw || "{}") || {}; } catch (_) { return {}; }
  }
  function send(type, payload) {
    const json = JSON.stringify(payload || {});
    if (bridge && typeof bridge.action === "function") {
      try { bridge.action(type, json); } catch (_) {}
    } else {
      console.log("[report] action", type, json);
      demoReply(type, payload || {});
    }
  }

  /* ---------------------------------------------------------------- toast */
  let toastTimer = 0;
  function toast(msg, ok) {
    if (!msg) return;
    const t = $("rpToast");
    t.textContent = msg;
    t.classList.toggle("err", !ok);
    t.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add("hidden"), 4200);
  }

  /* ---------------------------------------------------------------- nouveau report */
  function renderCats() {
    const box = $("rpCats");
    box.replaceChildren();
    CATS.forEach((c) => {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "rp-cat" + (c.k === state.cat ? " on" : "");
      b.innerHTML = '<i><svg viewBox="0 0 24 24">' + c.icon + "</svg></i><span><b></b><small></small></span>";
      b.querySelector("b").textContent = c.n;
      b.querySelector("small").textContent = c.d;
      b.addEventListener("click", () => { state.cat = c.k; renderCats(); refreshForm(); });
      box.appendChild(b);
    });
  }

  function refreshForm() {
    const text = $("rpText").value.trim();
    $("rpCount").textContent = $("rpText").value.length + "/100";
    $("rpTargetWrap").classList.toggle("hidden", state.cat !== 2);
    const targetOk = state.cat !== 2 || /^\d{1,3}$/.test($("rpTarget").value.trim());
    $("rpSend").disabled = text.length < 3 || !targetOk;
  }

  $("rpText").addEventListener("input", refreshForm);
  $("rpTarget").addEventListener("input", () => {
    $("rpTarget").value = $("rpTarget").value.replace(/\D+/g, "").slice(0, 3);
    refreshForm();
  });
  $("rpSend").addEventListener("click", () => {
    const text = $("rpText").value.replace(/\s+/g, " ").trim().slice(0, 100);
    if (text.length < 3) return;
    const target = state.cat === 2 ? Number($("rpTarget").value) : -1;
    $("rpSend").disabled = true;
    send("new", { cat: state.cat, target, text });
  });

  /* ---------------------------------------------------------------- conversation */
  function renderThread(d) {
    $("rpNum").textContent = "#" + (Number(d.id) || 0);
    $("rpCat").textContent = CAT_NAME[Number(d.c)] || "Autre";
    $("rpTgRow").classList.toggle("hidden", !d.tg);
    $("rpTg").textContent = d.tg || "";
    const claimed = Number(d.st) === 2;
    const st = $("rpState");
    st.textContent = claimed ? "Pris en charge" + (d.by ? " · " + d.by : "") : "En attente du staff";
    st.classList.toggle("on", claimed);
    $("rpChatTip").textContent = claimed
      ? (d.by || "Un membre du staff") + " s'occupe de toi : réponds-lui ici."
      : "Ton report est en attente : un membre du staff va le prendre en charge.";

    const box = $("rpMsgs");
    const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 40;
    const msgs = Array.isArray(d.msgs) ? d.msgs : [];
    const before = box.childElementCount;
    box.replaceChildren();
    msgs.forEach((m, i) => {
      const f = Number(m.f);
      const row = document.createElement("div");
      row.className = "rp-m " + (f === 0 ? "me" : f === 1 ? "staff" : "sys") + (i >= before && before > 0 ? " new" : "");
      if (f !== 2) {
        const who = document.createElement("span");
        who.className = "who";
        who.textContent = f === 0 ? "Toi" : (m.n || "Staff");
        const time = document.createElement("time");
        time.textContent = m.h || "";
        who.appendChild(time);
        row.appendChild(who);
      }
      const p = document.createElement("p");
      p.textContent = m.t || "";
      row.appendChild(p);
      box.appendChild(row);
    });
    if (atBottom || msgs.length !== before) box.scrollTop = box.scrollHeight;
  }

  $("rpCompose").addEventListener("submit", (e) => {
    e.preventDefault();
    const input = $("rpMsg");
    const text = input.value.replace(/\s+/g, " ").trim().slice(0, 100);
    if (!text) return;
    input.value = "";
    send("msg", { text });
  });

  $("rpCancel").addEventListener("click", () => $("rpModal").classList.remove("hidden"));
  $("rpModalNo").addEventListener("click", () => $("rpModal").classList.add("hidden"));
  $("rpModalYes").addEventListener("click", () => { $("rpModal").classList.add("hidden"); send("cancel"); });

  /* ---------------------------------------------------------------- etat */
  function apply(d) {
    if (d.loading) return;
    $("rpLoading").classList.add("hidden");
    const staff = Number(d.staff) || 0;
    $("rpStaff").textContent = staff > 0
      ? staff + (staff > 1 ? " membres du staff en ligne" : " membre du staff en ligne")
      : "Aucun membre du staff en ligne pour le moment";
    const badge = $("rpBadge");
    if (Number(d.open) === 1) {
      const wasOpen = state.open;
      state.open = true;
      $("rpNew").classList.add("hidden");
      $("rpChat").classList.remove("hidden");
      badge.classList.remove("hidden");
      badge.classList.toggle("on", Number(d.st) === 2);
      badge.textContent = Number(d.st) === 2 ? "En cours" : "En attente";
      renderThread(d);
      if (!wasOpen) $("rpMsgs").scrollTop = $("rpMsgs").scrollHeight;
    } else {
      if (state.open) { $("rpText").value = ""; $("rpTarget").value = ""; }
      state.open = false;
      $("rpChat").classList.add("hidden");
      $("rpNew").classList.remove("hidden");
      badge.classList.add("hidden");
      refreshForm();
    }
    if (d.msg) toast(d.msg, Number(d.ok) === 1);
    else if (Number(d.ok) === 0) refreshForm();
  }

  $("rpClose").addEventListener("click", () => {
    if (bridge && typeof bridge.close === "function") { try { bridge.close(); } catch (_) {} }
  });

  window.ReportApp = {
    openFromNative(raw) { apply(parse(raw)); },
    onUpdateFromNative(raw) { apply(parse(raw)); },
    onNote() { /* les messages arrivent deja par onUpdateFromNative quand la page est ouverte */ }
  };

  renderCats();
  refreshForm();

  /* Apercu navigateur (hors jeu) : donnees fictives */
  let demo = null;
  function demoReply(type, p) {
    if (type === "new") {
      demo = { open: 1, id: 7, st: 1, c: p.cat, by: "", tg: p.cat === 2 ? "Carl_Johnson" : "", staff: 2, ok: 1,
        msg: "Report envoyé au staff.", msgs: [{ f: 0, n: "Moi", t: p.text, h: "18:02" }] };
      setTimeout(() => window.ReportApp.onUpdateFromNative(JSON.stringify(demo)), 200);
      setTimeout(() => {
        demo.st = 2; demo.by = "Anzo_Smith"; demo.msg = "";
        demo.msgs.push({ f: 2, n: "Support", t: "Anzo_Smith prend en charge ton report.", h: "18:03" });
        demo.msgs.push({ f: 1, n: "Anzo_Smith", t: "Salut ! Je regarde ça tout de suite, tu es où exactement ?", h: "18:03" });
        window.ReportApp.onUpdateFromNative(JSON.stringify(demo));
      }, 1500);
    } else if (type === "msg" && demo) {
      demo.msg = ""; demo.msgs.push({ f: 0, n: "Moi", t: p.text, h: "18:04" });
      window.ReportApp.onUpdateFromNative(JSON.stringify(demo));
    } else if (type === "cancel") {
      demo = null;
      window.ReportApp.onUpdateFromNative(JSON.stringify({ open: 0, staff: 2, ok: 1, msg: "Report fermé. Merci !" }));
    }
  }
  if (!bridge) {
    setTimeout(() => window.ReportApp.openFromNative(JSON.stringify({ open: 0, staff: 2, ok: 1, msg: "" })), 300);
  }
  try { if (bridge && typeof bridge.ready === "function") bridge.ready(); } catch (_) {}
})();
