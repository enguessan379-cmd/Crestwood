#include <android/log.h>
#include "BrlTextureShader.h"

#include <algorithm>
#include <mutex>
#include <optional>
#include <sstream>
#include <set>
#include <unordered_map>
#include <vector>

#include "../main.h"
#include "game.h"
#include "playerped.h"
#include "vehicle.h"
#include "object.h"
#include "Textures/TextureDatabaseRuntime.h"
#include "RW/rwcore.h"
#include "RW/rpworld.h"
#include "../net/netgame.h"
#include "../net/playerpool.h"
#include "../net/remoteplayer.h"
#include "../net/vehiclepool.h"
#include "../net/objectpool.h"

extern CGame* pGame;
extern CNetGame* pNetGame;

namespace {
	constexpr uint16_t kLocalPlayerShaderId = 0xFFFF;
	constexpr uint16_t kAnyShaderModelId = 0xFFFF;
	constexpr uint32_t kProcessIntervalMs = 750;

	struct ShaderKey {
		BrlShaderTargetType type{};
		uint16_t id{};
		uint8_t subId{};

		bool operator==(const ShaderKey& other) const
		{
			return type == other.type && id == other.id && subId == other.subId;
		}
	};

	struct ShaderKeyHash {
		size_t operator()(const ShaderKey& key) const
		{
			return (static_cast<size_t>(key.type) << 24u) ^ (static_cast<size_t>(key.id) << 8u) ^ key.subId;
		}
	};

	struct ShaderRule {
		std::string sourceTexture;
		std::string replacementTexture;
		uint16_t modelId{kAnyShaderModelId};
	};

	struct ApplyContext {
		uint16_t modelId{};
		ShaderRule* rule{};
	};

	struct RestoreContext {
		uint16_t modelId{};
		std::string sourceTexture;
	};

	std::mutex g_shaderMutex;
	std::unordered_map<ShaderKey, std::vector<ShaderRule>, ShaderKeyHash> g_rules;
	std::unordered_map<std::string, RwTexture*> g_textureCache;
	std::unordered_map<RpMaterial*, RwTexture*> g_originalMaterialTextures;
	std::unordered_map<RpMaterial*, std::string> g_originalMaterialNames;

