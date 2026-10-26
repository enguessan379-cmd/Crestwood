#!/usr/bin/env python3
"""Genere app/src/main/assets/hud/gps/roads.js a partir d'un GPS.dat (format RouteConnector).

Source utilisee : GPS.dat "fixed by NaS" (noeuds de route de GTA San Andreas, distribue avec
le plugin GPS de SA-MP / RouteConnector de Gamer_Z).

Format GPS.dat :
  0 x y z -1 id        -> noeud
  1 de vers sens       -> connexion orientee (sens 2 = interdite, ignoree)

Sortie (JS charge a la demande par gps-route.js) :
  window.CREST_ROADS = { n, m, xy, deg, to }   (base64, little endian)
    xy  : Int16[n*2]  coordonnees * 4 (precision 0.25 unite)
    deg : Uint8[n]    nombre de voisins de chaque noeud
    to  : Uint16[m]   voisins, ranges noeud par noeud
Seule la plus grande composante connexe est gardee (pas d'ilots isoles).

Usage : python3 tools/build_gps_roads.py GPS.dat app/src/main/assets/hud/gps/roads.js
"""
import base64
import collections
import math
import struct
import sys


def main(src, dst):
    nodes = {}
    edges = set()
    with open(src, "r", encoding="latin-1") as fh:
        for line in fh:
            p = line.split()
            if len(p) < 4:
                continue
            if p[0] == "0" and len(p) >= 6:
                nodes[int(p[5])] = (float(p[1]), float(p[2]))
            elif p[0] == "1":
                a, b, d = int(p[1]), int(p[2]), int(p[3])
                if d != 2 and a != b:
                    edges.add((a, b))

    edges = {(a, b) for a, b in edges if a in nodes and b in nodes}
    und = collections.defaultdict(set)
    for a, b in edges:
        und[a].add(b)
        und[b].add(a)
    comp = {}
    best, best_size = None, 0
    for start in nodes:
        if start in comp:
            continue
        comp[start] = start
        stack, size = [start], 1
        while stack:
            u = stack.pop()
            for v in und[u]:
                if v not in comp:
                    comp[v] = start
                    stack.append(v)
                    size += 1
        if size > best_size:
            best, best_size = start, size

    keep = sorted(n for n in nodes if comp[n] == best)
    # ordre spatial (meilleure localite memoire pendant la recherche)
    keep.sort(key=lambda n: (int((nodes[n][1] + 3000) // 250), nodes[n][0]))
    index = {n: i for i, n in enumerate(keep)}
    adj = [[] for _ in keep]
    for a, b in edges:
        if a in index and b in index:
            adj[index[a]].append(index[b])

    xy = bytearray()
    for n in keep:
        x, y = nodes[n]
        xy += struct.pack("<hh", int(round(x * 4)), int(round(y * 4)))
    deg = bytes(min(255, len(a)) for a in adj)
    to = bytearray()
    m = 0
    for a in adj:
        for b in a[:255]:
            to += struct.pack("<H", b)
            m += 1

    b64 = lambda raw: base64.b64encode(bytes(raw)).decode("ascii")
    with open(dst, "w", encoding="utf-8") as out:
        out.write("/* Graphe routier de GTA San Andreas pour le GPS Crestwood (genere par tools/build_gps_roads.py). */\n")
        out.write("window.CREST_ROADS={n:%d,m:%d,\n" % (len(keep), m))
        out.write('xy:"%s",\n' % b64(xy))
        out.write('deg:"%s",\n' % b64(deg))
        out.write('to:"%s"};\n' % b64(to))
    print("noeuds", len(keep), "arcs", m, "->", dst)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1], sys.argv[2])
