#include "../main.h"
#include "../game/game.h"
#include "netgame.h"
#include "../game/RW/RenderWare.h"
#include "../gui/gui.h"
#include "playerbubblepool.h"
#include <string>
#include <vector>
#include <algorithm>

extern CNetGame *pNetGame;

#define BUBBLE_CHAT_MAX_CHARS   28      // largeur d'une ligne (caracteres)
#define BUBBLE_CHAT_MAX_LINES   4       // nombre de lignes max (le reste est coupe avec "...")

// ---------------------------------------------------------------------------
// Anciennes fonctions (bulles SetPlayerChatBubble)
// ---------------------------------------------------------------------------
int WrapText(char* szText, int iWidth, int iMaxWord)
{
    int s, i, r, w, c;
    char buf[512];

    r = strlen(szText);
    w = iWidth;
    i = 0;
    c = 1;

    memset(buf, 0, sizeof(buf));

    if(r <= (int)sizeof(buf))
    {
        while(r > iWidth)
        {
            s = iWidth;

            while(s != 0 && szText[i + s] != ' ')
                --s;

            if(w - i - s <= iMaxWord)
            {
                i += s;
                szText[i] = '\n';
                r -= s;
                w = i + iWidth;
            }
            else
            {
                strcpy(buf, szText + w);
                szText[w + 1] = 0;
                szText[w] = '\n';
                memcpy(szText + strlen(szText), buf, strlen(buf) + 1);
                i = w + 1;
                r -= iWidth;
                w += 1 + iWidth;
            }

            c++;
        }

        return c;
    }

    return -1;
}

// ---------------------------------------------------------------------------
// Helpers UTF-8 pour les bulles de discussion
// ---------------------------------------------------------------------------
static std::vector<std::string> BubbleUtf8Split(const std::string& s)
{
    std::vector<std::string> v;
    for (size_t i = 0; i < s.size();)
    {
        unsigned char ch = (unsigned char)s[i];
        size_t l = 1;
        if (ch >= 0xF0) l = 4;
        else if (ch >= 0xE0) l = 3;
        else if (ch >= 0xC0) l = 2;
        if (i + l > s.size()) l = s.size() - i;
        v.push_back(s.substr(i, l));
        i += l;
    }
    return v;
}

// Retire les codes couleur SA-MP {RRGGBB}
static std::string BubbleStripColors(const std::string& s)
{
    std::string out;
    out.reserve(s.size());
    for (size_t i = 0; i < s.size(); i++)
    {
        if (s[i] == '{' && i + 7 < s.size() + 0 && s[i + 7] == '}')
        {
            bool hex = true;
            for (int k = 1; k <= 6; k++)
            {
                char c = s[i + k];
                if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) { hex = false; break; }
            }
            if (hex) { i += 7; continue; }
        }
        out += s[i];
    }
    return out;
}

// Retour a la ligne par mots, compte en caracteres (pas en octets)
static std::string BubbleWrap(const std::string& text, size_t maxChars, size_t maxLines, uint32_t& outLines)
{
    std::vector<std::string> lines;
    std::string cur;
    size_t curLen = 0;

    auto flush = [&]() {
        if (!cur.empty()) { lines.push_back(cur); cur.clear(); curLen = 0; }
    };

    size_t pos = 0;
    while (pos < text.size())
    {
        while (pos < text.size() && text[pos] == ' ') pos++;
        if (pos >= text.size()) break;
        size_t end = text.find(' ', pos);
        if (end == std::string::npos) end = text.size();
        std::string word = text.substr(pos, end - pos);
        pos = end;

        std::vector<std::string> cps = BubbleUtf8Split(word);

        if (curLen > 0 && curLen + 1 + cps.size() <= maxChars)
        {
            cur += " " + word;
            curLen += 1 + cps.size();
        }
        else
        {
            flush();
            if (cps.size() <= maxChars)
            {
                cur = word;
                curLen = cps.size();
            }
            else
            {
                for (size_t i = 0; i < cps.size(); i++)
                {
                    cur += cps[i];
                    curLen++;
                    if (curLen == maxChars) flush();
                }
            }
        }
    }
    flush();

    if (lines.empty()) lines.push_back("");
    if (lines.size() > maxLines)
    {
        lines.resize(maxLines);
        lines.back() += "...";
    }

    std::string out;
    for (size_t i = 0; i < lines.size(); i++)
    {
        if (i) out += "\n";
        out += lines[i];
    }
    outLines = (uint32_t)lines.size();
    return out;
}