	std::string NormalizeName(std::string value)
	{
		std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
			return static_cast<char>(std::tolower(c));
		});
		return value;
	}

	bool TextureMatches(RwTexture* texture, const std::string& sourceTexture)
	{
		if (sourceTexture.empty() || sourceTexture == "*") return true;
		if (!texture) return false;
		return NormalizeName(texture->name) == NormalizeName(sourceTexture);
	}

	bool MaterialMatches(RpMaterial* material, uint16_t, int, const std::string& sourceTexture)
	{
		if (!material) return false;
		if (sourceTexture.empty() || sourceTexture == "*") return true;

		const std::string normalizedSource = NormalizeName(sourceTexture);

		if (auto it = g_originalMaterialNames.find(material); it != g_originalMaterialNames.end()) {
			if (NormalizeName(it->second) == normalizedSource) return true;
		}

		if (TextureMatches(material->texture, sourceTexture)) return true;

		return false;
	}

	RwTexture* ResolveTexture(const std::string& textureName)
	{
		if (textureName.empty()) return nullptr;

		const std::string normalized = NormalizeName(textureName);
		if (auto it = g_textureCache.find(normalized); it != g_textureCache.end()) {
			return it->second;
		}

		RwTexture* texture = TextureDatabaseRuntime::GetTexture(textureName.c_str());
		if (!texture && normalized != textureName) {
			texture = TextureDatabaseRuntime::GetTexture(normalized.c_str());
		}

		if (texture) {
			RwTextureSetFilterMode(texture, rwFILTERLINEARMIPLINEAR);
			RwTextureSetAddressing(texture, rwTEXTUREADDRESSWRAP);
		}

		if (texture) {
			g_textureCache[normalized] = texture;
		}
		return texture;
	}

	RpAtomic* ApplyAtomicCallback(RpAtomic* atomic, void* data)
	{
		auto* context = static_cast<ApplyContext*>(data);
		if (!atomic || !context || !context->rule || *(uint8_t*) atomic != 1) return atomic;
		ShaderRule* rule = context->rule;
		if (rule->modelId != kAnyShaderModelId && rule->modelId != context->modelId) return atomic;

		RpGeometry* geometry = atomic->geometry;
		if (!geometry || geometry->matList.numMaterials <= 0 || !geometry->matList.materials) return atomic;

		RwTexture* replacement = ResolveTexture(rule->replacementTexture);
		if (!replacement) return atomic;

		for (int i = 0; i < geometry->matList.numMaterials; ++i) {
			RpMaterial* material = geometry->matList.materials[i];
			if (!material || !MaterialMatches(material, context->modelId, i, rule->sourceTexture)) continue;

			if (!g_originalMaterialTextures.contains(material)) {
				g_originalMaterialTextures[material] = material->texture;
				g_originalMaterialNames[material] = material->texture ? material->texture->name : rule->sourceTexture;
			}
			material->texture = replacement;
		}

		return atomic;
	}

	RpAtomic* RestoreAtomicCallback(RpAtomic* atomic, void*)
	{
		if (!atomic || *(uint8_t*) atomic != 1) return atomic;

		RpGeometry* geometry = atomic->geometry;
		if (!geometry || geometry->matList.numMaterials <= 0 || !geometry->matList.materials) return atomic;

		for (int i = 0; i < geometry->matList.numMaterials; ++i) {
			RpMaterial* material = geometry->matList.materials[i];
			if (!material) continue;

			auto it = g_originalMaterialTextures.find(material);
			if (it != g_originalMaterialTextures.end()) {
				material->texture = it->second;
				g_originalMaterialTextures.erase(it);
			}
		}

		return atomic;
	}

	RpAtomic* RestoreMatchingAtomicCallback(RpAtomic* atomic, void* data)
	{
		auto* context = static_cast<RestoreContext*>(data);
		if (!atomic || !context || *(uint8_t*) atomic != 1) return atomic;

		RpGeometry* geometry = atomic->geometry;
		if (!geometry || geometry->matList.numMaterials <= 0 || !geometry->matList.materials) return atomic;

		for (int i = 0; i < geometry->matList.numMaterials; ++i) {
			RpMaterial* material = geometry->matList.materials[i];
			if (!material || !MaterialMatches(material, context->modelId, i, context->sourceTexture)) continue;

			auto it = g_originalMaterialTextures.find(material);
			if (it != g_originalMaterialTextures.end()) {
				material->texture = it->second;
				g_originalMaterialTextures.erase(it);
			}
			g_originalMaterialNames.erase(material);
		}

		return atomic;
	}

	void ForEachAtomic(RwObject* rwObject, RpAtomicCallBack callback, void* data)
	{
		if (!rwObject || !callback) return;

		const uint8_t rwType = *(uint8_t*) rwObject;
		if (rwType == rpATOMIC) {
			callback(reinterpret_cast<RpAtomic*>(rwObject), data);
			return;
		}

		if (rwType == rpCLUMP) {
			RpClumpForAllAtomics(reinterpret_cast<RpClump*>(rwObject), callback, data);
			return;
		}

		auto* parent = reinterpret_cast<RwFrame*>(rwObject->parent);
		if (parent) {
			RwFrameForAllObjects(parent, reinterpret_cast<RwObjectCallBack>(callback), data);
		}
	}

	bool SetRule(const ShaderKey& key, const std::string& sourceTexture, const std::string& replacementTexture, uint16_t modelId)
	{
		if (sourceTexture.empty() || replacementTexture.empty()) return false;
		if (!ResolveTexture(replacementTexture)) {
			__android_log_print(ANDROID_LOG_INFO, "BrlShader", "[BrlShader] replacement texture '%s' is pending; add it to the mobile texture cache.", replacementTexture.c_str());
		}

		std::lock_guard lock(g_shaderMutex);
		auto& rules = g_rules[key];
		auto it = std::find_if(rules.begin(), rules.end(), [&](const ShaderRule& rule) {
			return NormalizeName(rule.sourceTexture) == NormalizeName(sourceTexture) && rule.modelId == modelId;
		});

		if (it != rules.end()) {
			it->replacementTexture = replacementTexture;
		}
		else {
			rules.push_back({sourceTexture, replacementTexture, modelId});
		}

		return true;
	}

	bool ClearRuleGroup(const ShaderKey& key)
	{
		std::lock_guard lock(g_shaderMutex);
		return g_rules.erase(key) > 0;
	}

	bool ClearRuleSource(const ShaderKey& key, const std::string& sourceTexture)
	{
		if (sourceTexture.empty()) return false;

		std::lock_guard lock(g_shaderMutex);
		auto it = g_rules.find(key);
		if (it == g_rules.end()) return false;

		const std::string normalizedSource = NormalizeName(sourceTexture);
		auto& rules = it->second;
		const auto oldSize = rules.size();
		rules.erase(std::remove_if(rules.begin(), rules.end(), [&](const ShaderRule& rule) {
			return NormalizeName(rule.sourceTexture) == normalizedSource;
		}), rules.end());

		const bool removed = rules.size() != oldSize;
		if (rules.empty()) {
			g_rules.erase(it);
		}

		return removed;
	}

	void ApplyRulesToObject(const ShaderKey& key, RwObject* rwObject, uint16_t modelId)
	{
		if (!rwObject) return;

		std::vector<ShaderRule> rules;
		{
			std::lock_guard lock(g_shaderMutex);
			auto it = g_rules.find(key);
			if (it == g_rules.end()) return;
			rules = it->second;
		}

		for (auto& rule : rules) {
			ApplyContext context{modelId, &rule};
			ForEachAtomic(rwObject, ApplyAtomicCallback, &context);
		}
	}

	void RestoreObject(RwObject* rwObject)
	{
		ForEachAtomic(rwObject, RestoreAtomicCallback, nullptr);
	}

	void RestoreSourceOnObject(RwObject* rwObject, uint16_t modelId, const std::string& sourceTexture)
	{
		RestoreContext context{modelId, sourceTexture};
		ForEachAtomic(rwObject, RestoreMatchingAtomicCallback, &context);
	}

	CPlayerPed* FindLocalPed()
	{
		if (pNetGame && pNetGame->GetPlayerPool() && pNetGame->GetPlayerPool()->GetLocalPlayer()) {
			return pNetGame->GetPlayerPool()->GetLocalPlayer()->GetPlayerPed();
		}
		if (pGame) {
			return pGame->FindPlayerPed();
		}
		return nullptr;
	}

	CPlayerPed* FindPlayerPed(uint16_t playerId)
	{
		if (playerId == kLocalPlayerShaderId) return FindLocalPed();
		if (pNetGame && pNetGame->GetPlayerPool()) {
			if (CRemotePlayer* remote = pNetGame->GetPlayerPool()->GetAt(static_cast<PLAYERID>(playerId))) {
				return remote->GetPlayerPed();
			}
		}
		return nullptr;
	}

	uint16_t CurrentPlayerModelId(uint16_t playerId)
	{
		if (CPlayerPed* ped = FindPlayerPed(playerId)) {
			if (ped->m_pPed) {
				return static_cast<uint16_t>(ped->m_pPed->m_nModelIndex);
			}
		}
		return kAnyShaderModelId;
	}

	uint16_t CurrentVehicleModelId(uint16_t vehicleId)
	{
		if (pNetGame && pNetGame->GetVehiclePool()) {
			if (CVehicle* vehicle = pNetGame->GetVehiclePool()->GetAt(vehicleId)) {
				if (vehicle->m_pVehicle) {
					return static_cast<uint16_t>(vehicle->m_pVehicle->m_nModelIndex);
				}
			}
		}
		return kAnyShaderModelId;
	}

	uint16_t CurrentObjectModelId(uint16_t objectId)
	{
		if (pNetGame && pNetGame->GetObjectPool()) {
			if (CObject* object = pNetGame->GetObjectPool()->GetAt(objectId)) {
				if (object->m_pEntity) {
					return static_cast<uint16_t>(object->m_pEntity->m_nModelIndex);
				}
			}
		}
		return kAnyShaderModelId;
	}
}

