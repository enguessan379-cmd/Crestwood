#include "../main.h"
#include "../game/game.h"
#include "netgame.h"
#include "../game/RW/RenderWare.h"
#include "../gui/gui.h"
#include "playerbubblepool.h"

#include <cctype>
#include <cstring>
#include <string>
#include <vector>

extern CNetGame *pNetGame;

/* ------------------------------------------------------------------
   Style des bulles de chat (type Reduto) : fond noir arrondi, texte blanc,
   petite pointe vers le joueur. Ajuste ces constantes pour modifier le rendu.
   ------------------------------------------------------------------ */
static const size_t kBubbleMaxCharsPerLine = 30;     // retour a la ligne automatique
static const size_t kBubbleMaxLines        = 6;
static const float  kBubbleFontScale       = 1.05f;  // x (fontSize / 2)
static const float  kBubbleBackgroundAlpha = 0.74f;
static const float  kBubbleDistance        = 35.0f;  // distance max d'affichage (m)
static const uint32_t kBubbleBaseTimeMs    = 2500;   // duree minimale
static const uint32_t kBubbleTimePerCharMs = 70;     // + duree par caractere
static const uint32_t kBubbleMaxTimeMs     = 9000;
static const uint32_t kBubbleFadeMs        = 400;

static size_t Utf8Length(const std::string &s)
{
    size_t n = 0;
    for (unsigned char c : s) if ((c & 0xC0) != 0x80) n++;
    return n;
}

// Retire les codes couleur {RRGGBB}
static std::string StripColorCodes(const std::string &in)
{
    std::string out;
    out.reserve(in.size());
    for (size_t i = 0; i < in.size(); i++)
    {
        if (in[i] == '{' && i + 7 < in.size() + 0 && in[i + 7] == '}')
        {
            bool hex = true;
            for (size_t k = 1; k <= 6; k++) if (!isxdigit((unsigned char)in[i + k])) { hex = false; break; }
            if (hex) { i += 7; continue; }
        }
        out += in[i];
    }
    return out;
}

// Retour a la ligne par mots (compte en caracteres UTF-8, pas en octets)
static std::vector<std::string> WrapBubbleLines(const std::string &text, size_t maxChars)
{
    std::vector<std::string> lines;
    std::string current;
    size_t currentLen = 0;
    auto flush = [&]() {
        if (!current.empty()) { lines.push_back(current); current.clear(); currentLen = 0; }
    };

    size_t i = 0;
    while (i < text.size())
    {
        while (i < text.size() && (text[i] == ' ' || text[i] == '\n' || text[i] == '\r' || text[i] == '\t')) i++;
        size_t start = i;
        while (i < text.size() && text[i] != ' ' && text[i] != '\n' && text[i] != '\r' && text[i] != '\t') i++;
        if (start == i) break;

        std::string word = text.substr(start, i - start);
        size_t wlen = Utf8Length(word);

        while (wlen > maxChars)           // mot plus long qu'une ligne : coupure dure
        {
            flush();
            size_t bytes = 0, cnt = 0;
            while (bytes < word.size() && cnt < maxChars)
            {
                bytes++;
                while (bytes < word.size() && ((unsigned char)word[bytes] & 0xC0) == 0x80) bytes++;
                cnt++;
            }
            lines.push_back(word.substr(0, bytes));
            word.erase(0, bytes);
            wlen -= cnt;
        }
        if (word.empty()) continue;

        if (currentLen == 0) { current = word; currentLen = wlen; }
        else if (currentLen + 1 + wlen <= maxChars) { current += ' '; current += word; currentLen += 1 + wlen; }
        else { flush(); current = word; currentLen = wlen; }
    }
    flush();
    if (lines.empty()) lines.push_back("");
    if (lines.size() > kBubbleMaxLines) lines.resize(kBubbleMaxLines);
    return lines;
}

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
        if(m_pPlayerBubble[i] && m_bSlotState[i])
        {
            delete m_pPlayerBubble[i];
        }

        m_pPlayerBubble[i] = 0;
        m_bSlotState[i] = false;
    }
}

PlayerBubbleStruct *CPlayerBubblePool::New(PLAYERID playerId, const char *text, uint32_t color, float distance, uint32_t time)
{
    if (playerId >= MAX_PLAYERS || !text) return nullptr;

    if(m_bSlotState[playerId] && m_pPlayerBubble[playerId])
    {
        delete m_pPlayerBubble[playerId];
        m_pPlayerBubble[playerId] = 0;
        m_bSlotState[playerId] = false;
    }

    std::string utf8 = StripColorCodes(Encoding::cp2utf(text));
    if (utf8.size() > 220)                       // coupe proprement sur une frontiere UTF-8
    {
        size_t cut = 220;
        while (cut > 0 && ((unsigned char)utf8[cut] & 0xC0) == 0x80) cut--;
        utf8.resize(cut);
    }

    std::vector<std::string> lines = WrapBubbleLines(utf8, kBubbleMaxCharsPerLine);
    std::string joined;
    for (size_t i = 0; i < lines.size(); i++)
    {
        if (i) joined += '\n';
        joined += lines[i];
    }

    PlayerBubbleStruct* playerBubbleStruct = new PlayerBubbleStruct;
    memset(playerBubbleStruct, 0, sizeof(PlayerBubbleStruct));
    strncpy(playerBubbleStruct->szText, joined.c_str(), sizeof(playerBubbleStruct->szText) - 1);

    playerBubbleStruct->fDistance = distance;
    playerBubbleStruct->uiExpireTime = GetTickCount() + time;
    playerBubbleStruct->dwLineCount = (uint32_t)lines.size();
    playerBubbleStruct->uiColor = color;

    m_pPlayerBubble[playerId] = playerBubbleStruct;
    m_bSlotState[playerId] = true;

    return m_pPlayerBubble[playerId];
}

