/* Panneau d'administration Crestwood.
   Serveur -> page : AdminApp.openFromNative(json) / AdminApp.onUpdateFromNative(json)
   Page -> serveur : SampAdmin.action(type, {id, arg}) puis /adminact (voir SAMP.java)
   Le serveur (basicgm.pwn, module ADMINISTRATION) re-verifie le niveau a chaque action :
   masquer un bouton ici n'est qu'un confort, pas une protection. */
(function () {
  "use strict";
  const bridge = window.SampAdmin || null;
  const ROLES = ["Joueur", "Admin", "Rcon", "Développeur"];
  const FACTIONS = ["Civil", "Police"];
  const POLICE_RANKS = ["Cadet", "Officier", "Sergent", "Lieutenant", "Capitaine", "Commandant"];
  const state = { lvl: 0, role: "Joueur", me: -1, count: 0, players: [], lockers: [], sel: null, tab: "players", filter: "" };
  function facLabel(p) {
    if (!p || !p.f) return "";
    const rank = POLICE_RANKS[Math.max(0, Math.min(POLICE_RANKS.length, Number(p.r) || 1) - 1)];
    return (FACTIONS[p.f] || "Faction") + (p.f === 1 ? " · " + rank : "");
  }

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

  /* ------------------------------------------------------------ envoi */
  function send(type, id, arg) {
    const payload = JSON.stringify({ id: id == null ? 0 : id, arg: arg == null ? "" : String(arg) });
    if (bridge && typeof bridge.action === "function") {
      try { bridge.action(type, payload); } catch (_) {}
    } else {
      console.log("[admin] action", type, payload);
    }
  }

  /* ------------------------------------------------------------ actions */
  const ACTIONS = [
    { k: "heal",      label: "Soigner",           sub: "Vie + gilet",         lvl: 1 },
    { k: "goto",      label: "Aller vers",        sub: "Te téléporter",       lvl: 1, other: true },
    { k: "bring",     label: "Amener",            sub: "Le téléporter à toi", lvl: 1, other: true },
    { k: "freeze",    label: "Geler",             sub: "Bloquer",             lvl: 1, other: true },
    { k: "unfreeze",  label: "Dégeler",           sub: "Débloquer",           lvl: 1 },
    { k: "kick",      label: "Expulser",          sub: "Avec raison",         lvl: 1, other: true, danger: true,
      ask: { title: "Expulser le joueur", text: "Raison de l'expulsion :", value: "Comportement" } },
    { k: "ban",       label: "Bannir",            sub: "Rcon et plus",        lvl: 2, other: true, danger: true,
      ask: { title: "Bannir le joueur", text: "Raison du bannissement :", value: "Règlement" } },
    { k: "givemoney", label: "Donner de l'argent", sub: "Développeur",        lvl: 3,
      ask: { title: "Donner de l'argent", text: "Montant en liquide (négatif pour retirer) :", value: "1000", number: true } },
    { k: "setadmin",  label: "Définir le rôle",   sub: "Développeur",         lvl: 3, other: true,
      ask: { title: "Rôle staff", text: "Choisis le nouveau rôle :", choices: ROLES } },
    { k: "setfaction", label: "Définir la faction", sub: "Civil / Police",      lvl: 1,
      ask: { title: "Faction du joueur", text: "Seul un membre du staff peut nommer un policier :", choices: FACTIONS } },
    { k: "setrank",    label: "Grade de police",   sub: "Cadet à Commandant",  lvl: 1,
      ask: { title: "Grade de police", text: "Choisis le grade :", choices: POLICE_RANKS, base: 1 } }
  ];

  /* ------------------------------------------------------------ fenetre de saisie */
  let modalDone = null;
  function openModal(cfg, done) {
    $("admModalTitle").textContent = cfg.title || "";
    $("admModalText").textContent = cfg.text || "";
    const input = $("admModalInput");
    const choices = $("admModalChoices");
    modalDone = done;
    choices.replaceChildren();
    if (cfg.choices) {
      input.classList.add("hidden");
      choices.classList.remove("hidden");
      let picked = 0;
      cfg.choices.forEach((name, i) => {
        const b = el("button", "adm-btn" + (i === 0 ? " on" : ""), name);
        b.type = "button";
        b.addEventListener("click", () => {
          picked = i;
          Array.prototype.forEach.call(choices.children, (c, j) => c.classList.toggle("on", j === i));
          modalDone.pick = picked;
        });
        choices.appendChild(b);
      });
      modalDone.pick = 0;
      modalDone.mode = "choice";
    } else {
      choices.classList.add("hidden");
      input.classList.remove("hidden");
      input.value = cfg.value || "";
      input.inputMode = cfg.number ? "numeric" : "text";
      input.maxLength = 90;
      modalDone.mode = "text";
      setTimeout(() => { try { input.focus(); input.select(); } catch (_) {} }, 60);
    }
    $("admModal").classList.remove("hidden");
  }
  function closeModal() { $("admModal").classList.add("hidden"); modalDone = null; }
  $("admModalCancel").addEventListener("click", closeModal);
  $("admModalOk").addEventListener("click", () => {
    if (!modalDone) return;
    const fn = modalDone.fn;
    const value = modalDone.mode === "choice" ? String(modalDone.pick || 0) : $("admModalInput").value.trim();
    closeModal();
    fn(value);
  });

  /* ------------------------------------------------------------ toast */
  let toastTimer = null;
  function toast(msg, ok) {
    if (!msg) return;
    const t = $("admToast");
    t.textContent = msg;
    t.classList.toggle("err", !ok);
    t.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add("hidden"), 3600);
  }

  /* ------------------------------------------------------------ rendu */
  function roleClass(l) { return l > 0 ? " r" + Math.min(3, l) : ""; }
  function initials(n) {
    const parts = String(n || "?").split(/[_\s.]+/).filter(Boolean);
    return ((parts[0] || "?").charAt(0) + (parts[1] ? parts[1].charAt(0) : "")).toUpperCase();
  }
  function selected() { return state.players.find((p) => p.id === state.sel) || null; }

  function renderHead() {
    $("admCount").textContent = state.count + (state.count > 1 ? " joueurs en ligne" : " joueur en ligne");
    const r = $("admRole");
    r.textContent = ROLES[Math.min(3, state.lvl)] || state.role;
    r.className = "adm-role" + roleClass(state.lvl);
  }

  function renderList() {
    const list = $("admList");
    list.replaceChildren();
    const q = state.filter.toLowerCase();
    const rows = state.players.filter((p) => !q || p.n.toLowerCase().includes(q) || String(p.id) === q);
    if (!rows.length) { list.appendChild(el("div", "adm-empty", "Aucun joueur trouvé")); return; }
    rows.forEach((p) => {
      const b = el("button", "adm-row" + (p.id === state.sel ? " sel" : ""));
      b.type = "button";
      b.appendChild(el("div", "adm-av", initials(p.n)));
      const main = el("div", "adm-row-main");
      main.appendChild(el("div", "adm-row-name", p.n + (p.id === state.me ? " (toi)" : "")));
      main.appendChild(el("div", "adm-row-sub", "ID " + p.id + " · " + p.p + " ms" + (p.f ? " · " + facLabel(p) : "")));
      b.appendChild(main);
      if (p.l > 0) b.appendChild(el("span", "adm-tag r" + Math.min(3, p.l), ROLES[Math.min(3, p.l)]));
      b.addEventListener("click", () => { state.sel = p.id; renderList(); renderDetail(); });
      list.appendChild(b);
    });
  }

  function renderDetail() {
    const box = $("admDetail");
    box.replaceChildren();
    const p = selected();
    if (!p) { box.appendChild(el("div", "adm-empty", "Choisis un joueur dans la liste pour afficher les actions.")); return; }
    const isMe = p.id === state.me;

    const who = el("div", "adm-card");
    const row = el("div", "adm-who");
    row.appendChild(el("div", "adm-av", initials(p.n)));
    const info = el("div");
    info.appendChild(el("div", "adm-who-name", p.n));
    info.appendChild(el("div", "adm-who-sub", "ID " + p.id + " · " + p.p + " ms · " + ROLES[Math.min(3, p.l || 0)] + (p.f ? " · " + facLabel(p) : "")));
    row.appendChild(info);
    who.appendChild(row);
    const bars = el("div", "adm-bars");
    [["Vie", p.h, ""], ["Gilet", p.a, " a"]].forEach(([label, val, cls]) => {
      const b = el("div", "adm-bar" + cls, label + " · " + Math.max(0, Math.min(100, val)) + "%");
      const track = el("i");
      const fill = el("b");
      fill.style.width = Math.max(0, Math.min(100, val)) + "%";
      track.appendChild(fill);
      b.appendChild(track);
      bars.appendChild(b);
    });
    who.appendChild(bars);
    box.appendChild(who);

    const acts = el("div", "adm-card");
    acts.appendChild(el("h3", "", "Actions"));
    const grid = el("div", "adm-grid");
    ACTIONS.forEach((a) => {
      const locked = state.lvl < a.lvl || (a.other && isMe);
      const b = el("button", "adm-btn" + (a.danger ? " danger" : "") + (locked ? " locked" : ""));
      b.type = "button";
      b.appendChild(document.createTextNode(a.label));
      b.appendChild(el("small", "", state.lvl < a.lvl ? "Niveau " + ROLES[a.lvl] : a.sub));
      b.addEventListener("click", () => runAction(a, p));
      grid.appendChild(b);
    });
    acts.appendChild(grid);
    box.appendChild(acts);
  }

  function runAction(a, p) {
    if (!a.ask) { send(a.k, p.id, ""); return; }
    openModal(a.ask, { fn: (value) => {
      if (a.ask.number && !/^-?\d+$/.test(value)) { toast("Montant invalide.", false); return; }
      if (a.ask.choices && a.ask.base) value = String(Number(value) + a.ask.base);
      send(a.k, p.id, value);
    } });
  }

  /* ---- Onglet Serveur ---- */
  function serverCard(title, minLvl, buildBody) {
    const c = el("div", "adm-card" + (state.lvl < minLvl ? " locked" : ""));
    if (state.lvl < minLvl) c.appendChild(el("span", "lock", "Niveau " + ROLES[minLvl]));
    c.appendChild(el("h3", "", title));
    buildBody(c);
    return c;
  }
  function inlineForm(card, placeholder, btnLabel, onSend, opts) {
    const row = el("div", "adm-inline");
    const input = el("input", "adm-input");
    input.type = "text";
    input.placeholder = placeholder;
    input.maxLength = 90;
    if (opts && opts.number) input.inputMode = "numeric";
    const btn = el("button", "adm-btn primary", btnLabel);
    btn.type = "button";
    btn.addEventListener("click", () => {
      const v = input.value.trim();
      if (!v) return;
      onSend(v);
      if (!(opts && opts.keep)) input.value = "";
    });
    row.appendChild(input);
    row.appendChild(btn);
    card.appendChild(row);
    return input;
  }
  function chips(card, items, onPick) {
    const wrap = el("div", "adm-chips");
    items.forEach(([label, value]) => {
      const c = el("button", "adm-chip", label);
      c.type = "button";
      c.addEventListener("click", () => onPick(value));
      wrap.appendChild(c);
    });
    card.appendChild(wrap);
  }

  function renderServer() {
    const box = $("admServer");
    box.replaceChildren();
    box.appendChild(serverCard("Annonce globale", 1, (c) => {
      inlineForm(c, "Message pour tous les joueurs", "Envoyer", (v) => send("announce", 0, v));
    }));
    box.appendChild(serverCard("Créer un véhicule", 2, (c) => {
      inlineForm(c, "Nom ou numéro (400 à 611)", "Créer", (v) => send("vehicle", 0, v));
      chips(c, [["Infernus", "411"], ["NRG-500", "522"], ["Sultan", "560"], ["Bullet", "541"], ["Banshee", "429"]], (v) => send("vehicle", 0, v));
    }));
    box.appendChild(serverCard("Météo", 2, (c) => {
      inlineForm(c, "Numéro de 0 à 45", "Appliquer", (v) => send("weather", 0, v), { number: true });
      chips(c, [["Soleil", "1"], ["Nuageux", "7"], ["Pluie", "8"], ["Brouillard", "9"], ["Tempête", "16"]], (v) => send("weather", 0, v));
    }));
    box.appendChild(serverCard("Heure du serveur", 2, (c) => {
      inlineForm(c, "Heure de 0 à 23", "Régler", (v) => send("time", 0, v), { number: true });
      chips(c, [["Aube 6h", "6"], ["Midi 12h", "12"], ["Soir 18h", "18"], ["Nuit 0h", "0"]], (v) => send("time", 0, v));
    }));
    box.appendChild(serverCard("Redémarrer le serveur", 3, (c) => {
      const b = el("button", "adm-btn danger", "Redémarrer maintenant");
      b.type = "button";
      b.style.marginTop = "6px";
      b.addEventListener("click", () => {
        openModal({ title: "Redémarrer le serveur ?", text: "Tous les joueurs seront déconnectés quelques secondes." }, { fn: () => send("gmx", 0, "") });
      });
      c.appendChild(b);
    }));
  }

  /* ---- Onglet Factions : policiers en ligne + vestiaires ---- */
  function renderFactions() {
    const box = $("admFactions");
    box.replaceChildren();

    box.appendChild(serverCard("Vestiaires de police", 1, (c) => {
      c.appendChild(el("p", "adm-hint", "Place-toi à l'endroit voulu (à pied) puis crée le vestiaire : il apparaît avec une icône et un texte 3D. Seuls les policiers peuvent l'utiliser."));
      const add = el("button", "adm-btn primary", "Créer un vestiaire ici");
      add.type = "button";
      add.style.marginTop = "6px";
      add.addEventListener("click", () => send("lockeradd", 0, "1"));
      c.appendChild(add);
      const list = el("div", "adm-lockers");
      if (!state.lockers.length) list.appendChild(el("div", "adm-empty", "Aucun vestiaire pour le moment."));
      state.lockers.forEach((l) => {
        const row = el("div", "adm-locker");
        const info = el("div", "adm-locker-info");
        info.appendChild(el("b", "", "Vestiaire " + (FACTIONS[l.f] || "") + " #" + l.i));
        info.appendChild(el("small", "", "X " + l.x + " · Y " + l.y + " · Z " + l.z));
        row.appendChild(info);
        const go = el("button", "adm-btn", "Y aller");
        go.type = "button";
        go.addEventListener("click", () => send("lockergoto", l.i, ""));
        const del = el("button", "adm-btn danger", "Supprimer");
        del.type = "button";
        del.addEventListener("click", () => {
          openModal({ title: "Supprimer le vestiaire #" + l.i + " ?", text: "Il disparaît pour tous les joueurs." }, { fn: () => send("lockerdel", l.i, "") });
        });
        row.appendChild(go);
        row.appendChild(del);
        list.appendChild(row);
      });
      c.appendChild(list);
    }));

    box.appendChild(serverCard("Policiers en ligne", 1, (c) => {
      const cops = state.players.filter((p) => p.f === 1);
      c.appendChild(el("p", "adm-hint", "Pour nommer un policier : onglet Joueurs, choisis le joueur puis « Définir la faction »."));
      const list = el("div", "adm-lockers");
      if (!cops.length) list.appendChild(el("div", "adm-empty", "Aucun policier connecté."));
      cops.forEach((p) => {
        const row = el("div", "adm-locker");
        const info = el("div", "adm-locker-info");
        info.appendChild(el("b", "", p.n));
        info.appendChild(el("small", "", "ID " + p.id + " · " + facLabel(p)));
        row.appendChild(info);
        const retire = el("button", "adm-btn danger", "Retirer");
        retire.type = "button";
        retire.addEventListener("click", () => send("setfaction", p.id, "0"));
        row.appendChild(retire);
        list.appendChild(row);
      });
      c.appendChild(list);
    }));
  }

  function setTab(name) {
    state.tab = name;
    Array.prototype.forEach.call(document.querySelectorAll(".adm-tab"), (b) => b.classList.toggle("active", b.dataset.tab === name));
    $("tabPlayers").classList.toggle("hidden", name !== "players");
    $("tabFactions").classList.toggle("hidden", name !== "factions");
    $("tabServer").classList.toggle("hidden", name !== "server");
  }

  function apply(data, fromOpen) {
    state.lvl = Number(data.lvl) || 0;
    state.role = data.role || "Joueur";
    state.me = data.me == null ? -1 : Number(data.me);
    state.count = Number(data.count) || 0;
    state.players = Array.isArray(data.players) ? data.players : [];
    state.lockers = Array.isArray(data.lockers) ? data.lockers : [];
    if (state.sel != null && !state.players.some((p) => p.id === state.sel)) state.sel = null;
    if (fromOpen && state.sel == null && state.me >= 0) state.sel = state.me;
    renderHead();
    renderList();
    renderDetail();
    renderFactions();
    renderServer();
  }

  /* ------------------------------------------------------------ evenements */
  Array.prototype.forEach.call(document.querySelectorAll(".adm-tab"), (b) => b.addEventListener("click", () => setTab(b.dataset.tab)));
  $("admSearch").addEventListener("input", (e) => { state.filter = e.target.value.trim(); renderList(); });
  $("admRefresh").addEventListener("click", () => send("refresh", 0, ""));
  $("admClose").addEventListener("click", () => {
    if (bridge && typeof bridge.close === "function") { try { bridge.close(); } catch (_) {} }
  });

  window.AdminApp = {
    openFromNative(raw) { setTab("players"); apply(parse(raw), true); },
    onUpdateFromNative(raw) {
      const d = parse(raw);
      apply(d, false);
      if (d.msg) toast(d.msg, d.ok === 1 || d.ok === true);
    }
  };

  /* Apercu navigateur (hors jeu) : donnees fictives */
  if (!bridge) {
    window.AdminApp.openFromNative(JSON.stringify({
      lvl: 3, role: "Developpeur", me: 0, count: 4,
      players: [
        { id: 0, n: "Anzo_Smith", h: 100, a: 100, p: 34, l: 3, f: 0, r: 1 },
        { id: 1, n: "Sir_Sugo", h: 76, a: 0, p: 58, l: 0, f: 1, r: 2 },
        { id: 2, n: "Carl_Johnson", h: 100, a: 40, p: 41, l: 1, f: 0, r: 1 },
        { id: 3, n: "Marie_Dupont", h: 52, a: 0, p: 90, l: 2, f: 1, r: 4 }
      ],
      lockers: [{ i: 1, f: 1, x: 1555, y: -1675, z: 16 }, { i: 2, f: 1, x: 1481, y: -1771, z: 18 }]
    }));
  }
  try { if (bridge && typeof bridge.ready === "function") bridge.ready(); } catch (_) {}
})();
