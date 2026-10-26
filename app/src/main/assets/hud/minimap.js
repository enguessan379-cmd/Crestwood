/*
 * Minimap Crestwood (remplace le radar natif de GTA San Andreas).
 *
 * Données reçues du jeu (via HudBridge.setPlayerState, ~10 fois par seconde) :
 *   posX, posY      position du joueur (unités GTA, X = est, Y = nord)
 *   camFx, camFy    direction de la caméra (vecteur avant, plan X/Y)
 *   heading         cap du joueur ou du véhicule en radians (convention GTA : atan2(-fx, fy))
 *   inVehicle, speed  pour le zoom automatique
 *
 * La carte tourne avec la caméra : le haut du cadre est toujours la direction où l'on regarde.
 * Les tuiles viennent de radar/tiles/radar0..143.png : grille 12 x 12, 500 unités par tuile,
 * monde de -3000 à +3000, ligne 0 = nord, colonne 0 = ouest.
 *
 * API supplémentaire (pour brancher plus tard les blips/GPS venant du serveur) :
 *   MinimapBridge.setBlips([{ id, x, y, icon }])   icon = numéro de radar/blips/blip_<icon>.png
 *   MinimapBridge.setWaypoint(x, y, auto) / clearWaypoint()   auto = efface a l'arrivee (point choisi sur la carte)
 *   MinimapBridge.setZoom(unitesVisibles)           (null = zoom automatique)
 */
