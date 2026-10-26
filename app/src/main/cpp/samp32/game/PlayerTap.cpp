// Double tap sur un autre joueur -> envoie "/interaction <id>" au serveur, qui ouvre la page
// assets/ui (menu d'interaction : saluer, argent, papiers, police, gouvernement...).
// Projection monde -> ecran : CSprite::CalcScreenCoors (meme repere que les touches et que ImGui).
#include "main.h"
#include "game/game.h"
#include "net/netgame.h"
#include "PlayerTap.h"

#include <chrono>
#include <cmath>
#include <cstdio>

extern CGame* pGame;
extern CNetGame* pNetGame;

namespace
{
    constexpr uint32_t kDoubleTapMs   = 380;    // delai max entre les deux touches
    constexpr float    kDoubleTapMove = 80.0f;  // ecart max (px) entre les deux touches
    constexpr float    kHitRadius     = 110.0f; // distance max (px) entre la touche et le joueur a l'ecran
    constexpr float    kMaxCamDist    = 30.0f;  // joueurs trop loin de la camera ignores
    constexpr uint32_t kCooldownMs    = 1200;

    uint32_t s_lastPushMs = 0;
    float    s_lastX = -9999.0f, s_lastY = -9999.0f;
    uint32_t s_lastSendMs = 0;

    uint32_t NowMs()
    {
        using namespace std::chrono;
        return (uint32_t)duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
    }

    bool ToScreen(CVector pos, float& sx, float& sy)
    {
        CVector out;
        ((void (*)(CVector*, CVector*, float*, float*, bool, bool))(g_libGTASA + (VER_x32 ? 0x005C57E8 + 1 : 0x5F449C)))(&pos, &out, 0, 0, 0, 0);
        if (out.z < 1.0f) return false;   // derriere la camera
        sx = out.x;
        sy = out.y;
        return true;
    }

    int FindPlayerAt(float x, float y)
    {
        if (!pNetGame || !pGame) return -1;
        CPlayerPool* pool = pNetGame->GetPlayerPool();
        if (!pool) return -1;
        CPlayerPed* local = pGame->FindPlayerPed();
        if (!local || !local->m_pPed || local->IsInVehicle()) return -1;

        int best = -1;
        float bestDist = kHitRadius;
        for (PLAYERID id = 0; id < MAX_PLAYERS; id++)
        {
            if (!pool->GetSlotState(id)) continue;
            CRemotePlayer* rp = pool->GetAt(id);
            if (!rp || !rp->IsActive() || rp->IsNPC()) continue;
            CPlayerPed* ped = rp->GetPlayerPed();
            if (!ped || !ped->m_pPed || !ped->m_pPed->IsAdded()) continue;
            if (ped->m_pPed->GetDistanceFromCamera() > kMaxCamDist) continue;

            // tete (8) et torse (3) : on garde le point le plus proche de la touche
            static const int bones[] = { 8, 3 };
            for (int b : bones)
            {
                CVector p;
                p.x = 0.0f; p.y = 0.0f; p.z = 0.0f;
                ped->GetBonePosition(b, &p);
                float sx, sy;
                if (!ToScreen(p, sx, sy)) continue;
                float d = std::hypot(sx - x, sy - y);
                if (d < bestDist) { bestDist = d; best = (int)id; }
            }
        }
        return best;
    }
}

void PlayerTap::OnTouch(int type, int x, int y)
{
    if (type != 2) return;   // 2 = appui
    const uint32_t now = NowMs();
    const bool isDouble = (now - s_lastPushMs) < kDoubleTapMs
        && std::fabs((float)x - s_lastX) < kDoubleTapMove
        && std::fabs((float)y - s_lastY) < kDoubleTapMove;
    s_lastPushMs = now;
    s_lastX = (float)x;
    s_lastY = (float)y;
    if (!isDouble) return;
    s_lastPushMs = 0;   // un triple tap ne declenche pas deux fois
    if (now - s_lastSendMs < kCooldownMs) return;

    const int id = FindPlayerAt((float)x, (float)y);
    if (id < 0) return;
    s_lastSendMs = now;
    char cmd[40];
    snprintf(cmd, sizeof(cmd), "/interaction %d", id);
    pNetGame->SendChatCommand(cmd);
}
