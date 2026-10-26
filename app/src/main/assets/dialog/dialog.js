/* Dialogues SA-MP (ShowPlayerDialog) en HTML, au style Crestwood.
   Natif -> page : SampDialog.show({id, style, caption, text, left, right})   (DialogManager.java)
                   SampDialog.hide() / SampDialog.setKeyboard(hauteurPx)
   Page -> natif : AndroidDialog.respond(reponse, listitem, inputtext)          (1 = bouton gauche, 0 = droit)
   Styles : 0 MSGBOX, 1 INPUT, 2 LIST, 3 PASSWORD, 4 TABLIST, 5 TABLIST_HEADERS.
   Les codes couleur {RRGGBB} de SA-MP sont respectes. */
(function () {
  "use strict";
  const bridge = window.AndroidDialog || null;
  const $ = (id) => document.getElementById(id);

  const S = { MSGBOX: 0, INPUT: 1, LIST: 2, PASSWORD: 3, TABLIST: 4, HEADERS: 5 };
  const DEFAULT_COLOR = "";            // couleur par defaut = celle du theme (CSS)
  const state = { id: 0, style: 0, rows: [], sel: 0, lastTap: 0, sent: false };

  /* ---------- Codes couleur SA-MP -> HTML ---------- */
  const COLOR_RE = /\{([0-9A-Fa-f]{6})\}/g;
  function stripColors(s) { return String(s || "").replace(COLOR_RE, ""); }
  function colored(text, base) {
    const frag = document.createDocumentFragment();
    let color = base || DEFAULT_COLOR, last = 0, m;
    const src = String(text || "");
    COLOR_RE.lastIndex = 0;
    const push = (chunk) => {
      if (!chunk) return;
      const sp = document.createElement("span");
      if (color) sp.style.color = color;
      sp.textContent = chunk;
      frag.appendChild(sp);
    };
    while ((m = COLOR_RE.exec(src)) !== null) {
      push(src.slice(last, m.index));
      color = "#" + m[1];
      last = COLOR_RE.lastIndex;
    }
    push(src.slice(last));
    return frag;
  }
  // La couleur en cours a la fin d'une cellule continue dans la suivante (comme SA-MP)
  function lastColor(text, base) {
    let c = base || "", m;
    COLOR_RE.lastIndex = 0;
    while ((m = COLOR_RE.exec(String(text || ""))) !== null) c = "#" + m[1];
    return c;
  }

  /* ---------- Reponse ---------- */
  function respond(response) {
    if (state.sent) return;
    state.sent = true;
    let listitem = -1, input = "";
    if (state.style === S.INPUT || state.style === S.PASSWORD) {
      input = $("dgInput").value || "";
    } else if (state.style === S.LIST || state.style === S.TABLIST || state.style === S.HEADERS) {
      const row = state.rows[state.sel];
      listitem = row ? row.index : -1;
      input = row ? stripColors(row.cells[0]).trim() : "";
    }
    $("dgInput").blur();
    hide();
    if (bridge && typeof bridge.respond === "function") {
      try { bridge.respond(response, listitem, input); } catch (_) {}
    } else {
      console.log("[dialog] reponse", response, listitem, input);
    }
  }

  /* ---------- Listes ---------- */
  function parseRows(text, style) {
    const lines = String(text || "").replace(/\r/g, "").split("\n");
    let header = null;
    if (style === S.HEADERS && lines.length) header = lines.shift().split("\t");
    const rows = lines.map((l, i) => ({
      index: i,
      cells: style === S.LIST ? [l] : l.split("\t").filter((c, k, a) => c !== "" || k < a.length - 1)
    }));
    // SA-MP ignore la derniere ligne vide
    while (rows.length && rows[rows.length - 1].cells.join("") === "") rows.pop();
    return { header, rows };
  }

  function renderRows() {
    const box = $("dgRows");
    box.replaceChildren();
    const q = stripColors($("dgSearch").value || "").trim().toLowerCase();
    let shown = 0;
    state.rows.forEach((row, k) => {
      if (q && stripColors(row.cells.join(" ")).toLowerCase().indexOf(q) < 0) return;
      const r = document.createElement("div");
      r.className = "dg-row" + (k === state.sel ? " on" : "");
      r.setAttribute("role", "option");
      let color = "";
      row.cells.forEach((c) => {
        const cell = document.createElement("span");
        cell.appendChild(colored(c, color));
        color = lastColor(c, color);
        r.appendChild(cell);
      });
      r.addEventListener("click", () => {
        const now = Date.now();
        if (state.sel === k && now - state.lastTap < 450) { respond(1); return; }   // double tap = valider
        state.lastTap = now;
        state.sel = k;
        box.querySelectorAll(".dg-row.on").forEach((n) => n.classList.remove("on"));
        r.classList.add("on");
      });
      box.appendChild(r);
      shown++;
    });
    if (!shown) {
      const e = document.createElement("div");
      e.className = "dg-empty";
      e.textContent = "Aucun résultat";
      box.appendChild(e);
    }
    const on = box.querySelector(".dg-row.on");
    if (on && on.scrollIntoView) on.scrollIntoView({ block: "nearest" });
  }

  function setColumns(n) {
    const cols = n <= 1 ? "1fr" : "minmax(0,1.25fr) " + Array(n - 1).fill("minmax(0,1fr)").join(" ");
    $("dgList").style.setProperty("--cols", cols);
  }

  /* ---------- Affichage ---------- */
  function hide() {
    $("dg").classList.add("hidden");
    $("dg").classList.remove("kb");
  }

  function show(d) {
    if (typeof d === "string") { try { d = JSON.parse(d); } catch (_) { d = {}; } }
    d = d || {};
    state.id = Number(d.id) || 0;
    state.style = Number(d.style) || 0;
    state.sent = false;
    state.sel = 0;
    state.lastTap = 0;

    $("dgCaption").replaceChildren(colored(d.caption || ""));
    const isList = state.style === S.LIST || state.style === S.TABLIST || state.style === S.HEADERS;
    const isInput = state.style === S.INPUT || state.style === S.PASSWORD;

    $("dgText").replaceChildren(isList ? document.createDocumentFragment() : colored(d.text || ""));
    $("dgInputWrap").classList.toggle("hidden", !isInput);
    $("dgList").classList.toggle("hidden", !isList);
    $("dgCard").classList.toggle("wide", isList && state.style !== S.LIST);

    const input = $("dgInput");
    input.value = "";
    input.type = state.style === S.PASSWORD ? "password" : "text";
    $("dgEye").classList.toggle("hidden", state.style !== S.PASSWORD);
    $("dgEye").classList.remove("on");

    $("dgSearch").value = "";
    if (isList) {
      const parsed = parseRows(d.text, state.style);
      state.rows = parsed.rows;
      let n = 1;
      state.rows.forEach((r) => { n = Math.max(n, r.cells.length); });
      if (parsed.header) n = Math.max(n, parsed.header.length);
      setColumns(Math.min(n, 4));
      const head = $("dgHeader");
      head.replaceChildren();
      head.classList.toggle("hidden", !parsed.header);
      if (parsed.header) parsed.header.forEach((h) => { const s = document.createElement("span"); s.appendChild(colored(h)); head.appendChild(s); });
      $("dgSearchWrap").classList.toggle("hidden", state.rows.length <= 10);
      renderRows();
    } else {
      state.rows = [];
      $("dgSearchWrap").classList.add("hidden");
    }

    const ok = stripColors(d.left || "").trim(), cancel = stripColors(d.right || "").trim();
    $("dgOk").textContent = ok || "OK";
    $("dgCancel").textContent = cancel;
    $("dgCancel").classList.toggle("hidden", !cancel);

    $("dg").classList.remove("hidden");
    if (isInput) setTimeout(() => { try { input.focus(); } catch (_) {} }, 120);
  }

  $("dgOk").addEventListener("click", () => respond(1));
  $("dgCancel").addEventListener("click", () => respond(0));
  $("dgInput").addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); respond(1); } });
  $("dgInput").addEventListener("focus", () => $("dg").classList.add("kb"));
  $("dgInput").addEventListener("blur", () => $("dg").classList.remove("kb"));
  $("dgSearch").addEventListener("input", renderRows);
  $("dgSearch").addEventListener("focus", () => $("dg").classList.add("kb"));
  $("dgSearch").addEventListener("blur", () => $("dg").classList.remove("kb"));
  $("dgEye").addEventListener("click", () => {
    const i = $("dgInput");
    const showPwd = i.type === "password";
    i.type = showPwd ? "text" : "password";
    $("dgEye").classList.toggle("on", showPwd);
  });

  window.SampDialog = {
    show,
    hide,
    /* Clavier Android ouvert (hauteur en px CSS) : la fenetre remonte en haut de l'ecran */
    setKeyboard(h) { $("dg").classList.toggle("kb", Number(h) > 80); },
    /* Retour Android = bouton droit (ou fermeture) */
    back() { respond(0); }
  };

  try { if (bridge && typeof bridge.ready === "function") bridge.ready(); } catch (_) {}

  /* Apercu navigateur (hors jeu) : ?style=0..5 */
  if (!bridge) {
    const st = Number((location.search.match(/style=(\d)/) || [])[1] || 5);
    const samples = {
      0: { caption: "{33CCFF}Crestwood RP {FFFFFF}- Information", text: "Bienvenue sur {FFCC33}Crestwood RP{FFFFFF} !\n\nTape {33CCFF}/aide{FFFFFF} pour voir la liste des commandes.\nBon jeu.", left: "OK", right: "" },
      1: { caption: "Renommer", text: "Entre le nouveau nom de ta faction :", left: "Valider", right: "Annuler" },
      2: { caption: "{33CCFF}Crestwood RP {FFFFFF}- Aide", text: "{FFFFFF}Général\n{33CC33}Argent & banque\n{FFB454}Boutique & inventaire\n{FFCC33}Emplois\n{7DB4FF}Véhicules\n{BDF38B}Factions\n{FF6B72}Lieux & GPS", left: "Ouvrir", right: "Fermer" },
      3: { caption: "Connexion", text: "Entre ton mot de passe :", left: "Connexion", right: "Quitter" },
      4: { caption: "Armes", text: "Desert Eagle\t{33CC33}$1500\t70 balles\nM4\t{33CC33}$5000\t200 balles\nShotgun\t{33CC33}$2500\t40 balles", left: "Acheter", right: "Fermer" },
      5: { caption: "{33CCFF}Aide {FFFFFF}- Emplois", text: "Commande\tDescription\n{FFCC33}/emplois\tAgence pour l'emploi (liste des métiers + GPS)\n{FFCC33}/contrat\tContrat de travail (au siège d'un métier)\n{FFCC33}/travail\tPrendre son service\n{FFCC33}/finservice\tTerminer son service\n{FFCC33}/demissionner\tQuitter son emploi", left: "Retour", right: "Fermer" }
    };
    show(Object.assign({ id: 1, style: st }, samples[st] || samples[5]));
  }
})();
