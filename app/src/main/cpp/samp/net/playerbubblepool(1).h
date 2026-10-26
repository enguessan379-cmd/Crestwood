#pragma once

#include "../gui/gui.h"

#pragma pack(push, 1)
struct PlayerBubbleStruct
{
    char szText[256];
    float fDistance;
    uint32_t uiExpireTime;
    uint32_t dwLineCount;
    uint32_t uiColor;
    uint8_t bChat;      // 1 = bulle de discussion (fond + pointe), 0 = SetPlayerChatBubble classique
};
#pragma pack(pop)

class CPlayerBubblePool
{
public:
    CPlayerBubblePool();
    ~CPlayerBubblePool();

    // Bulle classique (RPC SetPlayerChatBubble) : texte seul au-dessus du joueur
    PlayerBubbleStruct *New(PLAYERID playerId, const char *text, uint32_t color, float distance, uint32_t time);

    // Bulle de discussion : ce que le joueur ecrit s'affiche dans une bulle au-dessus de sa tete.
    // color = couleur du joueur (RGBA), textIsUtf8 = true si le texte est deja en UTF-8.
    PlayerBubbleStruct *NewChat(PLAYERID playerId, const char *text, uint32_t color, bool textIsUtf8);

    void Delete(PLAYERID playerId);

    void Render(ImGuiRenderer* renderer);

private:
    PlayerBubbleStruct *Acquire(PLAYERID playerId);
    void Draw(ImGuiRenderer* renderer);
    PlayerBubbleStruct *m_pPlayerBubble[MAX_PLAYERS];
    bool m_bSlotState[MAX_PLAYERS];
};
