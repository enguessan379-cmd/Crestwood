/* Actions de police Crestwood.
   Serveur -> page : PoliceApp.openFromNative(json) / PoliceApp.onUpdateFromNative(json)
   Page -> serveur : SampPolice.action(type, {arg}) puis /policeact (voir SAMP.java)
   Le serveur (basicgm.pwn, module POLICE) re-verifie la faction, le grade, la distance et
   l'etat de la cible (menotte...) a CHAQUE action : cette page ne fait que l'affichage. */
(function () {
  "use strict";
  const bridge = window.SampPolice || null;

  const state = {
    tid: -1, tn: "", cf: 0, dr: 0, lic: 1, rank: 1, rn: "", jd: 0,
    cat: [],                       // [[code, libelle, amende, minutes de prison], ...]
    idv: null, papers: null, search: null
  };
  const ui = { mode: "", pickCharges: [], pickFine: 0, jailMin: 10, after: false };

  const ITEM_NAMES = {
    iphone_orange: "iPhone Orange", iphone_noir: "iPhone Titane Noir", enceinte_jbl: "Enceinte JBL",
    apple_watch: "Apple Watch", liasse_billets: "Liasse de billets", gilet_tactique: "Gilet tactique",
    vody: "Vody Vodka Energy", eau_kirene: "Eau Kirène"
  };
  // Libelles accentues des infractions (le serveur envoie du texte ASCII ; les codes sont ceux de g_PolChargeName).
  const CHARGE_LABELS = {
    1: "Excès de vitesse", 2: "Conduite dangereuse", 3: "Refus d'obtempérer", 4: "Port d'arme illégal",
    5: "Détention illégale", 6: "Vol", 7: "Agression", 8: "Outrage à agent",
    9: "Conduite sans permis", 10: "Trouble à l'ordre public"
  };
  const EXTRA_LABELS = { "Permis retire": "Permis retiré", "Permis rendu": "Permis rendu" };
  const labelMap = {};              // libelle ASCII du serveur -> libelle accentue
  function pretty(label) {
    const k = String(label || "");
    return labelMap[k] || EXTRA_LABELS[k] || k;
  }
  function chargeLabel(c) { return CHARGE_LABELS[c[0]] || c[1]; }
  const KIND = { c: ["Inculpation", ""], f: ["Amende", "f"], a: ["Arrestation", "a"], l: ["Permis", "l"] };
  const JAIL_MAX = 60;

  const $ = (id) => document.getElementById(id);
  function el(tag, cls, text) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined) n.textContent = text;
    return n;
  }
  function parse(raw) { try { return JSON.parse(raw || "{}") || {}; } catch (_) { return {}; } }
  function nice(name) { return String(name || "").replace(/_/g, " "); }
  function money(n) { return (Number(n) || 0).toLocaleString("fr-FR") + " $"; }
  function dateFr(ts) {
    const d = new Date((Number(ts) || 0) * 1000);
    if (!Number(ts) || isNaN(d.getTime())) return "—";
    return d.toLocaleDateString("fr-FR");
  }

  function send(type, arg) {
    const payload = JSON.stringify({ arg: arg == null ? "" : String(arg) });
    if (bridge && typeof bridge.action === "function") {
      try { bridge.action(type, payload); } catch (_) {}
    } else {
      console.log("[police] action", type, payload);
    }
  }

  let toastTimer = null;
  function toast(msg, ok) {
    if (!msg) return;
    const t = $("plToast");
    t.textContent = msg;
    t.classList.toggle("err", !ok);
    t.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add("hidden"), 3400);
  }

  /* ---------- barre d'actions ---------- */
  function setBtn(id, opts) {
    const b = $(id);
    b.classList.toggle("locked", !!opts.locked);
    b.classList.toggle("on", !!opts.on);
    if (opts.label) {
      const s = b.querySelector("span");
      if (s) s.textContent = opts.label;
    }
  }

  function renderBar() {
    $("plRank").textContent = "Grade : " + (state.rn || "—");
    $("plTargetName").textContent = state.tn ? nice(state.tn) : "—";
    const flags = $("plFlags");
    flags.replaceChildren();
    if (state.cf) flags.appendChild(el("span", "pl-flag warn", "Menotté"));
    if (state.dr) flags.appendChild(el("span", "pl-flag", "Traîné"));
    if (!state.lic) flags.appendChild(el("span", "pl-flag bad", "Permis retiré"));

    setBtn("plId", { on: ui.mode === "id" });
    setBtn("plPapers", { on: ui.mode === "papers" });
    setBtn("plSearch", { on: ui.mode === "search", locked: !state.cf });
    setBtn("plCuff", { on: !!state.cf, label: state.cf ? "Démenotter" : "Menotter" });
    setBtn("plDrag", { on: !!state.dr, locked: !state.cf, label: state.dr ? "Lâcher" : "Traîner" });
    setBtn("plLicense", { locked: !state.jd, label: state.lic ? "Retirer le permis" : "Rendre le permis" });
    setBtn("plFine", { on: ui.mode === "fine", locked: !state.jd });
    setBtn("plCharge", { on: ui.mode === "charge", locked: !state.jd });
    setBtn("plArrest", { on: ui.mode === "arrest", locked: !state.jd || !state.cf });
  }

  /* ---------- panneau de droite ---------- */
  function clearDetail() { const d = $("plDetail"); d.replaceChildren(); return d; }

  function viewEmpty() {
    const d = clearDetail();
    const box = el("div", "pl-empty");
    box.appendChild(el("b", "", state.tn ? nice(state.tn) : "Aucune cible"));
    box.appendChild(document.createTextNode(
      "Choisis une action à gauche : contrôle d'identité, menottes, fouille, amende, inculpation ou arrestation."));
    d.appendChild(box);
  }

  function field(dl, label, value, wide) {
    const w = el("div", wide ? "wide" : "");
    w.appendChild(el("dt", "", label));
    w.appendChild(el("dd", "", value));
    dl.appendChild(w);
  }

  function viewId() {
    const d = clearDetail();
    const v = state.idv;
    if (!v) { d.appendChild(el("p", "pl-note", "Demande de la carte d'identité…")); return; }
    const card = el("article", "pl-idcard");
    const head = el("div", "pl-idhead");
    head.appendChild(el("strong", "", "CRESTWOOD"));
    head.appendChild(el("span", "", "Carte d'identité · Gouvernement"));
    card.appendChild(head);
    const main = el("div", "pl-idmain");
    const initials = nice(state.tn).split(" ").map((p) => p.charAt(0)).join("").slice(0, 2).toUpperCase() || "?";
    main.appendChild(el("div", "pl-avatar", initials));
    const dl = el("dl", "pl-idfields");
    field(dl, "Nom / Name", nice(state.tn), true);
    field(dl, "N° d'identité", v.idn || "000");
    field(dl, "Sexe", v.sex || "M");
    field(dl, "Inscrit le", dateFr(v.reg));
    field(dl, "Nationalité", "Crestwoodienne");
    main.appendChild(dl);
    card.appendChild(main);
    d.appendChild(card);
    d.appendChild(el("p", "pl-note", "La photo et la signature restent stockées sur le téléphone du titulaire."));
  }

  function viewPapers() {
    const d = clearDetail();
    const p = state.papers;
    if (!p) { d.appendChild(el("p", "pl-note", "Consultation du dossier…")); return; }
    d.appendChild(el("h2", "pl-h", "Permis de conduire"));
    const st = el("div", "pl-status" + (p.lic ? "" : " bad"));
    st.appendChild(el("span", "", p.lic ? "Valide" : "Retiré"));
    if (!p.lic && p.licAt) st.appendChild(el("small", "", "depuis le " + dateFr(p.licAt)));
    d.appendChild(st);
    d.appendChild(el("h2", "pl-h", "Casier judiciaire"));
    const rec = Array.isArray(p.rec) ? p.rec : [];
    if (!rec.length) d.appendChild(el("p", "pl-note", "Aucune inscription."));
    rec.forEach((r) => {
      const k = KIND[r[0]] || ["Note", ""];
      const row = el("div", "pl-row");
      const left = el("div");
      left.appendChild(el("b", "", pretty(r[1])));
      left.appendChild(el("small", "", dateFr(r[2]) + " · " + nice(r[3] || "")));
      row.appendChild(left);
      row.appendChild(el("span", "pl-tag " + k[1], k[0]));
      d.appendChild(row);
    });
  }

  function viewSearch() {
    const d = clearDetail();
    const s = state.search;
    if (!s) { d.appendChild(el("p", "pl-note", "Fouille en cours…")); return; }
    d.appendChild(el("h2", "pl-h", "Argent liquide"));
    d.appendChild(el("div", "pl-cash", money(s.cash)));
    d.appendChild(el("h2", "pl-h", "Sac"));
    const inv = Array.isArray(s.inv) ? s.inv : [];
    if (!inv.length) d.appendChild(el("p", "pl-note", "Rien de suspect : le sac est vide."));
    inv.forEach((it) => {
      const row = el("div", "pl-row");
      row.appendChild(el("b", "", ITEM_NAMES[it[0]] || String(it[0])));
      row.appendChild(el("span", "amt", "× " + (Number(it[1]) || 0)));
      d.appendChild(row);
    });
  }

  function confirmButton(text, cls, onClick, locked) {
    const b = el("button", "pl-confirm" + (cls ? " " + cls : "") + (locked ? " locked" : ""), text);
    b.type = "button";
    b.addEventListener("click", onClick);
    return b;
  }

  function viewCharge() {
    const d = clearDetail();
    d.appendChild(el("h2", "pl-h", "Inculper " + nice(state.tn)));
    d.appendChild(el("p", "pl-note", "Choisis jusqu'à 5 chefs d'inculpation : ils sont inscrits au casier."));
    const chips = el("div", "pl-chips");
    state.cat.forEach((c) => {
      const on = ui.pickCharges.indexOf(c[0]) !== -1;
      const b = el("button", "pl-chip" + (on ? " on" : ""), chargeLabel(c));
      b.type = "button";
      b.addEventListener("click", () => {
        const i = ui.pickCharges.indexOf(c[0]);
        if (i !== -1) ui.pickCharges.splice(i, 1);
        else if (ui.pickCharges.length < 5) ui.pickCharges.push(c[0]);
        viewCharge();
      });
      chips.appendChild(b);
    });
    d.appendChild(chips);
    d.appendChild(confirmButton(
      ui.pickCharges.length ? "Inculper (" + ui.pickCharges.length + ")" : "Choisis au moins un chef",
      "", () => {
        if (!ui.pickCharges.length) return;
        ui.after = true;
        send("charge", ui.pickCharges.join(" "));
        ui.pickCharges = [];
      }, !ui.pickCharges.length));
  }

  function viewFine() {
    const d = clearDetail();
    d.appendChild(el("h2", "pl-h", "Amende pour " + nice(state.tn)));
    d.appendChild(el("p", "pl-note", "Le montant est prélevé sur l'argent liquide du citoyen."));
    state.cat.forEach((c) => {
      const row = el("div", "pl-row pick" + (ui.pickFine === c[0] ? " on" : ""));
      row.appendChild(el("b", "", chargeLabel(c)));
      row.appendChild(el("span", "amt", money(c[2])));
      row.addEventListener("click", () => { ui.pickFine = c[0]; viewFine(); });
      d.appendChild(row);
    });
    d.appendChild(confirmButton(ui.pickFine ? "Infliger l'amende" : "Choisis une infraction", "", () => {
      if (!ui.pickFine) return;
      ui.after = true;
      send("fine", ui.pickFine);
      ui.pickFine = 0;
    }, !ui.pickFine));
  }

  function viewArrest() {
    const d = clearDetail();
    d.appendChild(el("h2", "pl-h", "Arrêter " + nice(state.tn)));
    if (!state.cf) d.appendChild(el("p", "pl-note warn", "La cible doit être menottée."));
    d.appendChild(el("p", "pl-note", "Durée de détention (minutes réelles). Le suspect est transféré en cellule."));
    const step = el("div", "pl-stepper");
    const minus = el("button", "", "−"); minus.type = "button";
    const out = el("output", "", String(ui.jailMin));
    out.appendChild(el("small", "", "min"));
    const plus = el("button", "", "+"); plus.type = "button";
    const refresh = () => { out.firstChild.nodeValue = String(ui.jailMin); };
    minus.addEventListener("click", () => { ui.jailMin = Math.max(1, ui.jailMin - (ui.jailMin > 15 ? 5 : 1)); refresh(); });
    plus.addEventListener("click", () => { ui.jailMin = Math.min(JAIL_MAX, ui.jailMin + (ui.jailMin >= 15 ? 5 : 1)); refresh(); });
    step.appendChild(minus); step.appendChild(out); step.appendChild(plus);
    d.appendChild(step);
    const chips = el("div", "pl-chips");
    [5, 10, 15, 30, 45, 60].forEach((m) => {
      const c = el("button", "pl-chip", m + " min"); c.type = "button";
      c.addEventListener("click", () => { ui.jailMin = m; refresh(); });
      chips.appendChild(c);
    });
    d.appendChild(chips);
    d.appendChild(confirmButton("Placer en détention", "danger", () => {
      ui.after = true;
      send("arrest", ui.jailMin);
    }, !state.cf));
  }

  function renderDetail() {
    switch (ui.mode) {
      case "id": return viewId();
      case "papers": return viewPapers();
      case "search": return viewSearch();
      case "charge": return viewCharge();
      case "fine": return viewFine();
      case "arrest": return viewArrest();
      default: return viewEmpty();
    }
  }

  function render() { renderBar(); renderDetail(); }

  function setMode(m) {
    ui.mode = (ui.mode === m && (m === "charge" || m === "fine" || m === "arrest")) ? "" : m;
    render();
  }

  /* ---------- donnees du serveur ---------- */
  function apply(d) {
    if (d.tid !== undefined) state.tid = Number(d.tid);
    if (d.tn !== undefined) state.tn = String(d.tn);
    if (d.cf !== undefined) state.cf = Number(d.cf) ? 1 : 0;
    if (d.dr !== undefined) state.dr = Number(d.dr) ? 1 : 0;
    if (d.lic !== undefined && d.v !== "papers") state.lic = Number(d.lic) ? 1 : 0;
    if (d.rank !== undefined) state.rank = Number(d.rank) || 1;
    if (d.rn !== undefined) state.rn = String(d.rn);
    if (d.jd !== undefined) state.jd = Number(d.jd) ? 1 : 0;
    if (Array.isArray(d.cat)) {
      state.cat = d.cat;
      d.cat.forEach((c) => { labelMap[String(c[1])] = chargeLabel(c); });
    }
    if (d.v === "id") state.idv = { idn: d.idn, sex: d.sex, reg: d.reg };
    if (d.v === "papers") { state.papers = { lic: Number(d.lic) ? 1 : 0, licAt: d.licAt, rec: d.rec }; state.lic = state.papers.lic; }
    if (d.v === "search") state.search = { cash: d.cash, inv: d.inv };
    if (d.v === "id" && ui.mode !== "id") ui.mode = "id";
    if (d.v === "papers" && ui.mode !== "papers") ui.mode = "papers";
    if (d.v === "search" && ui.mode !== "search") ui.mode = "search";
    if (!state.cf && ui.mode === "search") ui.mode = "";
  }

  function resetViews() { state.idv = null; state.papers = null; state.search = null; ui.mode = ""; ui.pickCharges = []; ui.pickFine = 0; ui.after = false; }

  /* ---------- evenements ---------- */
  $("plId").addEventListener("click", () => { state.idv = null; setMode("id"); send("id"); });
  $("plPapers").addEventListener("click", () => { state.papers = null; setMode("papers"); send("papers"); });
  $("plSearch").addEventListener("click", () => { if (!state.cf) return; state.search = null; setMode("search"); send("search"); });
  $("plCuff").addEventListener("click", () => send(state.cf ? "uncuff" : "cuff"));
  $("plDrag").addEventListener("click", () => { if (state.cf) send(state.dr ? "undrag" : "drag"); });
  $("plLicense").addEventListener("click", () => { if (state.jd) { ui.after = true; send(state.lic ? "licwd" : "licret"); } });
  $("plFine").addEventListener("click", () => { if (state.jd) setMode("fine"); });
  $("plCharge").addEventListener("click", () => { if (state.jd) setMode("charge"); });
  $("plArrest").addEventListener("click", () => { if (state.jd && state.cf) setMode("arrest"); });
  $("plClose").addEventListener("click", () => {
    if (bridge && typeof bridge.close === "function") { try { bridge.close(); } catch (_) {} }
  });

  window.PoliceApp = {
    openFromNative(raw) { resetViews(); apply(parse(raw)); render(); },
    onUpdateFromNative(raw) {
      const d = parse(raw);
      apply(d);
      const ok = d.ok === 1 || d.ok === true;
      if (ui.after && d.msg) {
        ui.after = false;
        if (ok) {            // action de justice reussie : on montre le casier mis a jour
          ui.mode = "papers"; state.papers = null; send("papers");
        }
      }
      render();
      if (d.msg) toast(d.msg, ok);
    }
  };

  render();

  /* Apercu navigateur (hors jeu) : donnees fictives */
  if (!bridge) {
    window.PoliceApp.openFromNative(JSON.stringify({
      tid: 3, tn: "Elias_Vance", cf: 0, dr: 0, lic: 1, rank: 2, rn: "Officier", jd: 1,
      cat: [[1, "Exces de vitesse", 500, 5], [2, "Conduite dangereuse", 900, 10], [3, "Refus obtemperer", 1500, 15],
            [4, "Port arme illegal", 2500, 20], [5, "Detention illegale", 2000, 20], [6, "Vol", 2000, 20]]
    }));
  }
  try { if (bridge && typeof bridge.ready === "function") bridge.ready(); } catch (_) {}
})();