// ---------------------------------------------------------------------------
// Pool
// ---------------------------------------------------------------------------
CPlayerBubblePool::CPlayerBubblePool()
{
    for(int i = 0; i < MAX_PLAYERS; i++)
    {
        m_pPlayerBubble[i] = 0;
        m_bSlotState[i] = false;
    }
}

CPlayerBubblePool::~CPlayerBubblePool()
{
    for(int i = 0; i < MAX_PLAYERS; i++)
    {
        m_bSlotState[i] = false;
        if(m_pPlayerBubble[i])
        {
            delete m_pPlayerBubble[i];
        }
        m_pPlayerBubble[i] = 0;
    }
}

// La memoire d'un slot est allouee une seule fois et reutilisee (jamais liberee avant
// le destructeur) : evite tout acces a une zone liberee entre le thread reseau/UI et le rendu.
PlayerBubbleStruct *CPlayerBubblePool::Acquire(PLAYERID playerId)
{
    if (playerId >= MAX_PLAYERS) return nullptr;

    m_bSlotState[playerId] = false;
    if (!m_pPlayerBubble[playerId])
    {
        m_pPlayerBubble[playerId] = new PlayerBubbleStruct;
        memset(m_pPlayerBubble[playerId], 0, sizeof(PlayerBubbleStruct));
    }
    return m_pPlayerBubble[playerId];
}

PlayerBubbleStruct *CPlayerBubblePool::New(PLAYERID playerId, const char *text, uint32_t color, float distance, uint32_t time)
{
    PlayerBubbleStruct* b = Acquire(playerId);
    if (!b || !text) return nullptr;

    std::string GText = Encoding::cp2utf(text);
    memset(b->szText, 0, sizeof(b->szText));
    strncpy(b->szText, GText.c_str(), sizeof(b->szText) - 1);

    b->fDistance = distance;
    b->uiExpireTime = GetTickCount() + time;
    int lines = WrapText(b->szText, 36, 12);
    b->dwLineCount = (lines > 0) ? (uint32_t)(lines - 1) : 0;
    b->uiColor = color;
    b->bChat = 0;

    m_bSlotState[playerId] = true;
    return b;
}

PlayerBubbleStruct *CPlayerBubblePool::NewChat(PLAYERID playerId, const char *text, uint32_t color, bool textIsUtf8)
{
    PlayerBubbleStruct* b = Acquire(playerId);
    if (!b || !text) return nullptr;

    std::string utf = textIsUtf8 ? std::string(text) : Encoding::cp2utf(text);
    utf = BubbleStripColors(utf);

    uint32_t lines = 1;
    std::string wrapped = BubbleWrap(utf, BUBBLE_CHAT_MAX_CHARS, BUBBLE_CHAT_MAX_LINES, lines);

    // copie bornee, coupee sur une frontiere UTF-8
    size_t n = std::min(wrapped.size(), sizeof(b->szText) - 1);
    while (n > 0 && n < wrapped.size() && (((unsigned char)wrapped[n]) & 0xC0) == 0x80) n--;
    memset(b->szText, 0, sizeof(b->szText));
    memcpy(b->szText, wrapped.c_str(), n);

    // duree selon la longueur du message : 3.5s a 9s
    uint32_t len = (uint32_t)BubbleUtf8Split(utf).size();
    uint32_t duration = std::min<uint32_t>(9000, std::max<uint32_t>(3500, 2500 + len * 70));

    b->fDistance = 35.0f;
    b->uiExpireTime = GetTickCount() + duration;
    b->dwLineCount = lines;
    b->uiColor = color;
    b->bChat = 1;

    m_bSlotState[playerId] = true;
    return b;
}

void CPlayerBubblePool::Delete(PLAYERID playerId)
{
    if (playerId >= MAX_PLAYERS) return;
    m_bSlotState[playerId] = false;   // la memoire est conservee pour reutilisation
}

void CPlayerBubblePool::Render(ImGuiRenderer* renderer)
{
    this->Draw(renderer);
}

