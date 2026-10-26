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
 * GPS façon GTA V : dès qu'une destination existe (mission du serveur ou point placé sur la carte),
 * l'itinéraire est calculé sur le vrai réseau routier (gps/gps-route.js) et tracé en violet.
 * Il se raccourcit au fur et à mesure et se recalcule si l'on quitte la route.
 *
 * API :
 *   MinimapBridge.setBlips([{ id, x, y, icon }])   icon = numéro de radar/blips/blip_<icon>.png
 *   MinimapBridge.setWaypoint(x, y, auto) / clearWaypoint()   auto = efface a l'arrivee (point choisi sur la carte)
 *   MinimapBridge.setZoom(unitesVisibles)           (null = zoom automatique)
 *
 * Optimisation : le dessin s'arrête tout seul quand rien ne bouge (aucune image calculée pour rien),
 * et il est limité à ~40 images par seconde.
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
  const MIN_FRAME_MS = 24;    // ~40 images / s maximum
  const ROUTE_COLOR = "#b44dff";        // violet GTA V
  const ROUTE_EDGE = "rgba(28, 6, 44, 0.9)";
  const OFF_ROUTE = 32;       // unités hors du tracé avant recalcul
  const RECALC_MS = 1500;     // délai mini entre deux recalculs
  const ARRIVED = 30;         // distance d'arrivée (point placé sur la carte)

  const frame = document.querySelector(".minimap-frame");
  const canvas = frame && frame.querySelector(".minimap-canvas");
  if (!frame || !canvas) return;
  const ctx = canvas.getContext("2d", { alpha: false }) || canvas.getContext("2d");

  const tiles = new Map();   // index -> { img, ready, used }
  const icons = new Map();   // chemin -> { img, ready }
  let blips = [];
  let waypoint = null;
  let manualSpan = null;
  let frameCounter = 0;
  let lastTime = 0;
  let lastDraw = 0;
  let running = false;
  let dirty = true;

  // Itinéraire
  let route = null;          // { pts, cum, len }
  let routeIdx = 0;
  let routeLeft = 0;
  let routeProj = null;      // point projeté du joueur sur le tracé
  let routeAt = 0;
  let routePending = false;

  const target = { x: 0, y: 0, fx: 0, fy: 1, hx: 0, hy: 1, span: FOOT_SPAN };
  const cur = { x: 0, y: 0, fx: 0, fy: 1, hx: 0, hy: 1, span: FOOT_SPAN };
  let hasData = false;

  function markDirty() { dirty = true; start(); }

  function loadImage(path, store) {
    let entry = store.get(path);
    if (!entry) {
      const img = new Image();
      entry = { img, ready: false, used: 0 };
      img.onload = () => { entry.ready = true; markDirty(); };
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

  let cachedDpr = 1;
  function resizeCanvas() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = Math.max(1, Math.round(frame.clientWidth * dpr));
    const h = Math.max(1, Math.round(frame.clientHeight * dpr));
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
    }
    cachedDpr = dpr;
    return dpr;
  }

  function normalize(vx, vy, fallbackX, fallbackY) {
    const len = Math.hypot(vx, vy);
    if (len < 1e-4) return [fallbackX, fallbackY];
    return [vx / len, vy / len];
  }

  /* ------------------------------------------------------------------ itinéraire */
  function gps() { return window.CrestGPS || null; }

  function requestRoute(force) {
    if (!waypoint || !hasData) return;
    const now = performance.now();
    if (!force && now - routeAt < RECALC_MS) return;
    if (routePending) return;
    const G = gps();
    if (!G) return;
    routePending = true;
    routeAt = now;
    G.load((ok) => {
      // calcul hors de l'appel du pont natif (ne bloque pas la mise à jour du HUD)
      window.setTimeout(() => {
        routePending = false;
        if (!waypoint) return;
        let r = ok ? G.route(target.x, target.y, waypoint.x, waypoint.y) : null;
        if (!r) {
          // pas de route trouvée (intérieur, île...) : ligne droite
          const pts = new Float32Array([target.x, target.y, waypoint.x, waypoint.y]);
          const len = Math.hypot(waypoint.x - target.x, waypoint.y - target.y);
          r = { pts, cum: new Float32Array([0, len]), len, straight: true };
        }
        route = r;
        routeIdx = 0;
        routeAt = performance.now();
        trackRoute();
        markDirty();
      }, 0);
    });
  }

  function clearRoute() {
    route = null;
    routeIdx = 0;
    routeLeft = 0;
    routeProj = null;
    markDirty();
  }

  function trackRoute() {
    if (!waypoint) return;
    if (!route) { requestRoute(true); return; }
    const G = gps();
    if (!G) return;
    const p = G.progress(route, routeIdx, target.x, target.y);
    routeIdx = p.idx;
    routeProj = p;
    routeLeft = p.left + (p.off > 8 ? p.off : 0);
    if (!route.straight && p.off > OFF_ROUTE) requestRoute(false);
    else if (route.straight && Math.hypot(route.pts[0] - target.x, route.pts[1] - target.y) > 80) requestRoute(false);
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
      // Téléportation : on saute directement à la nouvelle position (et on refait l'itinéraire).
      cur.x = target.x;
      cur.y = target.y;
      if (waypoint) requestRoute(true);
    }

    if (waypoint) {
      // Point placé sur la carte : effacé à l'arrivée (les points du serveur sont gérés par le serveur)
      if (waypoint.auto && Math.hypot(waypoint.x - target.x, waypoint.y - target.y) < ARRIVED) {
        waypoint = null;
        clearRoute();
        try { window.AndroidHUD && window.AndroidHUD.onHudAction && window.AndroidHUD.onHudAction("gps:arrived", "{}"); } catch (_) { /* aperçu */ }
      } else {
        trackRoute();
      }
    }
    markDirty();
  }

  function step(dt) {
    const k = 1 - Math.exp(-dt * 12);
    const before = Math.abs(target.x - cur.x) + Math.abs(target.y - cur.y)
      + Math.abs(target.fx - cur.fx) + Math.abs(target.fy - cur.fy)
      + Math.abs(target.hx - cur.hx) + Math.abs(target.hy - cur.hy);
    cur.x += (target.x - cur.x) * k;
    cur.y += (target.y - cur.y) * k;
    const f = normalize(cur.fx + (target.fx - cur.fx) * k, cur.fy + (target.fy - cur.fy) * k, target.fx, target.fy);
    cur.fx = f[0];
    cur.fy = f[1];
    const h = normalize(cur.hx + (target.hx - cur.hx) * k, cur.hy + (target.hy - cur.hy) * k, target.hx, target.hy);
    cur.hx = h[0];
    cur.hy = h[1];
    const goalSpan = manualSpan || target.span;
    const spanGap = goalSpan - cur.span;
    cur.span += spanGap * (1 - Math.exp(-dt * 3));
    // encore en mouvement ?
    return before > 0.02 || Math.abs(spanGap) > 0.5;
  }

  function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }

  function setWorldTransform(w, h, s) {
    const fx = cur.fx;
    const fy = cur.fy;
    const rx = fy;
    const ry = -fx;
    // Monde (X est, V = -Y sud) -> écran, avec le haut de l'écran = direction de la caméra.
    ctx.setTransform(s * rx, -s * fx, -s * ry, s * fy,
      w / 2 - s * (rx * cur.x + ry * cur.y),
      h / 2 + s * (fx * cur.x + fy * cur.y));
  }

  function drawTiles(w, h, s) {
    setWorldTransform(w, h, s);
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

  // Tracé violet (repère du monde) : du joueur jusqu'à la destination, partie déjà parcourue retirée.
  function drawRoute(s, dpr) {
    if (!route || !waypoint) return;
    const pts = route.pts;
    const n = pts.length / 2;
    if (n < 2) return;
    const px = 1 / s;   // 1 pixel du canvas en unités du monde
    const reach = cur.span * 0.9;
    ctx.beginPath();
    // repère des tuiles : (X, V) avec V = -Y
    ctx.moveTo(cur.x, -cur.y);
    if (routeProj && !route.straight) ctx.lineTo(routeProj.px, -routeProj.py);
    let last = Math.min(n - 1, routeIdx + 1);
    for (let i = last; i < n; i += 1) {
      ctx.lineTo(pts[i * 2], -pts[i * 2 + 1]);
      last = i;
      // on arrête le tracé un peu après la sortie du cadre (inutile de dessiner toute la ville)
      if (Math.abs(pts[i * 2] - cur.x) > reach * 2.5 || Math.abs(pts[i * 2 + 1] - cur.y) > reach * 2.5) break;
    }
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.strokeStyle = ROUTE_EDGE;
    ctx.lineWidth = 8.5 * dpr * px;
    ctx.stroke();
    ctx.strokeStyle = route.straight ? "rgba(180, 77, 255, 0.8)" : ROUTE_COLOR;
    ctx.lineWidth = 5 * dpr * px;
    if (route.straight) ctx.setLineDash([10 * dpr * px, 7 * dpr * px]);
    ctx.stroke();
    ctx.setLineDash([]);
  }

  // Position écran (pixels canvas) d'un point du monde, relative au centre.
  function project(wx, wy, s) {
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

  // Repère de destination violet (style GTA V), même en bord de cadre.
  function drawWaypointMarker(x, y, dpr, edge) {
    const r = (edge ? 5.5 : 6.5) * dpr;
    ctx.beginPath();
    ctx.arc(x, y, r + 2 * dpr, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(20, 4, 32, 0.9)";
    ctx.fill();
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fillStyle = ROUTE_COLOR;
    ctx.fill();
    ctx.beginPath();
    ctx.arc(x, y, r * 0.38, 0, Math.PI * 2);
    ctx.fillStyle = "#ffffff";
    ctx.fill();
  }

  function drawMarkers(w, h, s, dpr) {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    const half = w / 2;
    // Cadre rond : les repères hors champ sont posés sur le cercle.
    const limit = half - 9 * dpr;

    const place = (wx, wy) => {
      const p = project(wx, wy, s);
      const m = Math.hypot(p.x, p.y);
      let edge = false;
      if (m > limit) {
        p.x *= limit / m;
        p.y *= limit / m;
        edge = true;
      }
      return { x: half + p.x, y: h / 2 + p.y, edge };
    };

    for (const blip of blips) {
      const pos = place(blip.x, blip.y);
      const entry = loadImage("radar/blips/blip_" + blip.icon + ".png", icons);
      ctx.globalAlpha = pos.edge ? 0.75 : 1;
      drawIcon(entry, pos.x, pos.y, 16 * dpr);
    }
    ctx.globalAlpha = 1;

    if (waypoint) {
      const pos = place(waypoint.x, waypoint.y);
      drawWaypointMarker(pos.x, pos.y, dpr, pos.edge);
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

    // Repère du nord, sur le bord du cercle.
    const t = half - 8 * dpr;
    const nx = -cur.fx * t;
    const ny = -cur.fy * t;
    ctx.font = "700 " + Math.round(9 * dpr) + "px sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.lineWidth = 3 * dpr;
    ctx.strokeStyle = "rgba(0,0,0,0.75)";
    ctx.fillStyle = "#ffffff";
    ctx.strokeText("N", half + nx, h / 2 + ny);
    ctx.fillText("N", half + nx, h / 2 + ny);

    // Distance restante jusqu'à la destination (en bas du cadre).
    if (waypoint && route && window.CrestGPS) {
      const label = window.CrestGPS.formatDistance(routeLeft);
      if (label) {
        ctx.font = "800 " + Math.round(9.5 * dpr) + "px sans-serif";
        const tw = ctx.measureText(label).width + 12 * dpr;
        const th = 15 * dpr;
        const bx = half - tw / 2;
        const by = h - th - 7 * dpr;
        ctx.fillStyle = "rgba(20, 4, 32, 0.82)";
        roundRect(bx, by, tw, th, th / 2);
        ctx.fill();
        ctx.fillStyle = "#e7c8ff";
        ctx.fillText(label, half, by + th / 2 + 0.5);
      }
    }
  }

  function roundRect(x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
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
    drawRoute(s, dpr);
    drawMarkers(w, h, s, dpr);
    frameCounter += 1;
    if (frameCounter % 120 === 0) evictTiles();
  }

  function loop(time) {
    if (!running) return;
    if (document.hidden) { running = false; return; }
    if (time - lastDraw < MIN_FRAME_MS) { window.requestAnimationFrame(loop); return; }
    const dt = Math.min(0.1, Math.max(0.001, (time - lastTime) / 1000 || 0.016));
    lastTime = time;
    lastDraw = time;
    const moving = step(dt);
    if (moving || dirty) {
      dirty = false;
      render();
    }
    if (!moving && !dirty) {
      // plus rien ne bouge : on s'arrête jusqu'au prochain paquet du jeu
      running = false;
      return;
    }
    window.requestAnimationFrame(loop);
  }

  function start() {
    if (running || !hasData) return;
    running = true;
    lastTime = performance.now();
    window.requestAnimationFrame(loop);
  }

  window.addEventListener("resize", markDirty);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) markDirty(); });

  window.Minimap = { update };

  window.MinimapBridge = {
    setBlips(list) {
      if (typeof list === "string") {
        try { list = JSON.parse(list); } catch (_) { return; }
      }
      blips = Array.isArray(list)
        ? list.filter((b) => b && isFinite(b.x) && isFinite(b.y)).map((b) => ({ id: b.id, x: +b.x, y: +b.y, icon: Number(b.icon) || 0 }))
        : [];
      markDirty();
    },
    setWaypoint(x, y, auto) {
      if (!isFinite(x) || !isFinite(y)) { this.clearWaypoint(); return; }
      const same = waypoint && Math.abs(waypoint.x - x) < 0.5 && Math.abs(waypoint.y - y) < 0.5;
      waypoint = { x: +x, y: +y, auto: !!auto };
      if (!same) { route = null; routeIdx = 0; routeProj = null; }
      requestRoute(true);
      markDirty();
    },
    clearWaypoint() { waypoint = null; clearRoute(); },
    setZoom(span) {
      manualSpan = span && isFinite(span) ? clamp(+span, 80, 1200) : null;
      markDirty();
    },
    /* Distance restante (unités) ou -1 sans GPS : utile pour d'autres éléments du HUD. */
    distanceLeft() { return waypoint && route ? routeLeft : -1; }
  };
})();
