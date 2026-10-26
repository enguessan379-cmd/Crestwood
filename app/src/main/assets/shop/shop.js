/* Boutique 24/7 Crestwood.
   Serveur -> page : ShopApp.openFromNative(json) / ShopApp.onUpdateFromNative(json)
     json = {"shop":0,"shopName":"24/7 Idlewood","cash":5000,"balance":0,"card":1,"ok":1,"msg":"",
             "bought":"eau_kirene","qty":2,"max":10,"items":[{"id":"eau_kirene","price":15,"owned":3}]}
   Page -> serveur : SampShop.action("buy", {"arg":"<article> <quantite> <cash|card>"}) puis /shopact (voir SAMP.java)
   Le serveur (basicgm1.pwn, module BOUTIQUE) re-verifie distance, argent et place dans le sac :
   la page ne fait que l'affichage. Les articles achetes arrivent dans l'inventaire. */
(function () {
  "use strict";
  const bridge = window.SampShop || null;

  const CATALOG = {
    eau_kirene:     { name: "Eau Kirène",         cat: "Boissons",    desc: "Eau minérale naturelle. Redonne un peu de santé." },
    vody:           { name: "Vody Vodka Energy",  cat: "Boissons",    desc: "Canette de vodka énergisante (18 %). À consommer avec modération." },
    iphone_orange:  { name: "iPhone Orange",      cat: "Téléphonie",  desc: "Smartphone haut de gamme, finition orange cuivré." },
    iphone_noir:    { name: "iPhone Titane Noir", cat: "Téléphonie",  desc: "Smartphone haut de gamme, finition titane noir." },
    enceinte_jbl:   { name: "Enceinte JBL",       cat: "Accessoires", desc: "Enceinte Bluetooth portable et étanche." },
    apple_watch:    { name: "Apple Watch",        cat: "Accessoires", desc: "Montre connectée avec bracelet noir." },
    gilet_tactique: { name: "Gilet tactique",     cat: "Équipement",  desc: "Gilet pare-balles avec porte-chargeurs. Donne 100 d'armure." },
    liasse_billets: { name: "Liasse de billets",  cat: "Divers",      desc: "Une liasse de billets." }
  };
  const CATS = ["Tout", "Boissons", "Téléphonie", "Accessoires", "Équipement"];

  const state = { shopName: "", cash: 0, balance: 0, card: 0, max: 10, items: [], cat: "Tout", sel: null, qty: 1, pay: "cash", busy: false };
  const $ = (id) => document.getElementById(id);
  const money = (v) => (Number(v) || 0).toLocaleString("fr-FR").replace(/ | /g, " ") + " $";
  const info = (id) => CATALOG[id] || { name: id, cat: "Divers", desc: "" };
  const img = (id) => "../inventory/items/" + id + ".png";

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

  let toastTimer = null;
  function toast(msg, ok) {
    if (!msg) return;
    const t = $("shToast");
    t.textContent = msg;
    t.classList.toggle("err", !ok);
    t.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add("hidden"), 3000);
  }

  function send(type, arg) {
    const payload = JSON.stringify({ arg: arg == null ? "" : String(arg) });
    if (bridge && typeof bridge.action === "function") {
      try { bridge.action(type, payload); } catch (_) {}
      return true;
    }
    console.log("[shop] action", type, payload);
    return false;
  }

  function selected() { return state.items.find((i) => i.id === state.sel) || null; }

  function renderCats() {
    const box = $("shCats");
    box.replaceChildren();
    CATS.forEach((c) => {
      const count = c === "Tout" ? state.items.length : state.items.filter((i) => info(i.id).cat === c).length;
      if (c !== "Tout" && count === 0) return;
      const b = el("button", "sh-cat" + (state.cat === c ? " on" : ""));
      b.type = "button";
      b.append(document.createTextNode(c), el("small", "", String(count)));
      b.addEventListener("click", () => { state.cat = c; render(); });
      box.appendChild(b);
    });
  }

  function renderGrid() {
    const box = $("shGrid");
    box.replaceChildren();
    state.items.filter((i) => state.cat === "Tout" || info(i.id).cat === state.cat).forEach((it) => {
      const c = el("button", "sh-card" + (state.sel === it.id ? " on" : ""));
      c.type = "button";
      const im = el("img");
      im.src = img(it.id);
      im.alt = "";
      im.onerror = () => { im.style.visibility = "hidden"; };
      c.append(im, el("span", "n", info(it.id).name), el("span", "p", money(it.price)));
      if (it.owned > 0) c.appendChild(el("span", "o", "x" + it.owned));
      c.addEventListener("click", () => { if (state.sel !== it.id) { state.sel = it.id; state.qty = 1; } render(); });
      box.appendChild(c);
    });
  }

  function renderDetail() {
    const it = selected();
    const d = it ? info(it.id) : null;
    const dimg = $("shImg");
    if (it) dimg.src = img(it.id);
    dimg.style.visibility = it ? "visible" : "hidden";
    $("shTag").textContent = d ? d.cat : "—";
    $("shItemName").textContent = d ? d.name : "Choisis un article";
    $("shDesc").textContent = d ? (d.desc + (it.owned ? "  ·  Dans ton sac : " + it.owned : "")) : "Touche un article pour voir son prix et l'acheter.";
    const maxQty = it ? Math.max(1, Math.min(state.max, 99 - (it.owned || 0))) : 1;
    if (state.qty > maxQty) state.qty = maxQty;
    $("shQty").textContent = String(state.qty);
    const total = it ? it.price * state.qty : 0;
    $("shTotal").textContent = money(total);
    const cardBtn = document.querySelector('.sh-seg-btn[data-pay="card"]');
    cardBtn.disabled = !state.card;
    if (!state.card && state.pay === "card") state.pay = "cash";
    document.querySelectorAll(".sh-seg-btn").forEach((b) => b.classList.toggle("on", b.dataset.pay === state.pay));
    const funds = state.pay === "card" ? state.balance : state.cash;
    const buy = $("shBuy");
    buy.disabled = !it || state.busy || total > funds || (it.owned || 0) >= 99;
    buy.textContent = !it ? "Acheter" : (total > funds ? "Fonds insuffisants" : "Acheter · " + money(total));
  }

  function render() {
    $("shName").textContent = (state.shopName || "Boutique") + (state.tax ? " · TVA " + state.tax + " % incluse" : "");
    $("shCash").textContent = money(state.cash);
    $("shBank").textContent = state.card ? money(state.balance) : "—";
    renderCats();
    renderGrid();
    renderDetail();
  }

  function apply(d) {
    state.shopName = d.shopName || state.shopName;
    state.cash = Number(d.cash) || 0;
    state.balance = Number(d.balance) || 0;
    state.card = Number(d.card) ? 1 : 0;
    state.max = Number(d.max) || 10;
    state.tax = Number(d.tax) || 0;
    if (Array.isArray(d.items)) {
      state.items = d.items.map((i) => ({ id: String(i.id || ""), price: Number(i.price) || 0, owned: Number(i.owned) || 0 })).filter((i) => i.id);
    }
    if (!state.sel || !selected()) state.sel = state.items.length ? state.items[0].id : null;
    state.busy = false;
    render();
  }

  $("shMinus").addEventListener("click", () => { if (state.qty > 1) { state.qty--; renderDetail(); } });
  $("shPlus").addEventListener("click", () => { state.qty++; renderDetail(); });
  document.querySelectorAll(".sh-seg-btn").forEach((b) => b.addEventListener("click", () => {
    if (b.disabled) return;
    state.pay = b.dataset.pay;
    renderDetail();
  }));
  $("shBuy").addEventListener("click", () => {
    const it = selected();
    if (!it || state.busy) return;
    state.busy = true;
    renderDetail();
    if (!send("buy", it.id + " " + state.qty + " " + state.pay)) {
      // apercu navigateur : simule la reponse du serveur
      setTimeout(() => window.ShopApp.onUpdateFromNative(JSON.stringify({
        cash: state.cash - (state.pay === "cash" ? it.price * state.qty : 0),
        balance: state.balance - (state.pay === "card" ? it.price * state.qty : 0), card: state.card,
        items: state.items.map((i) => i.id === it.id ? Object.assign({}, i, { owned: i.owned + state.qty }) : i),
        ok: 1, msg: "Achat effectue", bought: it.id, qty: state.qty
      })), 300);
    }
    setTimeout(() => { if (state.busy) { state.busy = false; renderDetail(); } }, 4000);
  });
  $("shClose").addEventListener("click", () => {
    if (bridge && typeof bridge.close === "function") { try { bridge.close(); } catch (_) {} }
  });

  window.ShopApp = {
    openFromNative(raw) { state.qty = 1; apply(parse(raw)); },
    onUpdateFromNative(raw) {
      const d = parse(raw);
      apply(d);
      if (Number(d.ok) && d.bought) {
        toast(info(d.bought).name + " x" + (Number(d.qty) || 1) + " ajouté à ton sac", true);
        state.qty = 1;
        renderDetail();
      } else if (d.msg) {
        toast(d.msg, Number(d.ok) === 1);
      }
    }
  };

  render();

  /* Apercu navigateur (hors jeu) : donnees fictives */
  if (!bridge) {
    window.ShopApp.openFromNative(JSON.stringify({
      shopName: "24/7 Idlewood", cash: 5000, balance: 12500, card: 1, max: 10,
      items: [
        { id: "eau_kirene", price: 15, owned: 2 }, { id: "vody", price: 40, owned: 0 },
        { id: "enceinte_jbl", price: 300, owned: 0 }, { id: "apple_watch", price: 450, owned: 1 },
        { id: "iphone_orange", price: 1100, owned: 0 }, { id: "iphone_noir", price: 1200, owned: 0 },
        { id: "gilet_tactique", price: 2500, owned: 0 }
      ]
    }));
  }
  try { if (bridge && typeof bridge.ready === "function") bridge.ready(); } catch (_) {}
})();
