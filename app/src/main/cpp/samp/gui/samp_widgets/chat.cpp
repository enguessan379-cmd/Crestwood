#include "../gui.h"
#include "../../main.h"
#include "../../game/game.h"
#include "../../net/netgame.h"
#include <algorithm>
#include "../settings.h"
#include "java/jniutil.h"

extern UI* pUI;
extern CGame* pGame;
extern CNetGame* pNetGame;
extern CSettings* pSettings;
extern CJavaWrapper *pJavaWrapper;

Chat::Chat()
        : ListBox()
{

}

void Chat::addChatMessage(const std::string& message, const std::string& nick, const ImColor& nick_color)
{
#if CLASSIC_CHAT_ENABLED
    addPlayerMessage(message, nick, nick_color);
#endif
    // Chat classique coupe : le texte des joueurs s'affiche en bulle (CPlayerBubblePool::Say).
}

static uint32_t chatColorToRGB(const ImColor& color)
{
    auto ch = [](float v) { int i = (int)(v * 255.0f + 0.5f); return (uint32_t)(i < 0 ? 0 : (i > 255 ? 255 : i)); };
    return (ch(color.Value.x) << 16) | (ch(color.Value.y) << 8) | ch(color.Value.z);
}

void Chat::addInfoMessage(const std::string& format, ...)
{
    char tmp_buf[512];

    va_list args;
    va_start(args, format);
    vsnprintf(tmp_buf, sizeof(tmp_buf), format.c_str(), args);
    va_end(args);

#if CLASSIC_CHAT_ENABLED
    addMessage(std::string(tmp_buf), ImColor(0x00, 0xc8, 0xc8));
#else
    // Infos importantes (connexion refusee...) : notification du HUD
    if (pJavaWrapper) pJavaWrapper->OnServerMessage(tmp_buf, 0x00c8c8);
#endif
}

void Chat::addDebugMessage(const std::string& format, ...)
{
    char tmp_buf[512];

    va_list args;
    va_start(args, format);
    vsnprintf(tmp_buf, sizeof(tmp_buf), format.c_str(), args);
    va_end(args);

#if CLASSIC_CHAT_ENABLED
    addMessage(std::string(tmp_buf), ImColor(0xbe, 0xbe, 0xbe));
#endif
    // Messages techniques ("Connecting to Server...") : ignores quand le chat classique est coupe.
}

void Chat::addClientMessage(const std::string& message, const ImColor& color)
{
#if CLASSIC_CHAT_ENABLED
    addMessage(message, color);
#else
    // Messages du serveur (SendClientMessage) : notification du HUD au lieu du chat natif.
    if (pJavaWrapper) pJavaWrapper->OnServerMessage(message.c_str(), chatColorToRGB(color));
#endif
}

void Chat::addMessage(const std::string& message, const ImColor& color)
{
    if (this->itemsCount() > UISettings::chatMaxMessages())
    {
        this->removeItem(0);
    }

    MessageItem* item = new MessageItem(message, color);
    this->addItem(item);
    /*if(!active())*/ this->setScrollY(1.0f);
}

void Chat::addPlayerMessage(const std::string& message, const std::string& nick, const ImColor& nick_color)
{
    if (this->itemsCount() > UISettings::chatMaxMessages())
    {
        this->removeItem(0);
    }

    PlayerMessageItem* item = new PlayerMessageItem(message, nick, nick_color);
    this->addItem(item);
    /*if(!active())*/ this->setScrollY(1.0f);
}

void Chat::draw(ImGuiRenderer* renderer)
{
    ListBox::draw(renderer);
}

void Chat::activateEvent(bool active)
{
    if (active)
    {
        this->setScrollable(true);
    }
    else
    {
        this->setScrollable(false);
    }
}

void Chat::touchPopEvent()
{
    if (pUI->playertablist()->visible()) return;

    pUI->keyboard()->show(this);
}

void Chat::keyboardEvent(const std::string& input)
{
    if (input.length() > 0 && pNetGame)
    {
        if (input[0] == '/') pNetGame->SendChatCommand(input.c_str());
        else pNetGame->SendChatMessage(input.c_str());
    }
}