(function () {
  "use strict";

  const GRID = 12;
  const TILE_WORLD = 500;
  const WORLD_HALF = 3000;
  const MAX_CACHED_TILES = 40;
  const SEA_COLOR = "#0b0b0d";   // fond de la carte sombre (assets/hud/radar/tiles)
  const FOOT_SPAN = 260;      // unités visibles sur la largeur du cadre, à pied
  const VEHICLE_SPAN = 260;   // base en véhicule, + vitesse
  const SPEED_SPAN_GAIN = 1.6;

  const frame = document.querySelector(".minimap-frame");
  const canvas = frame && frame.querySelector(".minimap-canvas");
  if (!frame || !canvas) return;
  const ctx = canvas.getContext("2d");

  const tiles = new Map();   // index -> { img, ready, used }
  const icons = new Map();   // chemin -> { img, ready }
  let blips = [];
  let waypoint = null;
  let manualSpan = null;
  let frameCounter = 0;
  let lastTime = 0;
  let running = false;

  const target = { x: 0, y: 0, fx: 0, fy: 1, hx: 0, hy: 1, span: FOOT_SPAN };
  const cur = { x: 0, y: 0, fx: 0, fy: 1, hx: 0, hy: 1, span: FOOT_SPAN };
  let hasData = false;

  function loadImage(path, store) {
    let entry = store.get(path);
    if (!entry) {
      const img = new Image();
      entry = { img, ready: false, used: 0 };
      img.onload = () => { entry.ready = true; };
      img.onerror = () => { entry.failed = true; };
      img.src = path;
      store.set(path, entry);
    }
    return entry;
  }

  function tile(index) {
    const entry = loadImage("radar/tiles/radar" + index + ".png", tiles);
    entry.used = frameCounter;
    return entry;
  }

  function evictTiles() {
    if (tiles.size <= MAX_CACHED_TILES) return;
    const sorted = Array.from(tiles.entries()).sort((a, b) => a[1].used - b[1].used);
    for (let i = 0; i < sorted.length - MAX_CACHED_TILES; i += 1) {
      sorted[i][1].img.src = "";
      tiles.delete(sorted[i][0]);
    }
  }

  function resizeCanvas() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = Math.max(1, Math.round(frame.clientWidth * dpr));
    const h = Math.max(1, Math.round(frame.clientHeight * dpr));
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
    }
    return dpr;
  }

  function normalize(vx, vy, fallbackX, fallbackY) {
    const len = Math.hypot(vx, vy);
    if (len < 1e-4) return [fallbackX, fallbackY];
    return [vx / len, vy / len];
  }

  function update(state) {
    if (!state || typeof state !== "object") return;
    if (typeof state.posX !== "number" || typeof state.posY !== "number") return;
    if (!isFinite(state.posX) || !isFinite(state.posY)) return;

    target.x = state.posX;
    target.y = state.posY;
    if (typeof state.camFx === "number" && typeof state.camFy === "number") {
      const f = normalize(state.camFx, state.camFy, target.fx, target.fy);
      target.fx = f[0];
      target.fy = f[1];
    }
    if (typeof state.heading === "number" && isFinite(state.heading)) {
      target.hx = -Math.sin(state.heading);
      target.hy = Math.cos(state.heading);
    }
    const speed = Math.max(0, Number(state.speed) || 0);
    target.span = state.inVehicle
      ? VEHICLE_SPAN + Math.min(speed, 200) * SPEED_SPAN_GAIN
      : FOOT_SPAN;

    if (!hasData) {
      // Premier paquet : pas d'animation depuis (0, 0).
      Object.assign(cur, target);
      hasData = true;
      frame.dataset.ready = "1";
    } else if (Math.hypot(target.x - cur.x, target.y - cur.y) > 120) {
      // Téléportation : on saute directement à la nouvelle position.
      cur.x = target.x;
      cur.y = target.y;
    }
    start();
  }

  function step(dt) {
    const k = 1 - Math.exp(-dt * 12);
    cur.x += (target.x - cur.x) * k;
    cur.y += (target.y - cur.y) * k;
    const f = normalize(cur.fx + (target.fx - cur.fx) * k, cur.fy + (target.fy - cur.fy) * k, target.fx, target.fy);
    cur.fx = f[0];
    cur.fy = f[1];
    const h = normalize(cur.hx + (target.hx - cur.hx) * k, cur.hy + (target.hy - cur.hy) * k, target.hx, target.hy);
    cur.hx = h[0];
    cur.hy = h[1];
    const goalSpan = manualSpan || target.span;
    cur.span += (goalSpan - cur.span) * (1 - Math.exp(-dt * 3));
  }

  function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }

  function drawTiles(w, h, s) {
    const fx = cur.fx;
    const fy = cur.fy;
    const rx = fy;
    const ry = -fx;
    // Monde (X est, V = -Y sud) -> écran, avec le haut de l'écran = direction de la caméra.
    ctx.setTransform(s * rx, -s * fx, -s * ry, s * fy,
      w / 2 - s * (rx * cur.x + ry * cur.y),
      h / 2 + s * (fx * cur.x + fy * cur.y));

    const radius = cur.span * 0.75;   // un peu plus que la demi-diagonale
    const pad = radius * 0.35;        // préchargement des tuiles voisines
    const col0 = clamp(Math.floor((cur.x - radius - pad + WORLD_HALF) / TILE_WORLD), 0, GRID - 1);
    const col1 = clamp(Math.floor((cur.x + radius + pad + WORLD_HALF) / TILE_WORLD), 0, GRID - 1);
    const row0 = clamp(Math.floor((WORLD_HALF - (cur.y + radius + pad)) / TILE_WORLD), 0, GRID - 1);
    const row1 = clamp(Math.floor((WORLD_HALF - (cur.y - radius - pad)) / TILE_WORLD), 0, GRID - 1);

    const visCol0 = clamp(Math.floor((cur.x - radius + WORLD_HALF) / TILE_WORLD), 0, GRID - 1);
    const visCol1 = clamp(Math.floor((cur.x + radius + WORLD_HALF) / TILE_WORLD), 0, GRID - 1);
    const visRow0 = clamp(Math.floor((WORLD_HALF - (cur.y + radius)) / TILE_WORLD), 0, GRID - 1);
    const visRow1 = clamp(Math.floor((WORLD_HALF - (cur.y - radius)) / TILE_WORLD), 0, GRID - 1);

    ctx.imageSmoothingEnabled = true;
    for (let row = row0; row <= row1; row += 1) {
      for (let col = col0; col <= col1; col += 1) {
        const entry = tile(row * GRID + col);
        const visible = col >= visCol0 && col <= visCol1 && row >= visRow0 && row <= visRow1;
        if (!visible || !entry.ready) continue;
        // +0.6 : léger recouvrement pour éviter les coutures entre tuiles.
        ctx.drawImage(entry.img, col * TILE_WORLD - WORLD_HALF, row * TILE_WORLD - WORLD_HALF, TILE_WORLD + 0.6, TILE_WORLD + 0.6);
      }
    }
  }

  // Position écran (pixels canvas) d'un point du monde ; renvoie aussi s'il est hors cadre.
  function project(wx, wy, w, h, s) {
    const dx = wx - cur.x;
    const dy = wy - cur.y;
    const sx = (dx * cur.fy + dy * -cur.fx) * s;
    const sy = -(dx * cur.fx + dy * cur.fy) * s;
    return { x: sx, y: sy };
  }

  function drawIcon(entry, px, py, size) {
    if (!entry || !entry.ready) return false;
    ctx.drawImage(entry.img, px - size / 2, py - size / 2, size, size);
    return true;
  }

  function drawMarkers(w, h, s, dpr) {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    const half = w / 2;
    const margin = 9 * dpr;

    const place = (wx, wy) => {
      const p = project(wx, wy, w, h, s);
      const lim = half - margin;
      const m = Math.max(Math.abs(p.x), Math.abs(p.y));
      if (m > lim) {
        p.x *= lim / m;
        p.y *= lim / m;
        p.edge = true;
      }
      return { x: half + p.x, y: h / 2 + p.y, edge: !!p.edge };
    };

    for (const blip of blips) {
      const pos = place(blip.x, blip.y);
      const entry = loadImage("radar/blips/blip_" + blip.icon + ".png", icons);
      ctx.globalAlpha = pos.edge ? 0.75 : 1;
      drawIcon(entry, pos.x, pos.y, 16 * dpr);
    }
    ctx.globalAlpha = 1;

    // Point GPS choisi sur la carte : efface a l'arrivee (les points du serveur sont geres par le serveur)
    if (waypoint && waypoint.auto && hasData && Math.hypot(waypoint.x - target.x, waypoint.y - target.y) < 30) {
      waypoint = null;
      try { window.AndroidHUD && window.AndroidHUD.onHudAction && window.AndroidHUD.onHudAction("gps:arrived", "{}"); } catch (_) { /* aperçu */ }
    }
    if (waypoint) {
      const pos = place(waypoint.x, waypoint.y);
      drawIcon(loadImage("radar/cross.png", icons), pos.x, pos.y, 18 * dpr);
    }

    // Flèche du joueur : angle du cap par rapport au haut de l'écran (= direction de la caméra).
    const rx = cur.fy;
    const ry = -cur.fx;
    const angle = Math.atan2(cur.hx * rx + cur.hy * ry, cur.hx * cur.fx + cur.hy * cur.fy);
    ctx.save();
    ctx.translate(half, h / 2);
    ctx.rotate(angle);
    const arrow = loadImage("radar/blips/arrow.png", icons);
    if (arrow.ready) {
      const aw = 14 * dpr;
      ctx.drawImage(arrow.img, -aw / 2, -aw / 2, aw, aw * arrow.img.naturalHeight / arrow.img.naturalWidth);
    } else {
      ctx.fillStyle = "#12dfff";
      ctx.beginPath();
      ctx.moveTo(0, -7 * dpr);
      ctx.lineTo(5 * dpr, 6 * dpr);
      ctx.lineTo(0, 3 * dpr);
      ctx.lineTo(-5 * dpr, 6 * dpr);
      ctx.closePath();
      ctx.fill();
    }
    ctx.restore();

    // Repère du nord, sur le bord du cadre.
    const nx = -cur.fx;
    const ny = -cur.fy;
    const t = (half - 8 * dpr) / Math.max(Math.abs(nx), Math.abs(ny), 1e-4);
    ctx.font = "700 " + Math.round(9 * dpr) + "px sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.lineWidth = 3 * dpr;
    ctx.strokeStyle = "rgba(0,0,0,0.75)";
    ctx.fillStyle = "#ffffff";
    ctx.strokeText("N", half + nx * t, h / 2 + ny * t);
    ctx.fillText("N", half + nx * t, h / 2 + ny * t);
  }

  function render() {
    if (!hasData || frame.offsetParent === null) return;
    const dpr = resizeCanvas();
    const w = canvas.width;
    const h = canvas.height;
    const s = w / cur.span;

    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.fillStyle = SEA_COLOR;
    ctx.fillRect(0, 0, w, h);
    drawTiles(w, h, s);
    drawMarkers(w, h, s, dpr);
    frameCounter += 1;
    if (frameCounter % 120 === 0) evictTiles();
  }

  function loop(time) {
    if (!running) return;
    const dt = Math.min(0.1, Math.max(0.001, (time - lastTime) / 1000 || 0.016));
    lastTime = time;
    if (!document.hidden) {
      step(dt);
      render();
    }
    window.requestAnimationFrame(loop);
  }

  function start() {
    if (running) return;
    running = true;
    lastTime = performance.now();
    window.requestAnimationFrame(loop);
  }

  window.addEventListener("resize", () => { if (hasData) render(); });

  window.Minimap = { update };

  window.MinimapBridge = {
    setBlips(list) {
      if (typeof list === "string") {
        try { list = JSON.parse(list); } catch (_) { return; }
      }
      blips = Array.isArray(list)
        ? list.filter((b) => b && isFinite(b.x) && isFinite(b.y)).map((b) => ({ id: b.id, x: +b.x, y: +b.y, icon: Number(b.icon) || 0 }))
        : [];
    },
    setWaypoint(x, y, auto) {
      waypoint = isFinite(x) && isFinite(y) ? { x: +x, y: +y, auto: !!auto } : null;
    },
    clearWaypoint() { waypoint = null; },
    setZoom(span) {
      manualSpan = span && isFinite(span) ? clamp(+span, 80, 1200) : null;
    }
  };
})();
