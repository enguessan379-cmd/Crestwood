#pragma once

#include <cstdint>
#include <string>

// ============================================================
// CharacterCreator — nova interface de criação de personagem
// (janelas ImGui nativas, independentes do celular Flutter).
//
// Fluxo:
//  1. Servidor manda OPEN_SELECT -> tela "SELECIONE O PERSONAGEM"
//  2. Jogador clica no "+" -> modal "NOVO PERSONAGEM"
//     (gênero, nome, sobrenome, data de nascimento)
//  3. CONFIRMAR -> cliente envia CREATE_REQ -> servidor troca o
//     skin do ped (163 masc / 9 fem) e responde OPEN_CREATE
//  4. Tela "CRIAR PERSONAGEM" com o painel completo de clothes
//  5. CONFIRMAR -> cliente envia CREATE_SAVE -> servidor salva
//     no SQLite e spawna o jogador direto (APPLY_LOOK)
// ============================================================

class CharacterCreator
{
public:
    // Comandos do protocolo (packet 253) — devem bater com a GM e com o netgame.cpp
    static constexpr int kPacketId = 253;
    // servidor -> cliente
    static constexpr uint32_t kCmdSelect    = 1; // abre tela de seleção (com JSON do personagem)
    static constexpr uint32_t kCmdCreate    = 2; // abre tela de criação
    static constexpr uint32_t kCmdApplyLook = 3; // aplica visual salvo (reconexão) e fecha janelas
    static constexpr uint32_t kCmdClose     = 4; // fecha janelas
    // cliente -> servidor
    static constexpr uint32_t kCmdCreateReq = 1; // modal confirmado (gênero + nomes + nascimento)
    static constexpr uint32_t kCmdSave      = 2; // criação confirmada (valores de clothes)
    static constexpr uint32_t kCmdSpawnReq  = 3; // NASCER (personagem existente)
    static constexpr uint32_t kCmdSkinReq   = 4; // choix d'un skin du registre (uint16 id) -> serveur SetPlayerSkin (visible par tous)

    // Chamadas vindas do netgame.cpp (servidor -> cliente).
    static void OpenSelect(const std::string& json);
    static void OpenCreate();
    static void ApplyLook(const std::string& json);
    static void CloseAll();

    // MODE TEST (sans serveur / sans Pawn) : ouvre directement le panneau de
    // vêtements. Appelable depuis n'importe quel thread (bouton du HUD) : la
    // demande est seulement mémorisée, l'ouverture réelle se fait dans Render()
    // (thread du jeu) pour ne pas toucher au ped depuis le thread Android.
    static void RequestOpenTest();

    // Dialogues serveur "@clothes" (Java -> JNI) : 9550 appliquer l'apparence
    // sauvegardee, 9551 ouvrir l'editeur, 9552 fermer. Peut etre appele depuis
    // n'importe quel thread : tout est traite dans Render() (thread du jeu).
    static void OnServerDialog(int dialogId, const std::string& json);

    static bool IsVisible();

    // Desenha a janela do estado atual (chamado dentro do drawList do ImGui).
    static void Render();

    // Envios cliente -> servidor (usados pelas janelas).
    static void SendCreateRequest();
    static void SendSaveCharacter();
    static void SendSpawnRequest();
};