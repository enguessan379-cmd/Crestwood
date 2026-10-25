/* Crestwood - appelle le clavier Android quand on touche un champ de saisie.
   Utilise le pont natif SampHud (showAndroidKeyboard / hideAndroidKeyboard).
   Ajouter data-kb="off" sur un champ pour l'ignorer (ex. le chat du HUD, qui gère déjà son clavier). */
(function () {
  "use strict";
  var TYPES = { text: 1, number: 1, tel: 1, password: 1, search: 1, email: 1, url: 1 };
  var hideTimer = null;

  function bridge() { return window.SampHud || null; }

  function isField(el) {
    if (!el || el.nodeType !== 1) return false;
    if (el.closest && el.closest('[data-kb="off"]')) return false;
    var tag = el.tagName;
    if (tag === "TEXTAREA") return true;
    if (tag !== "INPUT") return false;
    return !!TYPES[(el.getAttribute("type") || "text").toLowerCase()];
  }

  function show() {
    clearTimeout(hideTimer);
    var b = bridge();
    if (b && typeof b.showAndroidKeyboard === "function") {
      try { b.showAndroidKeyboard(); } catch (_) {}
    }
  }

  function hide() {
    // petit délai : si on passe d'un champ à l'autre, on ne ferme pas le clavier entre les deux
    clearTimeout(hideTimer);
    hideTimer = setTimeout(function () {
      var a = document.activeElement;
      if (isField(a)) return;
      var b = bridge();
      if (b && typeof b.hideAndroidKeyboard === "function") {
        try { b.hideAndroidKeyboard(); } catch (_) {}
      }
    }, 120);
  }

  document.addEventListener("focusin", function (e) { if (isField(e.target)) show(); }, true);
  document.addEventListener("focusout", function (e) { if (isField(e.target)) hide(); }, true);
  // un second toucher sur un champ déjà actif rouvre le clavier s'il a été fermé (bouton retour)
  document.addEventListener("click", function (e) { if (isField(e.target)) show(); }, true);

  window.CrestKeyboard = { show: show, hide: hide };
})();
