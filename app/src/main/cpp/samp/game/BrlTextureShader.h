#pragma once

#include <cstdint>
#include <string>

class CPlayerPed;
class CVehicle;
class CObject;
struct RwObject;

// Tipos de alvo suportados pelas regras de troca de textura.
enum class BrlShaderTargetType : uint8_t
{
    Player = 0,
    Vehicle,
    Object,
    Weapon
};

// Motor de troca de textura por material (portado do sistema BRL).
//
// Regras: material original -> textura de substituicao, agrupadas por alvo
// (jogador local 0xFFFF, jogador remoto, veiculo, objeto ou arma). As regras
// sao reaplicadas periodicamente pelo Process(), porque o GTA mobile recria
// os materiais ao streamar entidades.
class BrlTextureShader
{
public:
    // Verifica se a textura existe no cache de texturas do jogo (mobile).
    static bool HasTexture(const std::string& textureName);

    // Registra regras de troca (nao aplica imediatamente; Process() aplica).
    static bool SetPlayerTexture(uint16_t playerId, const std::string& sourceTexture, const std::string& replacementTexture);
    static bool SetLocalPlayerTexture(const std::string& sourceTexture, const std::string& replacementTexture);
    static bool ClearLocalPlayerTexture(const std::string& sourceTexture);
    // Idem pour un joueur distant (restaure la texture d'origine sur son ped s'il est streame).
    static bool ClearPlayerTexture(uint16_t playerId, const std::string& sourceTexture);

    // Restaure les textures d'origine du ped local et oublie ses regles.
    // A appeler AVANT de changer son modele (SetModelIndex) : les anciens
    // RpMaterial* sont liberes et ne doivent plus rester dans les caches.
    static void ResetLocalPlayer();
    static bool SetVehicleTexture(uint16_t vehicleId, const std::string& sourceTexture, const std::string& replacementTexture);
    static bool SetObjectTexture(uint16_t objectId, const std::string& sourceTexture, const std::string& replacementTexture);
    static bool SetWeaponTexture(uint16_t playerId, uint8_t weaponId, const std::string& sourceTexture, const std::string& replacementTexture);

    // Remove todos os rules de um alvo.
    static bool ClearPlayer(uint16_t playerId);
    static bool ClearVehicle(uint16_t vehicleId);
    static bool ClearObject(uint16_t objectId);
    static bool ClearWeapon(uint16_t playerId, uint8_t weaponId);

    // Restaura todas as texturas originais de todas as entidades e limpa tudo.
    static void ClearAll();

    // Aplica as regras registradas a uma entidade especifica.
    static void ApplyToPed(uint16_t playerId, CPlayerPed* ped);
    static void ApplyToVehicle(uint16_t vehicleId, CVehicle* vehicle);
    static void ApplyToObject(uint16_t objectId, CObject* object);

    // Aplica uma troca unica (sem registrar rule) a um RwObject.
    static bool ApplyToRwObject(RwObject* rwObject, const std::string& sourceTexture, const std::string& replacementTexture);

    // Lista as texturas atuais de um RwObject (debug).
    static std::string DebugRwObjectTextures(RwObject* rwObject, int maxTextures);

    // Reaplica as regras a todas as entidades (chamar no MainLoop).
    static void Process();
};