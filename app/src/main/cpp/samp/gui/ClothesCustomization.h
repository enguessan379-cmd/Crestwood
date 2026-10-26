#pragma once

#include <cstdint>

// ============================================================
// ClothesCustomization — sistema de customização de personagem
// portado do BRL (todas as funções: pele, rosto, sobrancelha,
// barba, cabelo, maquiagem, roupas, calçados, tatuagens e
// equipamento policial).
//
// O estado fica em ClothesLook e o ApplyCurrent() aplica as
// texturas no ped local através do BrlTextureShader.
// ============================================================

struct ClothesLook
{
    // Identidade
    uint8_t skinTone     = 1; // 1..6
    uint8_t face         = 1; // varia conforme tom de pele
    // Cabelo
    uint8_t hairStyle    = 1;
    uint8_t hairColor    = 1;
    // Sobrancelha (0 = nenhuma)
    uint8_t eyebrowStyle = 0;
    uint8_t eyebrowColor = 1;
    // Barba (masculino, 0 = nenhuma)
    uint8_t beardStyle   = 0;
    uint8_t beardColor   = 1;
    // Maquiagem (feminino, 0 = nenhuma)
    uint8_t makeupStyle  = 0;
    uint8_t makeupColor  = 1;
    // Roupas
    uint8_t topStyle     = 1;
    uint8_t topColor     = 1;
    uint8_t bottomStyle  = 1;
    uint8_t bottomColor  = 1;
    uint8_t shoesStyle   = 1;
    uint8_t shoesColor   = 1;
    // Tatuagens (0 = nenhuma)
    uint8_t tattooArmLeft  = 0;
    uint8_t tattooArmRight = 0;
    uint8_t tattooLegLeft  = 0;
    uint8_t tattooLegRight = 0;
    uint8_t tattooBack     = 0;
    uint8_t tattooFront    = 0;
    // Equipamento policial (0/1)
    uint8_t policeBelt = 0;
    uint8_t policeVest = 0;
    uint8_t policeHat  = 0;
};

class ClothesCustomization
{
public:
    // Estado atual (editável antes de chamar ApplyCurrent).
    static ClothesLook& Look();

    // Zera as opções para os padrões do gênero (0 = masculino, 1 = feminino).
    static void Reset(int gender);

    // Aplica o estado atual no ped local (texturas via BrlTextureShader).
    static void ApplyCurrent();

    // Remove todas as texturas custom do jogador local.
    static void ClearLocal();

    // Joueurs distants : applique / retire l'apparence d'un autre joueur (thread du jeu).
    // modelId = skin du joueur (9, 163, 246 ou 280).
    static void ApplyRemote(uint16_t playerId, const ClothesLook& look, uint16_t modelId);
    static void ClearRemote(uint16_t playerId);

    // Renderiza o painel ImGui com TODAS as opções (usado na criação).
    static void RenderPanel();

    // True se o ped local está com um dos skins suportados (9/163/246/280).
    static bool IsSupportedLocalSkin();

    // Demande au serveur d'appliquer un skin du registre (SkinRegistry.h).
    // Le serveur fait SetPlayerSkin : le skin est donc visible par TOUS les joueurs.
    static bool RequestSkin(uint16_t modelId);

private:
    static bool IsSupportedSkin(uint16_t modelId);
};