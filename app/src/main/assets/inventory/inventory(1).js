/* Crestwood - Inventaire
   Serveur -> page : InvApp.openFromNative(json) / InvApp.onUpdateFromNative(json)
       json = {"items":[{"id":"eau_kirene","qty":2}],"cash":5000,"ok":1,"msg":"..."}
   Page -> serveur : SampInv.action("use"|"drop", {item, qty}) puis /invact (voir SAMP.java)
   Pour ajouter un objet : ajouter son image dans items/ et une ligne dans CATALOG
   (puis le meme id dans g_InvItems de basicgm.pwn). */
(() => {
  "use strict";
  const bridge = window.SampInv || null;
  const MAX_SLOTS = 20;

  const CATALOG = {
    iphone_orange:  { name: "iPhone Orange",       tag: "Téléphone",  usable: false, desc: "Un smartphone haut de gamme, finition orange cuivré." },
    iphone_noir:    { name: "iPhone Titane Noir",  tag: "Téléphone",  usable: false, desc: "Un smartphone haut de gamme, finition titane noir." },
    enceinte_jbl:   { name: "Enceinte JBL",        tag: "Accessoire", usable: false, desc: "Enceinte Bluetooth portable et étanche." },
    apple_watch:    { name: "Apple Watch",         tag: "Accessoire", usable: false, desc: "Montre connectée avec bracelet noir." },
    liasse_billets: { name: "Liasse de billets",   tag: "Argent",     usable: true,  useLabel: "Encaisser", desc: "Une liasse de billets. L'encaisser ajoute 1 000 $ en liquide." },
    gilet_tactique: { name: "Gilet tactique",      tag: "Équipement", usable: true,  useLabel: "Équiper",   desc: "Gilet pare-balles avec porte-chargeurs. Donne 100 d'armure." },
    vody:           { name: "Vody Vodka Energy",   tag: "Boisson",    usable: true,  useLabel: "Boire",     desc: "Canette de vodka énergisante (18 %). À consommer avec modération." },
    eau_kirene:     { name: "Eau Kirène",          tag: "Boisson",    usable: true,  useLabel: "Boire",     desc: "Eau minérale naturelle. Redonne un peu de santé." }
  };

  const $ = (id) => document.getElementById(id);
  const el = {
    grid: $("invGrid"), count: $("invCount"), cash: $("invCash"), close: $("invClose"),
    empty: $("invDetailEmpty"), info: $("invInfo"), img: $("invImg"), tag: $("invTag"),
    name: $("invName"), desc: $("invDesc"), qtyValue: $("qtyValue"), qtyOwned: $("qtyOwned"),
    minus: $("qtyMinus"), plus: $("qtyPlus"), use: $("btnUse"), drop: $("btnDrop"), toast: $("invToast")
  };

  let items = [];      // [{id, qty}]
  let selected = null; // id
  let amount = 1;
  let toastTimer = null;

  function parse(raw) {
    if (raw && typeof raw === "object") return raw;
    try { return JSON.parse(raw || "{}") || {}; } catch (_) { return {}; }
  }
  const qtyOf = (id) => { const it = items.find((i) => i.id === id); return it ? it.qty : 0; };
  const info = (id) => CATALOG[id] || { name: id, tag: "Objet", usable: false, desc: "Objet inconnu." };

  function toast(msg, ok) {
    if (!msg) return;
    el.toast.textContent = msg;
    el.toast.className = "inv-toast show " + (ok ? "ok" : "err");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.toast.className = "inv-toast"; }, 2400);
  }

  function renderGrid() {
    el.grid.textContent = "";
    for (let i = 0; i < MAX_SLOTS; i++) {
      const it = items[i];
      const slot = document.createElement("button");
      slot.type = "button";
      slot.className = "slot" + (it ? " filled" : "") + (it && it.id === selected ? " selected" : "");
      if (it) {
        const img = document.createElement("img");
        img.src = "items/" + it.id + ".png";
        img.alt = info(it.id).name;
        img.onerror = () => { img.style.display = "none"; };
        const q = document.createElement("span");
        q.className = "qty";
        q.textContent = "x" + it.qty;
        slot.append(img, q);
        slot.addEventListener("click", () => select(it.id));
      } else {
        slot.disabled = true;
      }
      el.grid.appendChild(slot);
    }
    el.count.textContent = items.length + " / " + MAX_SLOTS + " emplacements";
  }

  function renderDetail() {
    const have = selected ? qtyOf(selected) : 0;
    if (!selected || have <= 0) {
      selected = null;
      el.empty.classList.remove("hidden");
      el.info.classList.add("hidden");
      return;
    }
    const meta = info(selected);
    amount = Math.max(1, Math.min(amount, have));
    el.empty.classList.add("hidden");
    el.info.classList.remove("hidden");
    el.img.src = "items/" + selected + ".png";
    el.tag.textContent = meta.tag;
    el.name.textContent = meta.name;
    el.desc.textContent = meta.desc;
    el.qtyValue.textContent = amount;
    el.qtyOwned.textContent = "en stock : " + have;
    el.use.textContent = meta.useLabel || "Utiliser";
    el.use.disabled = !meta.usable;
  }

  function select(id) {
    selected = id;
    amount = 1;
    renderGrid();
    renderDetail();
  }

  function setState(data) {
    if (Array.isArray(data.items)) {
      items = data.items
        .map((i) => ({ id: String(i.id || ""), qty: Math.max(0, parseInt(i.qty, 10) || 0) }))
        .filter((i) => i.id && i.qty > 0)
        .slice(0, MAX_SLOTS);
    }
    if (typeof data.cash === "number") el.cash.textContent = "$" + data.cash.toLocaleString("fr-FR");
    renderGrid();
    renderDetail();
  }

  function send(type) {
    if (!selected) return;
    const payload = { item: selected, qty: type === "use" ? 1 : amount };
    if (bridge && typeof bridge.action === "function") {
      try { bridge.action(type, JSON.stringify(payload)); } catch (_) {}
    } else {
      toast("Hors jeu : action " + type + " ignorée.", false);
    }
  }

  el.minus.addEventListener("click", () => { amount = Math.max(1, amount - 1); renderDetail(); });
  el.plus.addEventListener("click", () => { amount = Math.min(qtyOf(selected), amount + 1); renderDetail(); });
  el.use.addEventListener("click", () => send("use"));
  el.drop.addEventListener("click", () => send("drop"));
  el.close.addEventListener("click", () => {
    if (bridge && typeof bridge.close === "function") { try { bridge.close(); } catch (_) {} }
  });

  window.InvApp = {
    openFromNative(raw) { setState(parse(raw)); },
    onUpdateFromNative(raw) {
      const d = parse(raw);
      setState(d);
      if (d.msg) toast(d.msg, !!d.ok);
    }
  };

  renderGrid();
  renderDetail();
  try { if (bridge && typeof bridge.ready === "function") bridge.ready(); } catch (_) {}

  // Apercu hors jeu (navigateur) : donnees de demonstration
  if (!bridge) {
    setState({ cash: 5000, items: Object.keys(CATALOG).map((id, i) => ({ id, qty: i + 1 })) });
  }
})();
