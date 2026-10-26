#pragma once

// Double tap sur un joueur (dans le monde 3D) -> menu d'interaction du serveur (/interaction <id>).
// Appele depuis AND_TouchEvent_hook (game/hooks.cpp) ; n'intercepte pas la touche.
namespace PlayerTap
{
    void OnTouch(int type, int x, int y);
}
