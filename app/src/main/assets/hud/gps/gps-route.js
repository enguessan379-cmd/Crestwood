/*
 * Moteur d'itineraire du GPS Crestwood (style GTA V) : partage par la minimap (hud/minimap.js)
 * et la grande carte (map/index.html).
 *
 *   CrestGPS.load(callback)                 charge le graphe routier (gps/roads.js) une seule fois
 *   CrestGPS.route(x0, y0, x1, y1)          -> { pts: Float32Array [x,y,...], cum: Float32Array, len } ou null
 *   CrestGPS.progress(route, from, px, py)  -> { idx, px, py, off, left }   (avancement le long du trace)
 *
 * Le graphe vient du GPS.dat de SA-MP (noeuds de route du jeu, sens uniques respectes) : voir
 * tools/build_gps_roads.py. Recherche A* sur ~27 000 noeuds : quelques millisecondes.
 */
(function () {
  "use strict";
  if (window.CrestGPS) return;

  const CELL = 100;                 // grille de recherche du noeud le plus proche
  const HALF = 3000;
  const COLS = Math.ceil((HALF * 2) / CELL);

  const here = document.currentScript && document.currentScript.src ? document.currentScript.src : "";
  const base = here ? here.slice(0, here.lastIndexOf("/") + 1) : "gps/";

  let ready = false;
  let loading = false;
  const waiters = [];

  let N = 0;
  let X = null, Y = null;           // Float32Array
  let OFF = null;                   // Int32Array (N + 1) : debut des voisins
  let TO = null;                    // Uint16Array
  let cellStart = null, cellNodes = null;

  // Tableaux de travail de l'A* (reutilises, remis a zero par "generation")
  let gScore = null, parent = null, seen = null, closed = null, gen = 0;
  let heapN = null, heapK = null, heapSize = 0;

  function bytes(b64) {
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i += 1) out[i] = bin.charCodeAt(i);
    return out;
  }

  function decode() {
    const R = window.CREST_ROADS;
    if (!R) return false;
    N = R.n | 0;
    const xy = new Int16Array(bytes(R.xy).buffer);
    const deg = bytes(R.deg);
    TO = new Uint16Array(bytes(R.to).buffer);
    X = new Float32Array(N);
    Y = new Float32Array(N);
    OFF = new Int32Array(N + 1);
    for (let i = 0; i < N; i += 1) {
      X[i] = xy[i * 2] / 4;
      Y[i] = xy[i * 2 + 1] / 4;
      OFF[i + 1] = OFF[i] + deg[i];
    }
    // grille (tri par alveole)
    const cells = COLS * COLS;
    const count = new Int32Array(cells + 1);
    const cellOf = new Int32Array(N);
    for (let i = 0; i < N; i += 1) {
      const c = cellIndex(X[i], Y[i]);
      cellOf[i] = c;
      count[c + 1] += 1;
    }
    for (let c = 0; c < cells; c += 1) count[c + 1] += count[c];
    cellStart = count;
    cellNodes = new Int32Array(N);
    const fill = count.slice(0, cells);
    for (let i = 0; i < N; i += 1) cellNodes[fill[cellOf[i]]++] = i;

    gScore = new Float32Array(N);
    parent = new Int32Array(N);
    seen = new Uint32Array(N);
    closed = new Uint32Array(N);
    heapN = new Int32Array(4096);
    heapK = new Float32Array(4096);
    try { delete window.CREST_ROADS; } catch (_) { window.CREST_ROADS = undefined; }
    return true;
  }

  function cellIndex(x, y) {
    const cx = Math.max(0, Math.min(COLS - 1, Math.floor((x + HALF) / CELL)));
    const cy = Math.max(0, Math.min(COLS - 1, Math.floor((y + HALF) / CELL)));
    return cy * COLS + cx;
  }

  function load(cb) {
    if (ready) { if (cb) cb(true); return; }
    if (cb) waiters.push(cb);
    if (loading) return;
    loading = true;
    const done = (ok) => {
      ready = ok;
      loading = false;
      const list = waiters.splice(0, waiters.length);
      list.forEach((fn) => { try { fn(ok); } catch (_) { /* rien */ } });
    };
    if (window.CREST_ROADS) { done(decode()); return; }
    const s = document.createElement("script");
    s.src = base + "roads.js";
    s.async = true;
    s.onload = () => { let ok = false; try { ok = decode(); } catch (e) { console.log("[gps] graphe invalide " + e); } done(ok); };
    s.onerror = () => { console.log("[gps] roads.js introuvable"); done(false); };
    (document.head || document.documentElement).appendChild(s);
  }

  function nearest(x, y) {
    if (!ready) return -1;
    const cx = Math.max(0, Math.min(COLS - 1, Math.floor((x + HALF) / CELL)));
    const cy = Math.max(0, Math.min(COLS - 1, Math.floor((y + HALF) / CELL)));
    let best = -1, bestD = Infinity;
    for (let r = 0; r < COLS; r += 1) {
      // au-dela de cet anneau, rien ne peut etre plus proche que "best"
      if (best >= 0 && (r - 1) * CELL > Math.sqrt(bestD)) break;
      const x0 = cx - r, x1 = cx + r, y0 = cy - r, y1 = cy + r;
      for (let gy = y0; gy <= y1; gy += 1) {
        if (gy < 0 || gy >= COLS) continue;
        const edgeRow = gy === y0 || gy === y1;
        for (let gx = x0; gx <= x1; gx += edgeRow ? 1 : (x1 - x0 || 1)) {
          if (gx < 0 || gx >= COLS) continue;
          const c = gy * COLS + gx;
          for (let k = cellStart[c]; k < cellStart[c + 1]; k += 1) {
            const i = cellNodes[k];
            const dx = X[i] - x, dy = Y[i] - y;
            const d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; best = i; }
          }
        }
      }
    }
    return best;
  }

  function heapPush(n, k) {
    if (heapSize >= heapN.length) {
      const nn = new Int32Array(heapN.length * 2); nn.set(heapN); heapN = nn;
      const kk = new Float32Array(heapK.length * 2); kk.set(heapK); heapK = kk;
    }
    let i = heapSize++;
    while (i > 0) {
      const p = (i - 1) >> 1;
      if (heapK[p] <= k) break;
      heapN[i] = heapN[p]; heapK[i] = heapK[p];
      i = p;
    }
    heapN[i] = n; heapK[i] = k;
  }

  function heapPop() {
    const top = heapN[0];
    const n = heapN[--heapSize], k = heapK[heapSize];
    let i = 0;
    for (;;) {
      let c = i * 2 + 1;
      if (c >= heapSize) break;
      if (c + 1 < heapSize && heapK[c + 1] < heapK[c]) c += 1;
      if (heapK[c] >= k) break;
      heapN[i] = heapN[c]; heapK[i] = heapK[c];
      i = c;
    }
    heapN[i] = n; heapK[i] = k;
    return top;
  }

  function astar(s, t) {
    gen += 1;
    if (gen > 0xfffffff0) { gen = 1; seen.fill(0); closed.fill(0); }
    heapSize = 0;
    const tx = X[t], ty = Y[t];
    gScore[s] = 0; parent[s] = -1; seen[s] = gen;
    heapPush(s, Math.hypot(X[s] - tx, Y[s] - ty));
    let guard = 0;
    while (heapSize > 0) {
      const u = heapPop();
      if (closed[u] === gen) continue;
      closed[u] = gen;
      if (u === t) break;
      if (++guard > N * 3) return null;
      const gu = gScore[u], ux = X[u], uy = Y[u];
      for (let k = OFF[u]; k < OFF[u + 1]; k += 1) {
        const v = TO[k];
        if (closed[v] === gen) continue;
        const ng = gu + Math.hypot(X[v] - ux, Y[v] - uy);
        if (seen[v] !== gen || ng < gScore[v]) {
          seen[v] = gen; gScore[v] = ng; parent[v] = u;
          heapPush(v, ng + Math.hypot(X[v] - tx, Y[v] - ty));
        }
      }
    }
    if (closed[t] !== gen) return null;
    const path = [];
    for (let v = t; v !== -1; v = parent[v]) path.push(v);
    path.reverse();
    return path;
  }

  // Simplification (Ramer-Douglas-Peucker) : moins de points a dessiner, meme forme.
  function simplify(xs, ys, eps) {
    const n = xs.length;
    if (n < 3) return xs.map((_, i) => i);
    const keep = new Uint8Array(n);
    keep[0] = keep[n - 1] = 1;
    const stack = [0, n - 1];
    while (stack.length) {
      const b = stack.pop(), a = stack.pop();
      const ax = xs[a], ay = ys[a], dx = xs[b] - ax, dy = ys[b] - ay;
      const len2 = dx * dx + dy * dy || 1e-6;
      let far = -1, farD = eps * eps;
      for (let i = a + 1; i < b; i += 1) {
        const t = Math.max(0, Math.min(1, ((xs[i] - ax) * dx + (ys[i] - ay) * dy) / len2));
        const ex = ax + t * dx - xs[i], ey = ay + t * dy - ys[i];
        const d = ex * ex + ey * ey;
        if (d > farD) { farD = d; far = i; }
      }
      if (far >= 0) { keep[far] = 1; stack.push(a, far, far, b); }
    }
    const out = [];
    for (let i = 0; i < n; i += 1) if (keep[i]) out.push(i);
    return out;
  }

  function build(xs, ys) {
    const idx = simplify(xs, ys, 1.5);
    const pts = new Float32Array(idx.length * 2);
    const cum = new Float32Array(idx.length);
    for (let i = 0; i < idx.length; i += 1) {
      pts[i * 2] = xs[idx[i]];
      pts[i * 2 + 1] = ys[idx[i]];
      if (i > 0) cum[i] = cum[i - 1] + Math.hypot(pts[i * 2] - pts[i * 2 - 2], pts[i * 2 + 1] - pts[i * 2 - 1]);
    }
    return { pts, cum, len: cum[cum.length - 1] || 0 };
  }

  function route(x0, y0, x1, y1) {
    if (!ready || !isFinite(x0) || !isFinite(y0) || !isFinite(x1) || !isFinite(y1)) return null;
    const s = nearest(x0, y0);
    const t = nearest(x1, y1);
    if (s < 0 || t < 0) return null;
    const xs = [x0], ys = [y0];
    if (s !== t) {
      const path = astar(s, t);
      if (!path) return null;
      for (let i = 0; i < path.length; i += 1) { xs.push(X[path[i]]); ys.push(Y[path[i]]); }
    } else {
      xs.push(X[s]); ys.push(Y[s]);
    }
    xs.push(x1); ys.push(y1);
    return build(xs, ys);
  }

  /*
   * Avancement sur le trace a partir du segment "from" : renvoie le segment courant (idx), le point
   * projete (px, py), l'ecart au trace (off) et la distance restante (left). On ne cherche qu'en avant
   * (quelques segments) pour ne pas "sauter" sur une portion parallele de l'itineraire.
   */
  function progress(r, from, px, py) {
    const pts = r.pts, n = pts.length / 2;
    if (n < 2) return { idx: 0, px: pts[0], py: pts[1], off: Math.hypot(pts[0] - px, pts[1] - py), left: 0 };
    let best = Math.max(0, Math.min(n - 2, from | 0)), bestD = Infinity, bestT = 0;
    const end = Math.min(n - 2, best + 25);
    for (let i = Math.max(0, best - 1); i <= end; i += 1) {
      const ax = pts[i * 2], ay = pts[i * 2 + 1];
      const dx = pts[i * 2 + 2] - ax, dy = pts[i * 2 + 3] - ay;
      const len2 = dx * dx + dy * dy || 1e-6;
      const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
      const ex = ax + t * dx - px, ey = ay + t * dy - py;
      const d = ex * ex + ey * ey;
      if (d < bestD - 0.01) { bestD = d; best = i; bestT = t; }
    }
    const ax = pts[best * 2], ay = pts[best * 2 + 1];
    const qx = ax + (pts[best * 2 + 2] - ax) * bestT;
    const qy = ay + (pts[best * 2 + 3] - ay) * bestT;
    const seg = r.cum[best + 1] - r.cum[best];
    const left = Math.max(0, r.len - (r.cum[best] + seg * bestT));
    return { idx: best, px: qx, py: qy, off: Math.sqrt(bestD), left };
  }

  /* Distance lisible : "350 m" / "1,2 km" (1 unite GTA ~ 1 metre). */
  function formatDistance(d) {
    if (!isFinite(d)) return "";
    if (d < 1000) return Math.max(0, Math.round(d / 10) * 10) + " m";
    return (d / 1000).toFixed(d < 10000 ? 1 : 0).replace(".", ",") + " km";
  }

  window.CrestGPS = {
    load,
    isReady: () => ready,
    route,
    progress,
    nearest,
    formatDistance
  };
})();