void CPlayerBubblePool::Draw(ImGuiRenderer* renderer)
{
    if (!pNetGame || !renderer) return;

    CPlayerPool *pPlayerPool = pNetGame->GetPlayerPool();
    if (!pPlayerPool) return;

    const PLAYERID localId = pPlayerPool->GetLocalPlayerID();

    for(PLAYERID i = 0; i < MAX_PLAYERS; i++)
    {
        if(!m_bSlotState[i] || !m_pPlayerBubble[i]) continue;

        PlayerBubbleStruct* b = m_pPlayerBubble[i];

        if(GetTickCount() >= b->uiExpireTime)
        {
            Delete(i);
            continue;
        }

        // ---- ped du joueur (local ou distant) ----
        CPlayerPed* pPlayerPed = nullptr;
        bool bLocal = (i == localId);

        if (bLocal)
        {
            CLocalPlayer* pLocal = pPlayerPool->GetLocalPlayer();
            if (pLocal) pPlayerPed = pLocal->GetPlayerPed();
        }
        else
        {
            if (!pPlayerPool->GetSlotState(i)) continue;
            CRemotePlayer* pRemotePlayer = pPlayerPool->GetAt(i);
            if (!pRemotePlayer || !pRemotePlayer->IsActive()) continue;
            pPlayerPed = pRemotePlayer->GetPlayerPed();
        }

        if (!pPlayerPed || !pPlayerPed->m_pPed || !pPlayerPed->m_pPed->IsAdded()) continue;

        float fCamDist = pPlayerPed->m_pPed->GetDistanceFromCamera();
        if (!bLocal && fCamDist > b->fDistance) continue;

        CVector VecPos;
        VecPos.x = 0.0f;
        VecPos.y = 0.0f;
        VecPos.z = 0.0f;
        pPlayerPed->GetBonePosition(8, &VecPos);

        CVector TagPos = VecPos;
        const float fs = UISettings::fontSize() / 2;

        if (b->bChat)
        {
            // meme point d'ancrage que le nametag, la bulle est ensuite decalee vers le haut en pixels
            TagPos.z += 0.25f + (fCamDist * 0.0475f);
        }
        else
        {
            float fTextHeight = ((b->dwLineCount * 0.0125f) + 0.065f) + (b->dwLineCount * 0.0125f);
            TagPos.z += fCamDist * fTextHeight + 0.2f;
        }

        CVector Out;
        ((void (*)(CVector *, CVector *, float *, float *, bool, bool)) (g_libGTASA + 0x5F449C))(&TagPos, &Out, 0, 0, 0, 0);

        if (Out.z < 1.0f) continue;   // derriere la camera : on passe au joueur suivant

        std::string text(b->szText);

        if (!b->bChat)
        {
            // ---- ancien rendu : texte seul ----
            ImVec2 pos = ImVec2(Out.x, Out.y - (UISettings::fontSize() * 1.0));
            pos.x -= renderer->calculateTextSize(text, fs).x / 2;
            renderer->drawText(pos, __builtin_bswap32(b->uiColor | (0x000000FF)), text, true, fs);
            continue;
        }

        // ---- bulle de discussion ----
        ImVec2 ts = renderer->calculateTextSize(text, fs);
        const float pad = fs * 0.6f;
        const float rounding = fs * 0.7f;
        const float tail = fs * 0.7f;

        // le joueur distant a un nametag (+ barre de vie) sous l'ancrage : on remonte la bulle
        float tipY = Out.y - (bLocal ? fs * 0.4f : fs * 1.7f);
        float bottom = tipY - tail;

        ImVec2 a = ImVec2(Out.x - ts.x / 2 - pad, bottom - ts.y - pad * 2);
        ImVec2 c = ImVec2(Out.x + ts.x / 2 + pad, bottom);

        // Bulle noire unie, sans contour colore (l'ancien liseré a la couleur du joueur donnait des
        // bords bizarres). Presque opaque + pointe qui chevauche d'1 px le corps : aucune couture visible.
        ImColor bg(8, 8, 8, 235);

        renderer->drawRect(a, c, bg, true, rounding);
        renderer->drawTriangle(ImVec2(Out.x - tail * 0.7f, bottom - 1.0f), ImVec2(Out.x + tail * 0.7f, bottom - 1.0f),
                               ImVec2(Out.x, tipY), bg, true);
        renderer->drawText(ImVec2(a.x + pad, a.y + pad), ImColor(255, 255, 255, 255), text, false, fs);
    }
}
