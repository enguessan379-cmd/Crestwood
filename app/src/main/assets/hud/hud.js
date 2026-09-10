(function () {
  'use strict';

  var svg = document.getElementById('hudSvg');
  var wheel = document.getElementById('interactionWheel');
  var toast = document.getElementById('toast');
  var state = { health: 100, armour: 0, thirst: 100, hunger: 100, energy: 100, dead: false, exhausted: false, inVehicle: false, speed: 0, vehHealth: 100, fuel: 100 };
  var toastTimer = 0;

  function clamp(value) {
    value = Number(value);
    if (!isFinite(value)) return 0;
    return Math.max(0, Math.min(100, value));
  }

  function send(action, payload) {
    try {
      if (window.AndroidHUD && typeof window.AndroidHUD.onHudAction === 'function') {
        window.AndroidHUD.onHudAction(action, payload || '{}');
      }
    } catch (ignored) { }
  }

  function flash(message) {
    if (!toast) return;
    toast.textContent = message || '';
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toast.classList.remove('show'); }, 1600);
  }

  function setNeed(prefix, value, forceBlack) {
    var card = document.getElementById(prefix + 'Card');
    var drain = document.getElementById(prefix + 'Drain');
    var level = clamp(value);
    if (!card || !drain) return;
    var height = 40 * (1 - level / 100);
    drain.setAttribute('height', String(height));
    drain.setAttribute('y', String(650 + 40 - height));
    drain.setAttribute('opacity', forceBlack || level <= 0 ? '.92' : '.62');
  }

  function pulseButton(node) {
    if (!node) return;
    node.classList.remove('btn-pop');
    void node.offsetWidth;
    node.classList.add('btn-pop');

    var rect = node.querySelector('rect.reference-action-button');
    if (rect) {
      var cx = Number(rect.getAttribute('x')) + Number(rect.getAttribute('width')) / 2;
      var cy = Number(rect.getAttribute('y')) + Number(rect.getAttribute('height')) / 2;
      var ripple = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
      ripple.setAttribute('cx', cx);
      ripple.setAttribute('cy', cy);
      ripple.setAttribute('r', 34);
      ripple.setAttribute('class', 'click-ripple');
      node.appendChild(ripple);
      setTimeout(function () { ripple.remove(); }, 520);
    }
  }

  function staggerWheelItems() {
    var items = wheel ? wheel.querySelectorAll('.wheel-item') : [];
    items.forEach(function (item, index) {
      item.style.animationDelay = (index * 35) + 'ms';
    });
  }

  function setWheelOpen(open) {
    if (!wheel) return;
    wheel.setAttribute('display', open ? 'inline' : 'none');
    if (open) {
      staggerWheelItems();
      wheel.classList.remove('opening');
      void wheel.offsetWidth;
      wheel.classList.add('opening');
      send('wheel:open', '{}');
    } else {
      wheel.classList.remove('opening');
      send('wheel:close', '{}');
    }
  }

  function render() {
    setNeed('health', state.dead ? 0 : state.health, !!state.dead);
    setNeed('armor', state.armour, false);
    setNeed('water', state.thirst, false);
    setNeed('food', state.hunger, false);
    setNeed('energy', state.energy, !!state.exhausted || state.energy <= 0);

    var vehicle = document.getElementById('vehicleHud');
    if (vehicle) vehicle.setAttribute('display', state.inVehicle ? 'inline' : 'none');
    var speed = document.getElementById('vehicleSpeed');
    var vehicleHealth = document.getElementById('vehicleHealth');
    var vehicleFuel = document.getElementById('vehicleFuel');
    if (speed) speed.textContent = String(Math.max(0, Math.round(Number(state.speed) || 0)));
    if (vehicleHealth) vehicleHealth.setAttribute('width', String(240 * clamp(state.vehHealth) / 100));
    if (vehicleFuel) vehicleFuel.setAttribute('width', String(240 * clamp(state.fuel) / 100));
  }

  function mergeState(next) {
    if (!next) return;
    if (typeof next === 'string') {
      try { next = JSON.parse(next); } catch (ignored) { return; }
    }
    Object.keys(next).forEach(function (key) { state[key] = next[key]; });
    render();
  }

  if (svg) {
    svg.addEventListener('click', function (event) {
      var node = event.target;
      while (node && node !== svg && !node.getAttribute('data-action') && !node.getAttribute('data-wheel')) node = node.parentNode;
      if (!node || node === svg) {
        if (wheel && wheel.getAttribute('display') !== 'none') setWheelOpen(false);
        return;
      }
      var action = node.getAttribute('data-action');
      var wheelAction = node.getAttribute('data-wheel');
      if (action === 'menu' || action === 'target' || action === 'chat') pulseButton(node);
      if (action === 'menu') { send('config:open', '{}'); return; }
      if (action === 'target') { setWheelOpen(!wheel || wheel.getAttribute('display') === 'none'); return; }
      if (action === 'chat') { send('chat:open', '{}'); return; }
      if (wheelAction) {
        node.classList.remove('btn-pop');
        void node.offsetWidth;
        node.classList.add('btn-pop');
        if (wheelAction === 'celular') {
          send('phone:open', '{}');
          setWheelOpen(false);
          return;
        }
        if (wheelAction === 'blazer') {
          send('blazer:open', '{}');
          setWheelOpen(false);
          return;
        }
        send('wheel:select', JSON.stringify({ item: wheelAction }));
        flash(wheelAction);
        setWheelOpen(false);
      }
    }, false);
  }

  window.HudBridge = {
    setPlayerState: mergeState,
    setVisible: function (visible) { if (svg) svg.style.display = visible === false ? 'none' : 'block'; },
    setWheelOpen: setWheelOpen,
    setVehicleMode: function (active) { mergeState({ inVehicle: !!active }); },
    reset: function () { state = { health: 100, armour: 0, thirst: 100, hunger: 100, energy: 100, dead: false, exhausted: false, inVehicle: false, speed: 0, vehHealth: 100, fuel: 100 }; render(); }
  };

  render();
}());