bool BrlTextureShader::HasTexture(const std::string& textureName)
{
	return ResolveTexture(textureName) != nullptr;
}

bool BrlTextureShader::SetPlayerTexture(uint16_t playerId, const std::string& sourceTexture, const std::string& replacementTexture)
{
	return SetRule({BrlShaderTargetType::Player, playerId, 0}, sourceTexture, replacementTexture, CurrentPlayerModelId(playerId));
}

bool BrlTextureShader::SetLocalPlayerTexture(const std::string& sourceTexture, const std::string& replacementTexture)
{
	if (!SetPlayerTexture(kLocalPlayerShaderId, sourceTexture, replacementTexture)) return false;
	if (CPlayerPed* ped = FindLocalPed(); ped && ped->m_pPed && ped->m_pPed->m_pRwObject) {
		ShaderRule rule{sourceTexture, replacementTexture, static_cast<uint16_t>(ped->m_pPed->m_nModelIndex)};
		ApplyContext context{static_cast<uint16_t>(ped->m_pPed->m_nModelIndex), &rule};
		ForEachAtomic(ped->m_pPed->m_pRwObject, ApplyAtomicCallback, &context);
	}
	return true;
}

bool BrlTextureShader::ClearLocalPlayerTexture(const std::string& sourceTexture)
{
	const ShaderKey key{BrlShaderTargetType::Player, kLocalPlayerShaderId, 0};
	if (!ClearRuleSource(key, sourceTexture)) return false;

	if (CPlayerPed* ped = FindLocalPed(); ped && ped->m_pPed && ped->m_pPed->m_pRwObject) {
		RestoreSourceOnObject(ped->m_pPed->m_pRwObject, ped->m_pPed->m_nModelIndex, sourceTexture);
	}

	return true;
}

