/* Emplois Crestwood : contrat de travail + agence pour l'emploi.
   Serveur -> page : JobApp.openFromNative(json) / JobApp.onUpdateFromNative(json)
     contrat {"mode":"contract","job":1,"my":0,"on":0,"level":1,"routes":0,"earned":0,"next":10,"shift":0,
              "bonus":250,"routePay":460,"steps":6,"step":0,"vehicle":0,"levelBonus":10,"player":"Anzo Smith",
              "date":"26/10/2026","signed":"","cooldown":0,"ok":1,"msg":""}
     agence  {"mode":"list","my":2,"on":0,"level":1,...,"jobs":[{"id":1,"routePay":460,"bonus":250,"steps":6,"vehicle":0,"level":1,"x":..,"y":..}]}
   Page -> serveur : SampJob.action(type, {"arg":"..."}) puis /jobact <type> [arg] (voir SAMP.java)
     sign <id> | start | stop | resign | gps <id> | list | refresh
   Le serveur (basicgm1.pwn, module EMPLOIS) re-verifie tout : la page ne fait que l'affichage. */
(function () {
  "use strict";
  const bridge = window.SampJob || null;

  const JOBS = {
    1: { name: "Bûcheron", company: "Scierie d'Angel Pine", place: "Angel Pine", blip: 99,
         desc: "Coupe les arbres autour de la scierie et rapporte le bois à la scie.",
         missions: ["Couper les arbres indiqués par le GPS (tronçonneuse fournie).", "Porter le bois jusqu'à la scie.", "Une prime est versée à chaque tournée complète."] },
    2: { name: "Livreur", company: "Crestwood Logistique", place: "Ocean Docks, Los Santos", blip: 145,
         desc: "Livre les commerces de Los Santos puis ramène le camion au dépôt.",
         missions: ["Conduire le camion de l'entreprise avec prudence.", "Livrer chaque commerce indiqué par le GPS.", "Ramener le camion au dépôt en fin de tournée."] },
    3: { name: "Chauffeur de bus", company: "Transports de Los Santos", place: "Unity Station", blip: 105,
         desc: "Dessers les arrêts du centre-ville et transporte les passagers en toute sécurité.",
         missions: ["Respecter les arrêts de la ligne indiqués par le GPS.", "Attendre la montée des passagers à chaque arrêt.", "Revenir au terminus d'Unity Station."] },
    4: { name: "Éboueur", company: "Propreté de Los Santos", place: "Willowfield", blip: 75,
         desc: "Ramasse les poubelles de Ganton et d'Idlewood puis vide la benne à la décharge.",
         missions: ["Conduire la benne à ordures de l'entreprise.", "S'arrêter à chaque point de collecte.", "Vider la benne à la décharge de Willowfield."] },
    5: { name: "Livreur de pizzas", company: "Well Stacked Pizza Co.", place: "Idlewood", blip: 139,
         desc: "Livre des pizzas bien chaudes aux habitants de Grove Street et d'Idlewood.",
         missions: ["Prendre le scooter de la pizzeria.", "Livrer chaque client indiqué par le GPS.", "Revenir à la pizzeria pour la commande suivante."] }
  };
  const VEHICLES = { 0: "À pied (outils fournis)", 414: "Camion Mule", 431: "Bus", 408: "Benne à ordures", 448: "Scooter Pizzaboy" };

  const $ = (id) => document.getElementById(id);
  const money = (v) => (Number(v) || 0).toLocaleString("fr-FR").replace(/ | /g, " ") + " $";
  const meta = (id) => JOBS[id] || { name: "Métier " + id, company: "Entreprise", place: "", blip: 35, desc: "", missions: [] };
  let data = {};
  let confirmResign = false;

  function el(tag, cls, text) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined) n.textContent = text;
    return n;
  }
  function parse(raw) {
    if (raw && typeof raw === "object") return raw;
    try { return JSON.parse(raw || "{}") || {}; } catch (_) { return {}; }
  }
  function send(type, arg) {
    const payload = JSON.stringify({ arg: arg == null ? "" : String(arg) });
    if (bridge && typeof bridge.action === "function") {
      try { bridge.action(type, payload); } catch (_) {}
      return true;
    }
    console.log("[job] action", type, payload);
    return false;
  }
  function close() {
    if (bridge && typeof bridge.close === "function") { try { bridge.close(); } catch (_) {} }
  }

  let toastTimer = null;
  function toast(msg, ok) {
    if (!msg) return;
    const t = $("jbToast");
    t.textContent = msg;
    t.classList.toggle("err", !ok);
    t.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add("hidden"), 3400);
  }

  /* ------------------------- Signature au doigt ------------------------- */
  const canvas = $("jbCanvas");
  const ctx = canvas.getContext("2d");
  let inked = 0, drawing = false, last = null, locked = false;

  function sizeCanvas() {
    const r = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    if (!r.width || !r.height) return;
    const snapshot = inked ? canvas.toDataURL() : null;
    canvas.width = Math.round(r.width * dpr);
    canvas.height = Math.round(r.height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.lineCap = "round"; ctx.lineJoin = "round"; ctx.strokeStyle = "#1c2b6e";
    if (snapshot) { const im = new Image(); im.onload = () => ctx.drawImage(im, 0, 0, r.width, r.height); im.src = snapshot; }
  }
  function clearSignature() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    inked = 0;
    $("jbHint").classList.remove("hidden");
    renderActions();
  }
  function pos(e) { const r = canvas.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top, t: performance.now() }; }
  canvas.addEventListener("pointerdown", (e) => {
    if (locked) return;
    canvas.setPointerCapture(e.pointerId);
    drawing = true; last = pos(e);
    $("jbHint").classList.add("hidden");
  });
  canvas.addEventListener("pointermove", (e) => {
    if (!drawing || locked) return;
    const p = pos(e);
    const d = Math.hypot(p.x - last.x, p.y - last.y);
    if (d < 0.8) return;
    const speed = d / Math.max(1, p.t - last.t);
    ctx.lineWidth = Math.max(1.2, Math.min(3.2, 3.2 - speed * 1.4));
    ctx.beginPath(); ctx.moveTo(last.x, last.y); ctx.lineTo(p.x, p.y); ctx.stroke();
    inked += d; last = p;
  });
  function endDraw() { if (drawing) { drawing = false; renderActions(); } }
  canvas.addEventListener("pointerup", endDraw);
  canvas.addEventListener("pointercancel", endDraw);

  /* ------------------------------ Contrat ------------------------------ */
  function renderContract() {
    const d = data, job = Number(d.job) || 0, m = meta(job);
    const mine = Number(d.my) === job && job > 0;
    $("jbContract").classList.remove("hidden");
    $("jbAgency").classList.add("hidden");

    const intro = $("jbIntro");
    intro.replaceChildren();
    const mk = (t) => { const x = el("mark", "", t); return x; };
    intro.append("Par le présent instrument, l'entreprise ", mk(m.company), " et M./Mme ", mk(d.player || "—"),
      " conviennent de la prestation de services selon les termes ci-dessous : LE CONTRACTANT s'engage à exécuter ses fonctions avec professionnalisme, en respectant les règles de l'entreprise. Le contrat peut être résilié à tout moment par l'une ou l'autre des parties.");

    const lv = Number(d.level) || 1, lb = (Number(d.levelBonus) || 10) * (lv - 1);
    const rows = [
      ["Contractant(e)", d.player || "—"],
      ["Poste", m.name],
      ["Type", "Prestation de services"],
      ["Lieu de travail", m.place],
      ["Rémunération", "≈ " + money(Math.round((Number(d.routePay) || 0) * (100 + lb) / 100)) + " / tournée"],
      ["Prime de tournée", money(Math.round((Number(d.bonus) || 0) * (100 + lb) / 100))],
      ["Véhicule", VEHICLES[Number(d.vehicle) || 0] || "Véhicule de service"],
      ["Niveau", lv + (lb ? "  (+" + lb + " % de paie)" : "")]
    ];
    const dl = $("jbParties");
    dl.replaceChildren();
    rows.forEach(([k, v]) => dl.append(el("dt", "", k), el("dd", "", v)));

    const ul = $("jbMissions");
    ul.replaceChildren();
    m.missions.forEach((t) => ul.appendChild(el("li", "", t)));

    const stats = $("jbStats");
    stats.replaceChildren();
    stats.classList.toggle("hidden", !mine);
    if (mine) {
      [["Tournées", String(Number(d.routes) || 0)], ["Niveau", lv + " / 5"], ["Gains totaux", money(d.earned)],
       [Number(d.on) ? "Ce service" : "Prochain niv.", Number(d.on) ? money(d.shift) : (Number(d.next) ? d.next + " tournées" : "Max")]]
        .forEach(([k, v]) => { const s = el("div", "jb-stat"); s.append(el("b", "", v), el("small", "", k)); stats.appendChild(s); });
    }

    // Signature : contrat deja signe -> nom manuscrit + coche ; sinon zone de dessin
    locked = mine;
    $("jbPad").classList.toggle("locked", locked);
    $("jbSigned").classList.toggle("hidden", !mine);
    $("jbCheck").classList.toggle("hidden", !mine && inked < 60);
    if (mine) {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      inked = 0;
      $("jbHint").classList.add("hidden");
      $("jbSigned").textContent = d.player || "";
      $("jbSignLabel").textContent = "Signé le " + (d.signed || d.date || "—");
    } else {
      $("jbSignLabel").textContent = "Signature du Contractant · " + (d.date || "");
      if (!inked) $("jbHint").classList.remove("hidden");
    }
    renderActions();
  }

  function button(label, cls, onClick, disabled) {
    const b = el("button", "jb-btn" + (cls ? " " + cls : ""), label);
    b.type = "button";
    b.disabled = !!disabled;
    b.addEventListener("click", onClick);
    return b;
  }

  function renderActions() {
    const box = $("jbActions");
    if (data.mode !== "contract") return;
    box.replaceChildren();
    const job = Number(data.job) || 0, my = Number(data.my) || 0, on = Number(data.on) === 1;
    const cooldown = Number(data.cooldown) || 0;
    $("jbCheck").classList.toggle("hidden", !(my === job && job > 0) && inked < 60);

    if (my === job && job > 0) {
      if (on) box.appendChild(button("Finir le service", "danger", () => send("stop")));
      else box.appendChild(button("Prendre le service", "ok", () => send("start")));
      box.appendChild(button(confirmResign ? "Confirmer la démission" : "Démissionner", "danger", () => {
        if (!confirmResign) { confirmResign = true; renderActions(); setTimeout(() => { confirmResign = false; renderActions(); }, 4000); return; }
        confirmResign = false;
        send("resign");
      }));
    } else if (my > 0) {
      const n = el("div", "jb-note");
      n.append("Tu es déjà sous contrat comme ", el("b", "", meta(my).name), ". Démissionne pour signer ce contrat.");
      box.appendChild(n);
      box.appendChild(button(confirmResign ? "Confirmer la démission" : "Démissionner", "danger", () => {
        if (!confirmResign) { confirmResign = true; renderActions(); setTimeout(() => { confirmResign = false; renderActions(); }, 4000); return; }
        confirmResign = false;
        send("resign");
      }));
    } else {
      if (cooldown > 0) {
        const n = el("div", "jb-note");
        n.append("Tu viens de démissionner. Nouveau contrat possible dans ", el("b", "", cooldown + " s"), ".");
        box.appendChild(n);
      }
      box.appendChild(button("Signer le contrat", "", () => {
        if (inked < 60) { toast("Signe d'abord dans le cadre prévu.", false); return; }
        send("sign", job);
      }, inked < 60 || cooldown > 0));
      box.appendChild(button("Effacer la signature", "ghost", clearSignature, inked === 0));
    }
    box.appendChild(button("Agence pour l'emploi", "ghost", () => send("list")));
  }

  /* ------------------------------ Agence ------------------------------ */
  function renderAgency() {
    const d = data, my = Number(d.my) || 0, on = Number(d.on) === 1;
    $("jbAgency").classList.remove("hidden");
    $("jbContract").classList.add("hidden");
    $("jbAStatus").textContent = my
      ? "Sous contrat : " + meta(my).name + " · niveau " + (Number(d.level) || 1) + (on ? " · en service (" + money(d.shift) + ")" : "")
      : "Aucun contrat · choisis un métier puis va signer au siège";

    const list = $("jbAList");
    list.replaceChildren();
    (Array.isArray(d.jobs) ? d.jobs : []).forEach((j) => {
      const id = Number(j.id) || 0, m = meta(id), mine = id === my;
      const card = el("div", "jb-card" + (mine ? " mine" : ""));
      const top = el("div", "jb-card-top");
      const ic = el("img");
      ic.src = "../hud/radar/blips/blip_" + m.blip + ".png";
      ic.alt = "";
      const tt = el("div");
      tt.append(el("h3", "", m.name), el("p", "co", m.company + " · " + m.place));
      top.append(ic, tt);
      const tags = el("div", "jb-tags");
      tags.append(el("span", "jb-tag pay", "≈ " + money(j.routePay) + " / tournée"),
        el("span", "jb-tag", VEHICLES[Number(j.vehicle) || 0] || "Véhicule"),
        el("span", "jb-tag" + (mine ? " me" : ""), mine ? "Ton métier · niv. " + (Number(j.level) || 1) : "Niveau " + (Number(j.level) || 1)));
      card.append(top, el("p", "ds", m.desc), tags,
        button(mine ? "GPS vers mon entreprise" : "GPS vers le siège", mine ? "ok" : "", () => send("gps", id)));
      list.appendChild(card);
    });

    const foot = $("jbAFoot");
    foot.replaceChildren();
    if (on) foot.appendChild(button("Finir le service", "danger", () => send("stop")));
  }

  function render() {
    if (data.mode === "list") renderAgency();
    else renderContract();
    if (data.mode !== "list") requestAnimationFrame(sizeCanvas);
  }

  $("jbClose").addEventListener("click", close);
  $("jbAClose").addEventListener("click", close);
  window.addEventListener("resize", sizeCanvas);

  window.JobApp = {
    openFromNative(raw) { data = parse(raw); confirmResign = false; if (data.mode !== "list") inked = 0; render(); },
    onUpdateFromNative(raw) {
      const prevMode = data.mode, prevJob = data.job;
      data = parse(raw);
      if (data.mode !== prevMode || data.job !== prevJob) { inked = 0; ctx.clearRect(0, 0, canvas.width, canvas.height); }
      render();
      if (data.msg) toast(data.msg, Number(data.ok) === 1);
    }
  };

  /* Apercu navigateur (hors jeu) : donnees fictives (?list pour l'agence) */
  if (!bridge) {
    if (location.search.indexOf("list") >= 0) {
      window.JobApp.openFromNative({ mode: "list", my: 2, on: 1, level: 2, shift: 840, player: "Anzo Smith",
        jobs: [1, 2, 3, 4, 5].map((i) => ({ id: i, routePay: [0, 460, 930, 770, 630, 480][i], vehicle: [0, 0, 414, 431, 408, 448][i], level: i === 2 ? 2 : 1 })) });
    } else {
      window.JobApp.openFromNative({ mode: "contract", job: 1, my: 0, on: 0, level: 1, routes: 0, earned: 0, next: 10, bonus: 250,
        routePay: 460, steps: 6, vehicle: 0, levelBonus: 10, player: "Anzo Smith", date: "26/10/2026", cooldown: 0 });
    }
  }
  try { if (bridge && typeof bridge.ready === "function") bridge.ready(); } catch (_) {}
})();