void CPlayerBubblePool::Say(PLAYERID playerId, const char *text)
{
    if (!text || !*text) return;
    uint32_t duration = kBubbleBaseTimeMs + (uint32_t)strlen(text) * kBubbleTimePerCharMs;
    if (duration > kBubbleMaxTimeMs) duration = kBubbleMaxTimeMs;
    New(playerId, text, 0xFFFFFFFF, kBubbleDistance, duration);
}

void CPlayerBubblePool::Delete(PLAYERID playerId)
{
    if(m_bSlotState[playerId] && m_pPlayerBubble[playerId])
    {
        delete m_pPlayerBubble[playerId];
        m_pPlayerBubble[playerId] = 0;
        m_bSlotState[playerId] = false;
    }
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

    const uint32_t now = GetTickCount();
    const PLAYERID localId = pPlayerPool->GetLocalPlayerID();

    for(PLAYERID i = 0; i < MAX_PLAYERS; i++)
    {
        if(!m_bSlotState[i] || !m_pPlayerBubble[i]) continue;

        PlayerBubbleStruct *bubble = m_pPlayerBubble[i];
        if(now >= bubble->uiExpireTime)
        {
            Delete(i);
            continue;
        }

        // Joueur concerne : le joueur local OU un joueur distant actif
        CPlayerPed *pPlayerPed = nullptr;
        if (i == localId)
        {
            CLocalPlayer *pLocal = pPlayerPool->GetLocalPlayer();
            pPlayerPed = pLocal ? pLocal->GetPlayerPed() : nullptr;
        }
        else if (pPlayerPool->GetSlotState(i))
        {
            CRemotePlayer *pRemotePlayer = pPlayerPool->GetAt(i);
            if (pRemotePlayer && pRemotePlayer->IsActive()) pPlayerPed = pRemotePlayer->GetPlayerPed();
        }

        if (!pPlayerPed || !pPlayerPed->m_pPed || !pPlayerPed->m_pPed->IsAdded()) continue;

        const float fDist = pPlayerPed->m_pPed->GetDistanceFromCamera();
        if (i != localId && fDist > bubble->fDistance) continue;

        CVector vecPos;
        vecPos.x = 0.0f; vecPos.y = 0.0f; vecPos.z = 0.0f;
        pPlayerPed->GetBonePosition(8, &vecPos);

        // meme hauteur que le nametag : la bulle se place juste au-dessus
        vecPos.z += 0.25f + (fDist * 0.0475f);

        CVector vecOut;
        ((void (*)(CVector *, CVector *, float *, float *, bool, bool))(g_libGTASA + 0x5F449C))(&vecPos, &vecOut, 0, 0, 0, 0);
        if (vecOut.z < 1.0f) continue;

        // ---- mise en page ----
        const float fontSize = (UISettings::fontSize() / 2) * kBubbleFontScale;
        const float lineH = fontSize * 1.18f;
        const float padX = fontSize * 0.75f;
        const float padY = fontSize * 0.50f;

        std::vector<std::string> lines;
        {
            std::string all(bubble->szText);
            size_t start = 0;
            while (true)
            {
                size_t nl = all.find('\n', start);
                lines.push_back(all.substr(start, nl == std::string::npos ? std::string::npos : nl - start));
                if (nl == std::string::npos) break;
                start = nl + 1;
            }
        }

        float maxW = 0.0f;
        std::vector<float> widths;
        for (const std::string &line : lines)
        {
            float w = renderer->calculateTextSize(line, fontSize).x;
            widths.push_back(w);
            if (w > maxW) maxW = w;
        }

        const float boxW = maxW + padX * 2.0f;
        const float boxH = lineH * (float)lines.size() + padY * 2.0f;
        const float tail = fontSize * 0.45f;
        const float cx = vecOut.x;
        const float bottom = vecOut.y - fontSize * 0.35f - tail;
        const ImVec2 bMin(cx - boxW / 2.0f, bottom - boxH);
        const ImVec2 bMax(cx + boxW / 2.0f, bottom);

        // fondu en fin de vie
        const uint32_t remaining = bubble->uiExpireTime - now;
        const float alpha = remaining < kBubbleFadeMs ? (float)remaining / (float)kBubbleFadeMs : 1.0f;

        const ImColor bg(0.0f, 0.0f, 0.0f, kBubbleBackgroundAlpha * alpha);
        renderer->drawRect(bMin, bMax, bg, true, fontSize * 0.55f);
        renderer->drawTriangle(ImVec2(cx - tail, bottom - 0.5f), ImVec2(cx + tail, bottom - 0.5f), ImVec2(cx, bottom + tail), bg, true);

        const ImColor fg(1.0f, 1.0f, 1.0f, alpha);
        for (size_t l = 0; l < lines.size(); l++)
        {
            ImVec2 tp(cx - widths[l] / 2.0f, bMin.y + padY + lineH * (float)l + (lineH - fontSize) * 0.35f);
            renderer->drawText(tp, fg, lines[l], false, fontSize);
        }
    }
}