bool BrlTextureShader::SetVehicleTexture(uint16_t vehicleId, const std::string& sourceTexture, const std::string& replacementTexture)
{
	return SetRule({BrlShaderTargetType::Vehicle, vehicleId, 0}, sourceTexture, replacementTexture, CurrentVehicleModelId(vehicleId));
}

bool BrlTextureShader::SetObjectTexture(uint16_t objectId, const std::string& sourceTexture, const std::string& replacementTexture)
{
	return SetRule({BrlShaderTargetType::Object, objectId, 0}, sourceTexture, replacementTexture, CurrentObjectModelId(objectId));
}

bool BrlTextureShader::SetWeaponTexture(uint16_t playerId, uint8_t weaponId, const std::string& sourceTexture, const std::string& replacementTexture)
{
	return SetRule({BrlShaderTargetType::Weapon, playerId, weaponId}, sourceTexture, replacementTexture, CurrentPlayerModelId(playerId));
}

bool BrlTextureShader::ClearPlayer(uint16_t playerId)
{
	return ClearRuleGroup({BrlShaderTargetType::Player, playerId, 0});
}

bool BrlTextureShader::ClearVehicle(uint16_t vehicleId)
{
	return ClearRuleGroup({BrlShaderTargetType::Vehicle, vehicleId, 0});
}

bool BrlTextureShader::ClearObject(uint16_t objectId)
{
	return ClearRuleGroup({BrlShaderTargetType::Object, objectId, 0});
}

bool BrlTextureShader::ClearWeapon(uint16_t playerId, uint8_t weaponId)
{
	return ClearRuleGroup({BrlShaderTargetType::Weapon, playerId, weaponId});
}

void BrlTextureShader::ClearAll()
{
	if (pNetGame) {
		if (CPlayerPool* playerPool = pNetGame->GetPlayerPool()) {
			if (CLocalPlayer* localPlayer = playerPool->GetLocalPlayer()) {
				if (CPlayerPed* ped = localPlayer->GetPlayerPed(); ped && ped->m_pPed) {
					RestoreObject(ped->m_pPed->m_pRwObject);
				}
			}

			for (uint16_t playerId = 0; playerId < MAX_PLAYERS; ++playerId) {
				CRemotePlayer* remotePlayer = playerPool->GetAt(playerId);
				if (!remotePlayer) continue;
				if (CPlayerPed* ped = remotePlayer->GetPlayerPed(); ped && ped->m_pPed) {
					RestoreObject(ped->m_pPed->m_pRwObject);
				}
			}
		}

		if (CVehiclePool* vehiclePool = pNetGame->GetVehiclePool()) {
			for (uint16_t vehicleId = 0; vehicleId < MAX_VEHICLES; ++vehicleId) {
				CVehicle* vehicle = vehiclePool->GetAt(vehicleId);
				if (vehicle && vehicle->m_pVehicle) RestoreObject(vehicle->m_pVehicle->m_pRwObject);
			}
		}

		if (CObjectPool* objectPool = pNetGame->GetObjectPool()) {
			for (uint16_t objectId = 0; objectId < MAX_OBJECTS; ++objectId) {
				CObject* object = objectPool->GetAt(objectId);
				if (object && object->m_pEntity) RestoreObject(object->m_pEntity->m_pRwObject);
			}
		}
	}
	else if (pGame && pGame->FindPlayerPed() && pGame->FindPlayerPed()->m_pPed) {
		RestoreObject(pGame->FindPlayerPed()->m_pPed->m_pRwObject);
	}

	std::lock_guard lock(g_shaderMutex);
	g_rules.clear();
	g_textureCache.clear();
	g_originalMaterialTextures.clear();
	g_originalMaterialNames.clear();
}

void BrlTextureShader::ApplyToPed(uint16_t playerId, CPlayerPed* ped)
{
	if (!ped || !ped->m_pPed || !ped->m_pPed->m_pRwObject) return;

	const uint16_t rulePlayerId = playerId == kLocalPlayerShaderId ? kLocalPlayerShaderId : playerId;
	ApplyRulesToObject({BrlShaderTargetType::Player, rulePlayerId, 0}, ped->m_pPed->m_pRwObject, ped->m_pPed->m_nModelIndex);

	const uint8_t currentWeapon = ped->GetCurrentWeapon();
	ApplyRulesToObject({BrlShaderTargetType::Weapon, rulePlayerId, currentWeapon}, ped->m_pPed->m_pRwObject, ped->m_pPed->m_nModelIndex);
}

