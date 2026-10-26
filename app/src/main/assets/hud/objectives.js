/*
 * Objectifs de mission Crestwood (haut droite du HUD).
 *
 * Le serveur envoie ShowPlayerDialog(9650, "@obj", JSON) ; SAMP.java appelle ObjTracker.set(json) :
 *   {"show":1,"t":"LIVREUR","s":"Tournée 2 · Niveau 1","l":[["Monte dans le camion",2],["Livre les colis 1/4",1],["Retourne au dépôt",0]]}
 *   {"show":0}                      -> masque le panneau
 *   état d'une étape : 0 à faire, 1 en cours, 2 terminée
 */
(function () {
  "use strict";

  const box = document.getElementById("objTracker");
  const title = document.getElementById("objTitle");
  const sub = document.getElementById("objSub");
  const list = document.getElementById("objList");
  const root = document.querySelector(".hud-root");
  if (!box || !title || !list) return;

  let last = [];
  let hideTimer = 0;

  function parse(raw) {
    if (raw && typeof raw === "object") return raw;
    try { return JSON.parse(String(raw || "{}")) || {}; } catch (_) { return {}; }
  }

  function hide() {
    box.classList.remove("on");
    root?.classList.remove("obj-on");
    window.clearTimeout(hideTimer);
    hideTimer = window.setTimeout(() => { if (!box.classList.contains("on")) box.hidden = true; }, 260);
    last = [];
  }

  function set(raw) {
    const d = parse(raw);
    if (!d || !Number(d.show) || !Array.isArray(d.l) || !d.l.length) { hide(); return; }
    window.clearTimeout(hideTimer);
    title.textContent = String(d.t || "Mission").slice(0, 40);
    if (sub) sub.textContent = String(d.s || "").slice(0, 60);
    const rows = d.l.slice(0, 8).map((row) => {
      const text = Array.isArray(row) ? row[0] : row && row.t;
      const st = Array.isArray(row) ? Number(row[1]) : Number(row && row.s);
      return { text: String(text || "").slice(0, 60), st: st === 2 ? 2 : st === 1 ? 1 : 0 };
    });
    list.replaceChildren();
    rows.forEach((r, i) => {
      const li = document.createElement("li");
      li.className = r.st === 2 ? "done" : r.st === 1 ? "cur" : "";
      // étape qui vient de changer (avancée, compteur) : petite animation
      if (last[i] && (last[i].st !== r.st || last[i].text !== r.text)) li.classList.add("pop");
      const span = document.createElement("span");
      span.textContent = r.text;
      li.appendChild(span);
      li.appendChild(document.createElement("i"));
      list.appendChild(li);
    });
    last = rows;
    box.hidden = false;
    root?.classList.add("obj-on");
    // laisse le navigateur appliquer "hidden = false" avant l'animation d'entrée
    window.requestAnimationFrame(() => box.classList.add("on"));
  }

  window.ObjTracker = { set, hide };
})();