void BrlTextureShader::ApplyToVehicle(uint16_t vehicleId, CVehicle* vehicle)
{
	if (!vehicle || !vehicle->m_pVehicle || !vehicle->m_pVehicle->m_pRwObject) return;
	ApplyRulesToObject({BrlShaderTargetType::Vehicle, vehicleId, 0}, vehicle->m_pVehicle->m_pRwObject, vehicle->m_pVehicle->m_nModelIndex);
}

void BrlTextureShader::ApplyToObject(uint16_t objectId, CObject* object)
{
	if (!object || !object->m_pEntity || !object->m_pEntity->m_pRwObject) return;
	ApplyRulesToObject({BrlShaderTargetType::Object, objectId, 0}, object->m_pEntity->m_pRwObject, object->m_pEntity->m_nModelIndex);
}

bool BrlTextureShader::ApplyToRwObject(RwObject* rwObject, const std::string& sourceTexture, const std::string& replacementTexture)
{
	if (!rwObject || sourceTexture.empty() || replacementTexture.empty()) return false;
	ShaderRule rule{sourceTexture, replacementTexture};
	ApplyContext context{0, &rule};
	ForEachAtomic(rwObject, ApplyAtomicCallback, &context);
	return true;
}

namespace {
	struct DebugTextureList {
		std::set<std::string> names;
		int maxTextures{};
	};

	RpAtomic* CollectTexturesAtomicCallback(RpAtomic* atomic, void* data)
	{
		auto* list = static_cast<DebugTextureList*>(data);
		if (!atomic || !list || *(uint8_t*) atomic != rpATOMIC) return atomic;

		RpGeometry* geometry = atomic->geometry;
		if (!geometry || geometry->matList.numMaterials <= 0 || !geometry->matList.materials) return atomic;

		for (int i = 0; i < geometry->matList.numMaterials; ++i) {
			RpMaterial* material = geometry->matList.materials[i];
			if (!material || !material->texture) continue;
			list->names.insert(material->texture->name);
			if (static_cast<int>(list->names.size()) >= list->maxTextures) break;
		}

		return atomic;
	}
}

std::string BrlTextureShader::DebugRwObjectTextures(RwObject* rwObject, int maxTextures)
{
	if (!rwObject) return "sem RwObject";
	DebugTextureList list;
	list.maxTextures = std::max(1, maxTextures);
	ForEachAtomic(rwObject, CollectTexturesAtomicCallback, &list);

	std::ostringstream out;
	out << "tipo=" << static_cast<int>(*(uint8_t*) rwObject) << " texturas=" << list.names.size() << ": ";
	bool first = true;
	for (const auto& name : list.names) {
		if (!first) out << ", ";
		out << name;
		first = false;
	}
	return out.str();
}

void BrlTextureShader::Process()
{
	static uint32_t lastProcess = 0;
	uint32_t now = GetTickCount();
	if (now - lastProcess < kProcessIntervalMs) return;
	lastProcess = now;

	if (!pNetGame) {
		if (pGame && pGame->FindPlayerPed()) {
			ApplyToPed(kLocalPlayerShaderId, pGame->FindPlayerPed());
		}
		return;
	}

	CPlayerPool* playerPool = pNetGame->GetPlayerPool();
	if (playerPool) {
		if (CLocalPlayer* localPlayer = playerPool->GetLocalPlayer()) {
			ApplyToPed(kLocalPlayerShaderId, localPlayer->GetPlayerPed());
		}

		for (uint16_t playerId = 0; playerId < MAX_PLAYERS; ++playerId) {
			CRemotePlayer* remotePlayer = playerPool->GetAt(playerId);
			if (remotePlayer) {
				ApplyToPed(playerId, remotePlayer->GetPlayerPed());
			}
		}
	}

	if (CVehiclePool* vehiclePool = pNetGame->GetVehiclePool()) {
		for (uint16_t vehicleId = 0; vehicleId < MAX_VEHICLES; ++vehicleId) {
			CVehicle* vehicle = vehiclePool->GetAt(vehicleId);
			if (vehicle) ApplyToVehicle(vehicleId, vehicle);
		}
	}

	if (CObjectPool* objectPool = pNetGame->GetObjectPool()) {
		for (uint16_t objectId = 0; objectId < MAX_OBJECTS; ++objectId) {
			CObject* object = objectPool->GetAt(objectId);
			if (object) ApplyToObject(objectId, object);
		}
	}
}
