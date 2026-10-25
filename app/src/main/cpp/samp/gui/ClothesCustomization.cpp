#include "ClothesCustomization.h"

#include <algorithm>
#include <array>
#include <cstdio>
#include <string>
#include <unordered_map>

#include "../main.h"
#include "../game/game.h"
#include "../game/playerped.h"
#include "../game/BrlTextureShader.h"
#include "../vendor/imgui/imgui.h"

extern CGame* pGame;

namespace {

	ClothesLook g_look;

	constexpr std::array<int, 7> kFaceCountByTone = {0, 6, 8, 8, 7, 5, 12};
	constexpr std::array<int, 7> kFemaleFaceCountByTone = {0, 6, 8, 8, 7, 6, 12};
	constexpr int kDffFaceSlots = 5;

	struct ClothingOption {
		const char* material;
		const char* texturePrefix;
		int maxValue;
	};

	struct FemaleClothingOption {
		std::array<const char*, 2> materials;
		std::array<const char*, 2> texturePrefixes;
		int materialCount;
		int maxValue;
	};

	constexpr std::array<ClothingOption, 3> kHairStyles = {{
			{"fantasy_m_hair1", "fantasy_m_hair1", 6},
			{"fantasy_m_hair2", "fantasy_m_hair2", 6},
			{"fantasy_m_hair3", "fantasy_m_hair3", 5}
	}};

	constexpr std::array<ClothingOption, 3> kTopStyles = {{
			{"fantasy_m_torso1", "fantasy_m_torso1", 26},
			{"fantasy_m_torso2", "fantasy_m_torso2", 35},
			{"fantasy_m_torso3", "fantasy_m_torso3", 35}
	}};

	constexpr std::array<ClothingOption, 3> kBottomStyles = {{
			{"fantasy_m_pants1", "fantasy_m_pants1", 17},
			{"fantasy_m_short1", "fantasy_m_short1", 34},
			{"fantasy_m_underwear", "fantasy_m_underwear", 16}
	}};

	constexpr std::array<ClothingOption, 7> kShoesStyles = {{
			{"fantasy_m_boot1", "fantasy_m_boot1", 16},
			{"fantasy_m_boot2", "fantasy_m_boot2", 55},
			{"fantasy_m_boot3", "fantasy_m_boot3", 23},
			{"fantasy_m_boot4", "fantasy_m_boot4", 40},
			{"fantasy_m_slipper1", "fantasy_m_slipper1", 24},
			{"fantasy_m_slipper2", "fantasy_m_slipper2", 15},
			{"fantasy_m_slipper3", "fantasy_m_slipper3", 21}
	}};

	constexpr std::array<ClothingOption, 3> kPoliceMaleHairStyles = {{
			{"fantasy_m_hair1", "fantasy_m_hair1", 6},
			{"fantasy_m_hair2", "fantasy_m_hair2", 6},
			{"fantasy_m_hair3", "fantasy_m_hair3", 5}
	}};

	constexpr std::array<ClothingOption, 2> kPoliceMaleTopStyles = {{
			{"fantasy_m_cop_torso2", "fantasy_m_cop_torso2", 1},
			{"fantasy_m_cop_torso4", "fantasy_m_cop_torso4", 2}
	}};

	constexpr std::array<ClothingOption, 2> kPoliceMaleBottomStyles = {{
			{"fantasy_m_underwear", "fantasy_m_underwear", 1},
			{"fantasy_m_cop_pants1", "fantasy_m_cop_pants1", 1}
	}};

	constexpr std::array<ClothingOption, 2> kPoliceMaleShoesStyles = {{
			{"fantasy_m_cop_boot1", "fantasy_m_cop_boot1", 1},
			{"fantasy_m_cop_boot2", "fantasy_m_cop_boot2", 1}
	}};

	constexpr std::array<FemaleClothingOption, 5> kFemaleHairStyles = {{
			{{"fantasy_f_hair1", nullptr}, {"fantasy_f_hair1", nullptr}, 1, 6},
			{{"fantasy_f_hair2", nullptr}, {"fantasy_f_hair2", nullptr}, 1, 6},
			{{"fantasy_f_hair3", nullptr}, {"fantasy_f_hair3", nullptr}, 1, 7},
			{{"fantasy_f_hair4", nullptr}, {"fantasy_f_hair4", nullptr}, 1, 7},
			{{"fantasy_f_hair5", nullptr}, {"fantasy_f_hair5", nullptr}, 1, 7}
	}};

	constexpr std::array<FemaleClothingOption, 4> kFemaleTopStyles = {{
			{{"fantasy_f_bikinitop", nullptr}, {"fantasy_f_bikinitop", nullptr}, 1, 12},
			{{"fantasy_f_torso5", nullptr}, {"fantasy_f_torso5", nullptr}, 1, 35},
			{{"fantasy_f_torso2", nullptr}, {"fantasy_f_torso2", nullptr}, 1, 20},
			{{"fantasy_f_torso6", nullptr}, {"fantasy_f_torso6", nullptr}, 1, 20}
	}};

	constexpr std::array<FemaleClothingOption, 4> kFemaleBottomStyles = {{
			{{"fantasy_f_bikinibotton", nullptr}, {"fantasy_f_bikinibotton", nullptr}, 1, 12},
			{{"fantasy_f_pants1", nullptr}, {"fantasy_f_pants1", nullptr}, 1, 16},
			{{"fantasy_f_pants5", nullptr}, {"fantasy_f_pants5", nullptr}, 1, 40},
			{{"fantasy_f_short1", nullptr}, {"fantasy_f_short1", nullptr}, 1, 11}
	}};

	constexpr std::array<FemaleClothingOption, 4> kFemaleShoesStyles = {{
			{{"fantasy_f_boot3", nullptr}, {"fantasy_f_boot3", nullptr}, 1, 16},
			{{"fantasy_f_boot5", nullptr}, {"fantasy_f_boot5", nullptr}, 1, 16},
			{{"fantasy_f_slipper1", nullptr}, {"fantasy_f_slipper1", nullptr}, 1, 24},
			{{"fantasy_f_slipper3", nullptr}, {"fantasy_f_slipper3", nullptr}, 1, 21}
	}};

	constexpr std::array<FemaleClothingOption, 5> kPoliceFemaleHairStyles = {{
			{{"fantasy_f_hair1", nullptr}, {"fantasy_f_hair1", nullptr}, 1, 6},
			{{"fantasy_f_hair2", nullptr}, {"fantasy_f_hair2", nullptr}, 1, 6},
			{{"fantasy_f_hair3", nullptr}, {"fantasy_f_hair3", nullptr}, 1, 7},
			{{"fantasy_f_hair4", nullptr}, {"fantasy_f_hair4", nullptr}, 1, 7},
			{{"fantasy_f_hair5", nullptr}, {"fantasy_f_hair5", nullptr}, 1, 7}
	}};

	constexpr std::array<FemaleClothingOption, 2> kPoliceFemaleTopStyles = {{
			{{"fantasy_f_cop_torso2", nullptr}, {"fantasy_f_cop_torso2", nullptr}, 1, 1},
			{{"fantasy_f_cop_torso4", nullptr}, {"fantasy_f_cop_torso4", nullptr}, 1, 2}
	}};

	constexpr std::array<FemaleClothingOption, 2> kPoliceFemaleBottomStyles = {{
			{{"fantasy_f_cop_pants1", nullptr}, {"fantasy_f_cop_pants1", nullptr}, 1, 1},
			{{"fantasy_f_cop_short1", nullptr}, {"fantasy_f_cop_short1", nullptr}, 1, 1}
	}};

	constexpr std::array<FemaleClothingOption, 2> kPoliceFemaleShoesStyles = {{
			{{"fantasy_f_cop_boot1", nullptr}, {"fantasy_f_cop_boot1", nullptr}, 1, 1},
			{{"fantasy_f_cop_boot2", nullptr}, {"fantasy_f_cop_boot2", nullptr}, 1, 1}
	}};

	constexpr std::array<const char*, 9> kTorsoMaterials = {
			"body_torso1", "body_torso2", "body_torso3", "body_torso4",
			"body_armright", "body_armleft", "body_shoulderleft", "body_shoulderright",
			"body_hand"
	};

	constexpr std::array<const char*, 12> kLegMaterials = {
			"body_legleft1", "body_legleft2", "body_legleft3", "body_legleft4", "body_legleft5", "body_legleft6",
			"body_legright1", "body_legright2", "body_legright3", "body_legright4", "body_legright5", "body_legright6"
	};

	constexpr std::array<const char*, 4> kFeetMaterials = {
			"body_feetleft", "body_feetright", "body_feets", "body_feets2"
	};

	constexpr std::array<const char*, 9> kFemaleTorsoMaterials = {
			"body_torso1", "body_torso2", "body_torso3", "body_torso4", "body_armright",
			"body_armleft", "body_shoulderleft", "body_shoulderright", "body_hand"
	};

	constexpr std::array<const char*, 12> kFemaleLegMaterials = {
			"body_legleft1", "body_legleft2", "body_legleft3", "body_legleft4", "body_legleft5", "body_legleft6",
			"body_legright1", "body_legright2", "body_legright3", "body_legright4", "body_legright5", "body_legright6"
	};

	constexpr std::array<const char*, 4> kFemaleFeetMaterials = {
			"body_feetleft", "body_feetright", "body_feets", "body_feets2"
	};

	constexpr std::array<const char*, 9> kPoliceMaleTorsoMaterials = {
			"body_torso1", "body_torso2", "body_torso3", "body_torso4", "body_armright",
			"body_armleft", "body_shoulderleft", "body_shoulderright", "body_hand"
	};

	constexpr std::array<const char*, 12> kPoliceMaleLegMaterials = {
			"body_legleft1", "body_legleft2", "body_legleft3", "body_legleft4", "body_legleft5", "body_legleft6",
			"body_legright1", "body_legright2", "body_legright3", "body_legright4", "body_legright5", "body_legright6"
	};

	constexpr std::array<const char*, 4> kPoliceMaleFeetMaterials = {
			"body_feetleft", "body_feetright", "body_feets", "body_feets2"
	};

	constexpr std::array<const char*, 25> kBodySkinMaterials = {
			"body_torso1", "body_torso2", "body_torso3", "body_torso4",
			"body_armright", "body_armleft", "body_shoulderleft", "body_shoulderright", "body_hand",
			"body_legleft1", "body_legleft2", "body_legleft3", "body_legleft4", "body_legleft5", "body_legleft6",
			"body_legright1", "body_legright2", "body_legright3", "body_legright4", "body_legright5", "body_legright6",
			"body_feetleft", "body_feetright", "body_feets", "body_feets2"
	};

	constexpr std::array<const char*, 16> kAllClothingMaterials = {
			"fantasy_m_hair1", "fantasy_m_hair2", "fantasy_m_hair3",
			"fantasy_m_torso1", "fantasy_m_torso2", "fantasy_m_torso3",
			"fantasy_m_pants1", "fantasy_m_short1", "fantasy_m_underwear",
			"fantasy_m_boot1", "fantasy_m_boot2", "fantasy_m_boot3", "fantasy_m_boot4",
			"fantasy_m_slipper1", "fantasy_m_slipper2", "fantasy_m_slipper3"
	};

	constexpr int kMaleEyebrowStyleCount = 5;
	constexpr int kMaleBeardStyleCount = 5;
	constexpr int kMaleEyebrowColorCount = 54;
	constexpr int kMaleBeardColorCount = 54;
	constexpr int kMaleTattooArmCount = 38;
	constexpr int kMaleTattooLegCount = 26;
	constexpr int kMaleTattooBackCount = 16;
	constexpr int kMaleTattooFrontCount = 25;
	constexpr int kFemaleMakeupStyleCount = 5;
	constexpr int kFemaleMakeupColorCount = 26;
	constexpr const char* kClearTexture = "brl_m7_clear_alpha";

	constexpr std::array<const char*, kMaleEyebrowStyleCount> kMaleEyebrowMaterials = {
			"body_eyebrown1", "body_eyebrown2", "body_eyebrown3", "body_eyebrown4", "body_eyebrown5"
	};

	constexpr std::array<const char*, kMaleBeardStyleCount> kMaleBeardMaterials = {
			"body_beards1", "body_beards2", "body_beards3", "body_beards4", "body_beards5"
	};

	constexpr std::array<const char*, kFemaleMakeupStyleCount> kFemaleMakeupMaterials = {
			"body_makeup1", "body_makeup2", "body_makeup3", "body_makeup4", "body_makeup5"
	};

	constexpr std::array<std::array<const char*, 3>, kDffFaceSlots> kFaceMaterialSlots = {{
			{"body_head1", "body_eyebrown1", "body_beards1"},
			{"body_head2", "body_eyebrown2", "body_beards2"},
			{"body_head3", "body_eyebrown3", "body_beards3"},
			{"body_head4", "body_eyebrown4", "body_beards4"},
			{"body_head5", "body_eyebrown5", "body_beards5"}
	}};

	constexpr std::array<const char*, kDffFaceSlots * 3> kAllFaceMaterials = {
			"body_head1", "body_eyebrown1", "body_beards1",
			"body_head2", "body_eyebrown2", "body_beards2",
			"body_head3", "body_eyebrown3", "body_beards3",
			"body_head4", "body_eyebrown4", "body_beards4",
			"body_head5", "body_eyebrown5", "body_beards5"
	};

	constexpr const char* kFemaleStableFaceMaterial = "body_head5";
	constexpr std::array<const char*, kDffFaceSlots> kPoliceMaleFaceMaterials = {
			"body_head1", "body_head2", "body_head3", "body_head4", "body_head5"
	};

	std::string MobileTextureName(const char* suffix, int tone, int face = 0)
	{
		char name[64]{};
		if (face > 0) {
			std::snprintf(name, sizeof(name), "brl_m7_skin_t%02d_%s%02d", tone, suffix, face);
		}
		else {
			std::snprintf(name, sizeof(name), "brl_m7_skin_t%02d_%s", tone, suffix);
		}
		return name;
	}

	std::string FemaleMobileTextureName(const char* suffix, int tone, int face = 0)
	{
		char name[64]{};
		if (face > 0) {
			std::snprintf(name, sizeof(name), "brl_f9_skin_t%02d_%s%02d", tone, suffix, face);
		}
		else {
			std::snprintf(name, sizeof(name), "brl_f9_skin_t%02d_%s", tone, suffix);
		}
		return name;
	}

	std::string ClothingTextureName(const char* texturePrefix, int value)
	{
		char name[96]{};
		std::snprintf(name, sizeof(name), "brl_m7_clothes_%s_%02d", texturePrefix, value);
		return name;
	}

	std::string FemaleClothingTextureName(const char* texturePrefix, int value)
	{
		char name[96]{};
		std::snprintf(name, sizeof(name), "brl_f9_clothes_%s_%02d", texturePrefix, value);
		return name;
	}

	std::string PoliceMaleTextureName(const char* texturePrefix, int value)
	{
		char name[112]{};
		std::snprintf(name, sizeof(name), "brl_m271_police_%s_%02d", texturePrefix, value);
		return name;
	}

	std::string PoliceFemaleTextureName(const char* texturePrefix, int value)
	{
		char name[112]{};
		std::snprintf(name, sizeof(name), "brl_f272_police_%s_%02d", texturePrefix, value);
		return name;
	}

	std::string MaleEyebrowTextureName(int value)
	{
		char name[64]{};
		std::snprintf(name, sizeof(name), "brl_m7_eyebrown_%02d", value);
		return name;
	}

	std::string MaleBeardTextureName(int value)
	{
		char name[64]{};
		std::snprintf(name, sizeof(name), "brl_m7_beard_%02d", value);
		return name;
	}

	std::string MaleTattooTextureName(const char* area, int value)
	{
		char name[64]{};
		std::snprintf(name, sizeof(name), "brl_m7_tattoo_%s_%02d", area, value);
		return name;
	}

	// Clamp para campos uint8_t do ClothesLook (evita conflito de tipos no std::clamp).
	uint8_t ClampU8(int value, int lo, int hi)
	{
		return static_cast<uint8_t>(std::clamp(value, lo, hi));
	}

	void ClampSelection()
	{
		g_look.skinTone = ClampU8(g_look.skinTone, 1, 6);
		g_look.face = ClampU8(g_look.face, 1, std::min(kFaceCountByTone[g_look.skinTone], kDffFaceSlots));
		g_look.hairStyle = ClampU8(g_look.hairStyle, 1, static_cast<int>(kHairStyles.size()));
		// topStyle/shoesStyle: 0 = sem camisa / descalço (começa só de cueca)
		g_look.topStyle = ClampU8(g_look.topStyle, 0, static_cast<int>(kTopStyles.size()));
		g_look.bottomStyle = ClampU8(g_look.bottomStyle, 1, static_cast<int>(kBottomStyles.size()));
		g_look.shoesStyle = ClampU8(g_look.shoesStyle, 0, static_cast<int>(kShoesStyles.size()));
		g_look.hairColor = ClampU8(g_look.hairColor, 1, kHairStyles[g_look.hairStyle - 1].maxValue);
		if (g_look.topStyle > 0) {
			g_look.topColor = ClampU8(g_look.topColor, 1, kTopStyles[g_look.topStyle - 1].maxValue);
		}
		g_look.bottomColor = ClampU8(g_look.bottomColor, 1, kBottomStyles[g_look.bottomStyle - 1].maxValue);
		if (g_look.shoesStyle > 0) {
			g_look.shoesColor = ClampU8(g_look.shoesColor, 1, kShoesStyles[g_look.shoesStyle - 1].maxValue);
		}
		g_look.eyebrowStyle = ClampU8(g_look.eyebrowStyle, 0, kMaleEyebrowStyleCount);
		g_look.eyebrowColor = ClampU8(g_look.eyebrowColor, 1, kMaleEyebrowColorCount);
		g_look.beardStyle = ClampU8(g_look.beardStyle, 0, kMaleBeardStyleCount);
		g_look.beardColor = ClampU8(g_look.beardColor, 1, kMaleBeardColorCount);
		g_look.tattooArmLeft = ClampU8(g_look.tattooArmLeft, 0, kMaleTattooArmCount);
		g_look.tattooArmRight = ClampU8(g_look.tattooArmRight, 0, kMaleTattooArmCount);
		g_look.tattooLegLeft = ClampU8(g_look.tattooLegLeft, 0, kMaleTattooLegCount);
		g_look.tattooLegRight = ClampU8(g_look.tattooLegRight, 0, kMaleTattooLegCount);
		g_look.tattooBack = ClampU8(g_look.tattooBack, 0, kMaleTattooBackCount);
		g_look.tattooFront = ClampU8(g_look.tattooFront, 0, kMaleTattooFrontCount);
	}

	void ClampFemaleSelection()
	{
		g_look.skinTone = ClampU8(g_look.skinTone, 1, 6);
		g_look.face = ClampU8(g_look.face, 1, kFemaleFaceCountByTone[g_look.skinTone]);
		g_look.hairStyle = ClampU8(g_look.hairStyle, 1, static_cast<int>(kFemaleHairStyles.size()));
		g_look.topStyle = ClampU8(g_look.topStyle, 1, static_cast<int>(kFemaleTopStyles.size()));
		g_look.bottomStyle = ClampU8(g_look.bottomStyle, 1, static_cast<int>(kFemaleBottomStyles.size()));
		g_look.shoesStyle = ClampU8(g_look.shoesStyle, 1, static_cast<int>(kFemaleShoesStyles.size()));
		g_look.hairColor = ClampU8(g_look.hairColor, 1, kFemaleHairStyles[g_look.hairStyle - 1].maxValue);
		g_look.topColor = ClampU8(g_look.topColor, 1, kFemaleTopStyles[g_look.topStyle - 1].maxValue);
		g_look.bottomColor = ClampU8(g_look.bottomColor, 1, kFemaleBottomStyles[g_look.bottomStyle - 1].maxValue);
		g_look.shoesColor = ClampU8(g_look.shoesColor, 1, kFemaleShoesStyles[g_look.shoesStyle - 1].maxValue);
		g_look.makeupStyle = ClampU8(g_look.makeupStyle, 0, kFemaleMakeupStyleCount);
		g_look.makeupColor = ClampU8(g_look.makeupColor, 1, kFemaleMakeupColorCount);
	}

	void ClampPoliceMaleSelection()
	{
		g_look.skinTone = ClampU8(g_look.skinTone, 1, 6);
		g_look.face = ClampU8(g_look.face, 1, std::min(kFaceCountByTone[g_look.skinTone], kDffFaceSlots));
		g_look.hairStyle = ClampU8(g_look.hairStyle, 1, static_cast<int>(kPoliceMaleHairStyles.size()));
		g_look.topStyle = ClampU8(g_look.topStyle, 1, static_cast<int>(kPoliceMaleTopStyles.size()));
		g_look.bottomStyle = ClampU8(g_look.bottomStyle, 1, static_cast<int>(kPoliceMaleBottomStyles.size()));
		g_look.shoesStyle = ClampU8(g_look.shoesStyle, 1, static_cast<int>(kPoliceMaleShoesStyles.size()));
		g_look.hairColor = ClampU8(g_look.hairColor, 1, kPoliceMaleHairStyles[g_look.hairStyle - 1].maxValue);
		g_look.topColor = ClampU8(g_look.topColor, 1, kPoliceMaleTopStyles[g_look.topStyle - 1].maxValue);
		g_look.bottomColor = ClampU8(g_look.bottomColor, 1, kPoliceMaleBottomStyles[g_look.bottomStyle - 1].maxValue);
		g_look.shoesColor = ClampU8(g_look.shoesColor, 1, kPoliceMaleShoesStyles[g_look.shoesStyle - 1].maxValue);
		g_look.policeBelt = ClampU8(g_look.policeBelt, 0, 1);
		g_look.policeVest = ClampU8(g_look.policeVest, 0, 1);
		g_look.policeHat = ClampU8(g_look.policeHat, 0, 1);
	}

	void ClampPoliceFemaleSelection()
	{
		g_look.skinTone = ClampU8(g_look.skinTone, 1, 6);
		g_look.face = ClampU8(g_look.face, 1, kFemaleFaceCountByTone[g_look.skinTone]);
		g_look.hairStyle = ClampU8(g_look.hairStyle, 1, static_cast<int>(kPoliceFemaleHairStyles.size()));
		g_look.topStyle = ClampU8(g_look.topStyle, 1, static_cast<int>(kPoliceFemaleTopStyles.size()));
		g_look.bottomStyle = ClampU8(g_look.bottomStyle, 1, static_cast<int>(kPoliceFemaleBottomStyles.size()));
		g_look.shoesStyle = ClampU8(g_look.shoesStyle, 1, static_cast<int>(kPoliceFemaleShoesStyles.size()));
		g_look.hairColor = ClampU8(g_look.hairColor, 1, kPoliceFemaleHairStyles[g_look.hairStyle - 1].maxValue);
		g_look.topColor = ClampU8(g_look.topColor, 1, kPoliceFemaleTopStyles[g_look.topStyle - 1].maxValue);
		g_look.bottomColor = ClampU8(g_look.bottomColor, 1, kPoliceFemaleBottomStyles[g_look.bottomStyle - 1].maxValue);
		g_look.shoesColor = ClampU8(g_look.shoesColor, 1, kPoliceFemaleShoesStyles[g_look.shoesStyle - 1].maxValue);
		g_look.policeBelt = ClampU8(g_look.policeBelt, 0, 1);
		g_look.policeVest = ClampU8(g_look.policeVest, 0, 1);
		g_look.policeHat = ClampU8(g_look.policeHat, 0, 1);
	}

	uint16_t CurrentLocalModelId()
	{
		if (!pGame) return 0;
		CPlayerPed* ped = pGame->FindPlayerPed();
		if (!ped || !ped->m_pPed) return 0;
		return static_cast<uint16_t>(ped->m_pPed->m_nModelIndex);
	}

	bool CurrentLocalPedReady()
	{
		if (!pGame) return false;
		CPlayerPed* ped = pGame->FindPlayerPed();
		return ped && ped->m_pPed && ped->m_pPed->m_pRwObject;
	}

	bool IsFemaleSkin()
	{
		return CurrentLocalModelId() == 9;
	}

	bool IsPoliceFemaleSkin()
	{
		return CurrentLocalModelId() == 246;
	}

	bool IsPoliceMaleSkin()
	{
		return CurrentLocalModelId() == 280;
	}

	bool IsBodyMaterialHidden(const char* material)
	{
		const std::string value = material ? material : "";
		if (IsFemaleSkin()) {
			std::string femaleValue = value;
			std::replace(femaleValue.begin(), femaleValue.end(), '.', '_');
			if (g_look.bottomStyle == 2 || g_look.bottomStyle == 3) {
				if (femaleValue == "body_legright1" || femaleValue == "body_legleft1" ||
						femaleValue == "body_legright2" || femaleValue == "body_legleft2" ||
						femaleValue == "body_legright3" || femaleValue == "body_legleft3") return true;
			}
			if (g_look.bottomStyle == 4) {
				if (femaleValue == "body_legleft1" || femaleValue == "body_legright1") return true;
			}
			if (g_look.bottomStyle != 3) {
				if (femaleValue == "body_legleft6" || femaleValue == "body_legright6") return true;
			}
			if (g_look.shoesStyle == 1 || g_look.shoesStyle == 2 || g_look.shoesStyle == 4) {
				if (femaleValue == "body_feets") return true;
			}
			if (g_look.shoesStyle == 2) {
				if (femaleValue == "body_legright5" || femaleValue == "body_legleft5") return true;
			}
			if (g_look.topStyle == 2) {
				if (femaleValue == "body_torso1" || femaleValue == "body_torso3" ||
						femaleValue == "body_shoulderleft" || femaleValue == "body_shoulderright" ||
						femaleValue == "body_armright" || femaleValue == "body_armleft") return true;
			}
			if (g_look.topStyle == 4) {
				if (femaleValue == "body_torso4" || femaleValue == "body_torso3" ||
						femaleValue == "body_legleft1" || femaleValue == "body_legright1") return true;
			}
			return false;
		}

		if (IsPoliceFemaleSkin()) {
			std::string femaleValue = value;
			std::replace(femaleValue.begin(), femaleValue.end(), '.', '_');
			if (g_look.bottomStyle == 1) {
				if (femaleValue == "body_legleft1" || femaleValue == "body_legright1" ||
						femaleValue == "body_legright2" || femaleValue == "body_legleft2" ||
						femaleValue == "body_legright3" || femaleValue == "body_legleft3" ||
						femaleValue == "body_legleft4" || femaleValue == "body_legright4") return true;
			}
			if (g_look.bottomStyle == 2) {
				if (femaleValue == "body_legleft1" || femaleValue == "body_legright1" ||
						femaleValue == "body_legright2" || femaleValue == "body_legleft2") return true;
			}
			if (g_look.shoesStyle >= 1 && g_look.shoesStyle <= static_cast<int>(kPoliceFemaleShoesStyles.size())) {
				if (femaleValue == "body_legright5" || femaleValue == "body_legleft5" || femaleValue == "body_feets") return true;
			}
			if (g_look.topStyle == 1) {
				if (femaleValue == "body_torso1" || femaleValue == "body_torso3" ||
						femaleValue == "body_shoulderleft" || femaleValue == "body_shoulderright" ||
						femaleValue == "body_torso4") return true;
			}
			if (g_look.topStyle == 2) {
				if (femaleValue == "body_torso1" || femaleValue == "body_shoulderleft" ||
						femaleValue == "body_shoulderright" || femaleValue == "body_torso3" ||
						femaleValue == "body_torso4" || femaleValue == "body_armright" ||
						femaleValue == "body_armleft" || femaleValue == "fantasy_tatto_arm_l" ||
						femaleValue == "fantasy_tatto_arm_r") return true;
			}
			return false;
		}

		if (IsPoliceMaleSkin()) {
			std::string policeMaleValue = value;
			std::replace(policeMaleValue.begin(), policeMaleValue.end(), '.', '_');
			if (g_look.topStyle == 1) {
				if (policeMaleValue == "body_shoulderleft" || policeMaleValue == "body_shoulderright" ||
						policeMaleValue == "body_torso1" || policeMaleValue == "fantasy_tatto_torso_back" ||
						policeMaleValue == "fantasy_tatto_torso_front") return true;
			}
			if (g_look.topStyle == 2) {
				if (policeMaleValue == "body_shoulderleft" || policeMaleValue == "body_shoulderright" ||
						policeMaleValue == "body_armleft" || policeMaleValue == "body_armright" ||
						policeMaleValue == "fantasy_tatto_arm_l" || policeMaleValue == "fantasy_tatto_arm_r") return true;
			}
			if (g_look.bottomStyle == 2) {
				if (policeMaleValue == "body_legleft1" || policeMaleValue == "body_legleft2" ||
						policeMaleValue == "body_legleft3" || policeMaleValue == "body_legright1" ||
						policeMaleValue == "body_legright2" || policeMaleValue == "body_legright3" ||
						policeMaleValue == "fantasy_tatto_leg_l_3" || policeMaleValue == "fantasy_tatto_leg_r_3") return true;
			}
			if (g_look.shoesStyle >= 1 && g_look.shoesStyle <= 2) {
				if (policeMaleValue == "body_feetright" || policeMaleValue == "body_feetleft") return true;
			}
			return false;
		}

		std::string maleValue = value;
		std::replace(maleValue.begin(), maleValue.end(), '.', '_');
		if (g_look.topStyle == 3) {
			if (maleValue == "body_shoulderright" || maleValue == "body_shoulderleft" ||
					maleValue == "body_armleft" || maleValue == "body_armright") return true;
		}
		if (g_look.topStyle == 2) {
			if (maleValue == "body_shoulderright" || maleValue == "body_shoulderleft" || maleValue == "body_torso1") return true;
		}
		if (g_look.bottomStyle == 1) {
			if (maleValue == "body_legright1" || maleValue == "body_legleft1" ||
					maleValue == "body_legleft2" || maleValue == "body_legright2" ||
					maleValue == "body_legleft3" || maleValue == "body_legright3") return true;
		}
		if (g_look.bottomStyle == 2) {
			if (maleValue == "body_legleft1" || maleValue == "body_legright1") return true;
		}
		if (g_look.shoesStyle >= 1 && g_look.shoesStyle <= 4) {
			if (maleValue == "body_feetright" || maleValue == "body_feetleft") return true;
		}
		return false;
	}

	void WantTexture(std::unordered_map<std::string, std::string>& wanted, const char* material, const std::string& texture)
	{
		if (!material || texture.empty()) return;
		wanted[material] = texture;
	}

	void WantList(std::unordered_map<std::string, std::string>& wanted, const auto& materials, const std::string& texture)
	{
		for (const char* material : materials) {
			if (!IsBodyMaterialHidden(material)) {
				WantTexture(wanted, material, texture);
			}
		}
	}

	void WantClearList(std::unordered_map<std::string, std::string>& wanted, const auto& materials)
	{
		for (const char* material : materials) {
			WantTexture(wanted, material, kClearTexture);
		}
	}

	void WantClothing(std::unordered_map<std::string, std::string>& wanted, const ClothingOption& option, int color)
	{
		const int safeColor = std::clamp(color, 1, option.maxValue);
		WantTexture(wanted, option.material, ClothingTextureName(option.texturePrefix, safeColor));
	}

	void WantPoliceMaleClothing(std::unordered_map<std::string, std::string>& wanted, const ClothingOption& option, int color)
	{
		const int safeColor = std::clamp(color, 1, option.maxValue);
		WantTexture(wanted, option.material, PoliceMaleTextureName(option.texturePrefix, safeColor));
	}

	void WantFemaleClothing(std::unordered_map<std::string, std::string>& wanted, const FemaleClothingOption& option, int color)
	{
		const int safeColor = std::clamp(color, 1, option.maxValue);
		for (int i = 0; i < option.materialCount; ++i) {
			const char* material = option.materials[i];
			const char* texturePrefix = option.texturePrefixes[i] ? option.texturePrefixes[i] : material;
			WantTexture(wanted, material, FemaleClothingTextureName(texturePrefix, safeColor));
		}
	}

	void WantPoliceFemaleClothing(std::unordered_map<std::string, std::string>& wanted, const FemaleClothingOption& option, int color)
	{
		const int safeColor = std::clamp(color, 1, option.maxValue);
		for (int i = 0; i < option.materialCount; ++i) {
			const char* material = option.materials[i];
			const char* texturePrefix = option.texturePrefixes[i] ? option.texturePrefixes[i] : material;
			WantTexture(wanted, material, PoliceFemaleTextureName(texturePrefix, safeColor));
		}
	}

	void ApplyMaleFaceAccessoriesAndTattoos(std::unordered_map<std::string, std::string>& wanted)
	{
		for (const char* material : kMaleEyebrowMaterials) {
			WantTexture(wanted, material, kClearTexture);
		}
		if (g_look.eyebrowStyle > 0) {
			WantTexture(wanted, kMaleEyebrowMaterials[g_look.eyebrowStyle - 1], MaleEyebrowTextureName(g_look.eyebrowColor));
		}

		for (const char* material : kMaleBeardMaterials) {
			WantTexture(wanted, material, kClearTexture);
		}
		if (g_look.beardStyle > 0) {
			WantTexture(wanted, kMaleBeardMaterials[g_look.beardStyle - 1], MaleBeardTextureName(g_look.beardColor));
		}

		WantTexture(wanted, "fantasy_tatto_arm_l", g_look.tattooArmLeft > 0 ? MaleTattooTextureName("arm", g_look.tattooArmLeft) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_arm_r", g_look.tattooArmRight > 0 ? MaleTattooTextureName("arm", g_look.tattooArmRight) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_leg_l_3", g_look.tattooLegLeft > 0 ? MaleTattooTextureName("leg", g_look.tattooLegLeft) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_leg_r_3", g_look.tattooLegRight > 0 ? MaleTattooTextureName("leg", g_look.tattooLegRight) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_torso_back", g_look.tattooBack > 0 ? MaleTattooTextureName("back", g_look.tattooBack) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_torso_front", g_look.tattooFront > 0 ? MaleTattooTextureName("front", g_look.tattooFront) : kClearTexture);
	}

	void ApplyFemaleFaceAccessoriesAndTattoos(std::unordered_map<std::string, std::string>& wanted)
	{
		for (const char* material : kMaleEyebrowMaterials) {
			WantTexture(wanted, material, kClearTexture);
		}
		if (g_look.eyebrowStyle > 0) {
			WantTexture(wanted, kMaleEyebrowMaterials[g_look.eyebrowStyle - 1], MaleEyebrowTextureName(g_look.eyebrowColor));
		}

		for (const char* material : kFemaleMakeupMaterials) {
			WantTexture(wanted, material, kClearTexture);
		}
		if (g_look.makeupStyle > 0) {
			char name[64]{};
			std::snprintf(name, sizeof(name), "brl_f9_makeup_%02d", g_look.makeupColor);
			WantTexture(wanted, kFemaleMakeupMaterials[g_look.makeupStyle - 1], name);
		}

		WantTexture(wanted, "fantasy_tatto_arm_l", g_look.tattooArmLeft > 0 ? MaleTattooTextureName("arm", g_look.tattooArmLeft) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_arm_r", g_look.tattooArmRight > 0 ? MaleTattooTextureName("arm", g_look.tattooArmRight) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_leg_l_3", g_look.tattooLegLeft > 0 ? MaleTattooTextureName("leg", g_look.tattooLegLeft) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_leg_r_3", g_look.tattooLegRight > 0 ? MaleTattooTextureName("leg", g_look.tattooLegRight) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_torso_back", g_look.tattooBack > 0 ? std::string("brl_f9_tattoo_back_") + (g_look.tattooBack < 10 ? "0" : "") + std::to_string(g_look.tattooBack) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_torso_front2", g_look.tattooFront > 0 ? std::string("brl_f9_tattoo_front_") + (g_look.tattooFront < 10 ? "0" : "") + std::to_string(g_look.tattooFront) : kClearTexture);
	}

	void ApplyPoliceFemaleFaceAccessories(std::unordered_map<std::string, std::string>& wanted)
	{
		for (const char* material : kMaleEyebrowMaterials) {
			WantTexture(wanted, material, kClearTexture);
		}
		if (g_look.eyebrowStyle > 0) {
			WantTexture(wanted, kMaleEyebrowMaterials[g_look.eyebrowStyle - 1], MaleEyebrowTextureName(g_look.eyebrowColor));
		}

		for (const char* material : kFemaleMakeupMaterials) {
			WantTexture(wanted, material, kClearTexture);
		}
		if (g_look.makeupStyle > 0) {
			char name[64]{};
			std::snprintf(name, sizeof(name), "brl_f9_makeup_%02d", g_look.makeupColor);
			WantTexture(wanted, kFemaleMakeupMaterials[g_look.makeupStyle - 1], name);
		}

		WantTexture(wanted, "fantasy_tatto_arm_l", g_look.tattooArmLeft > 0 ? MaleTattooTextureName("arm", g_look.tattooArmLeft) : kClearTexture);
		WantTexture(wanted, "fantasy_tatto_arm_r", g_look.tattooArmRight > 0 ? MaleTattooTextureName("arm", g_look.tattooArmRight) : kClearTexture);
	}

	// Textures actuellement posees sur le ped local (videe par Reset() quand le modele change).
	std::unordered_map<std::string, std::string> g_appliedTextures;

	void CommitWantedTextures(const std::unordered_map<std::string, std::string>& wanted)
	{

		for (auto it = g_appliedTextures.begin(); it != g_appliedTextures.end();) {
			if (!wanted.contains(it->first)) {
				BrlTextureShader::ClearLocalPlayerTexture(it->first);
				it = g_appliedTextures.erase(it);
			}
			else {
				++it;
			}
		}

		for (const auto& [material, texture] : wanted) {
			if (texture == kClearTexture && !BrlTextureShader::HasTexture(texture)) {
				BrlTextureShader::ClearLocalPlayerTexture(material);
				g_appliedTextures.erase(material);
				continue;
			}
			auto it = g_appliedTextures.find(material);
			if (it == g_appliedTextures.end() || it->second != texture) {
				BrlTextureShader::SetLocalPlayerTexture(material, texture);
				g_appliedTextures[material] = texture;
			}
		}
	}

	void ApplyFemaleCurrent()
	{
		ClampFemaleSelection();

		std::unordered_map<std::string, std::string> wanted;
		const std::string torso = FemaleMobileTextureName("torso", g_look.skinTone);
		const std::string leg = FemaleMobileTextureName("leg", g_look.skinTone);
		const std::string feet = FemaleMobileTextureName("feet", g_look.skinTone);
		const std::string head = FemaleMobileTextureName("head", g_look.skinTone, g_look.face);

		WantList(wanted, kFemaleTorsoMaterials, torso);
		WantList(wanted, kFemaleLegMaterials, leg);
		WantList(wanted, kFemaleFeetMaterials, feet);
		WantTexture(wanted, kFemaleStableFaceMaterial, head);

		WantFemaleClothing(wanted, kFemaleHairStyles[g_look.hairStyle - 1], g_look.hairColor);
		WantFemaleClothing(wanted, kFemaleTopStyles[g_look.topStyle - 1], g_look.topColor);
		WantFemaleClothing(wanted, kFemaleBottomStyles[g_look.bottomStyle - 1], g_look.bottomColor);
		WantFemaleClothing(wanted, kFemaleShoesStyles[g_look.shoesStyle - 1], g_look.shoesColor);
		ApplyFemaleFaceAccessoriesAndTattoos(wanted);

		CommitWantedTextures(wanted);
	}

	void ApplyPoliceMaleCurrent()
	{
		ClampPoliceMaleSelection();

		std::unordered_map<std::string, std::string> wanted;
		const std::string torso = MobileTextureName("torso", g_look.skinTone);
		const std::string leg = MobileTextureName("leg", g_look.skinTone);
		const std::string feet = MobileTextureName("feet", g_look.skinTone);
		const std::string head = MobileTextureName("head", g_look.skinTone, g_look.face);

		WantList(wanted, kPoliceMaleTorsoMaterials, torso);
		WantList(wanted, kPoliceMaleLegMaterials, leg);
		WantList(wanted, kPoliceMaleFeetMaterials, feet);
		WantTexture(wanted, kPoliceMaleFaceMaterials[g_look.face - 1], head);

		WantPoliceMaleClothing(wanted, kPoliceMaleTopStyles[g_look.topStyle - 1], g_look.topColor);
		WantPoliceMaleClothing(wanted, kPoliceMaleBottomStyles[g_look.bottomStyle - 1], g_look.bottomColor);
		WantPoliceMaleClothing(wanted, kPoliceMaleShoesStyles[g_look.shoesStyle - 1], g_look.shoesColor);
		if (g_look.policeBelt == 1) {
			WantPoliceMaleClothing(wanted, {"fantasy_m_cop_belt1", "fantasy_m_cop_belt1", 1}, 1);
		}
		if (g_look.policeVest == 1) {
			WantPoliceMaleClothing(wanted, {"fantasy_m_cop_vest3", "fantasy_m_cop_vest3", 1}, 1);
		}
		if (g_look.policeHat == 1) {
			WantTexture(wanted, "fantasy_m_cop_hat3", "BRLboina1");
		}
		else {
			WantClothing(wanted, kPoliceMaleHairStyles[g_look.hairStyle - 1], g_look.hairColor);
		}
		ApplyMaleFaceAccessoriesAndTattoos(wanted);

		CommitWantedTextures(wanted);
	}

	void ApplyPoliceFemaleCurrent()
	{
		ClampPoliceFemaleSelection();

		std::unordered_map<std::string, std::string> wanted;
		const std::string torso = FemaleMobileTextureName("torso", g_look.skinTone);
		const std::string leg = FemaleMobileTextureName("leg", g_look.skinTone);
		const std::string feet = FemaleMobileTextureName("feet", g_look.skinTone);
		const std::string head = FemaleMobileTextureName("head", g_look.skinTone, g_look.face);

		if (g_look.bottomStyle == 1) {
			WantClearList(wanted, std::array<const char*, 8>{
					"body_legleft1", "body_legright1", "body_legright2", "body_legleft2",
					"body_legright3", "body_legleft3", "body_legleft4", "body_legright4"
			});
		}
		else if (g_look.bottomStyle == 2) {
			WantClearList(wanted, std::array<const char*, 4>{
					"body_legleft1", "body_legright1", "body_legright2", "body_legleft2"
			});
		}

		if (g_look.shoesStyle >= 1 && g_look.shoesStyle <= static_cast<int>(kPoliceFemaleShoesStyles.size())) {
			WantClearList(wanted, std::array<const char*, 3>{
					"body_legright5", "body_legleft5", "body_feets"
			});
		}

		if (g_look.topStyle == 1) {
			WantClearList(wanted, std::array<const char*, 5>{
					"body_torso1", "body_torso3", "body_shoulderleft",
					"body_shoulderright", "body_torso4"
			});
		}
		else if (g_look.topStyle == 2) {
			WantClearList(wanted, std::array<const char*, 8>{
					"body_torso1", "body_shoulderleft", "body_shoulderright",
					"body_torso3", "body_torso4", "body_armright",
					"body_armleft", "fantasy_tatto_arm_l"
			});
			WantClearList(wanted, std::array<const char*, 1>{"fantasy_tatto_arm_r"});
		}

		WantList(wanted, kFemaleTorsoMaterials, torso);
		WantList(wanted, kFemaleLegMaterials, leg);
		WantList(wanted, kFemaleFeetMaterials, feet);
		WantTexture(wanted, kFemaleStableFaceMaterial, head);

		WantFemaleClothing(wanted, kPoliceFemaleHairStyles[g_look.hairStyle - 1], g_look.hairColor);
		WantPoliceFemaleClothing(wanted, kPoliceFemaleTopStyles[g_look.topStyle - 1], g_look.topColor);
		WantPoliceFemaleClothing(wanted, kPoliceFemaleBottomStyles[g_look.bottomStyle - 1], g_look.bottomColor);
		WantPoliceFemaleClothing(wanted, kPoliceFemaleShoesStyles[g_look.shoesStyle - 1], g_look.shoesColor);
		if (g_look.policeBelt == 1) {
			WantTexture(wanted, "fantasy_m_cop_belt1", PoliceFemaleTextureName("fantasy_m_cop_belt1", 1));
		}
		if (g_look.policeVest == 1) {
			WantPoliceFemaleClothing(wanted, {{"fantasy_f_cop_vest3", nullptr}, {"fantasy_f_cop_vest3", nullptr}, 1, 1}, 1);
		}
		if (g_look.policeHat == 1) {
			WantTexture(wanted, "fantasy_f_cop_hat3", "BRLboina1");
		}
		else {
			WantFemaleClothing(wanted, kPoliceFemaleHairStyles[g_look.hairStyle - 1], g_look.hairColor);
		}
		ApplyPoliceFemaleFaceAccessories(wanted);

		CommitWantedTextures(wanted);
	}

	bool ArrowButton(const char* id, const char* label)
	{
		ImGui::PushID(id);
		bool clicked = ImGui::Button(label, ImVec2(58.0f, 44.0f));
		ImGui::PopID();
		return clicked;
	}

	bool DrawSelectorInternal(const char* label, uint8_t& value, int minValue, int maxValue, bool applyOnChange)
	{
		bool changed = false;
		ImGui::Text("%s", label);
		// Alinha os botões < valor > logo após o texto do rótulo, com uma
		// margem proporcional à fonte (a fonte do launcher escala com a
		// resolução; um deslocamento fixo em px quebrava o layout em HD).
		ImGui::SameLine(ImGui::CalcTextSize(label).x + ImGui::GetFontSize() * 2.2f);
		if (ArrowButton(label, "<")) {
			value = value <= minValue ? maxValue : value - 1;
			changed = true;
		}
		ImGui::SameLine();
		ImGui::Text("%02d", value);
		ImGui::SameLine();
		if (ArrowButton(label, ">")) {
			value = value >= maxValue ? minValue : value + 1;
			changed = true;
		}
		if (changed && applyOnChange) {
			ClothesCustomization::ApplyCurrent();
		}
		return changed;
	}

	void DrawSelector(const char* label, uint8_t& value, int minValue, int maxValue)
	{
		DrawSelectorInternal(label, value, minValue, maxValue, true);
	}

	void DrawSelectorResettingColor(const char* label, uint8_t& style, uint8_t& color, int minValue, int maxValue)
	{
		if (DrawSelectorInternal(label, style, minValue, maxValue, false)) {
			color = 1;
			ClothesCustomization::ApplyCurrent();
		}
	}
}

ClothesLook& ClothesCustomization::Look()
{
	return g_look;
}

void ClothesCustomization::Reset(int gender)
{
	g_appliedTextures.clear();
	g_look = ClothesLook{};
	// Padrões do gênero
	g_look.skinTone = 1;
	g_look.face = 1;
	g_look.hairStyle = 1;
	g_look.hairColor = 1;
	g_look.topStyle = 1;
	g_look.topColor = 1;
	g_look.bottomStyle = 1;
	g_look.bottomColor = 1;
	g_look.shoesStyle = 1;
	g_look.shoesColor = 1;
	g_look.eyebrowStyle = 0;
	g_look.eyebrowColor = 1;
	if (gender == 1) {
		g_look.beardStyle = 0;
		g_look.makeupStyle = 0;
		g_look.makeupColor = 1;
	}
	else {
		// Personagem novo masculino começa só de cueca: sem camisa (top 0),
		// cueca (estilo 3 = fantasy_m_underwear) e descalço (shoes 0).
		g_look.topStyle = 0;
		g_look.bottomStyle = 3;
		g_look.shoesStyle = 0;
	}
	// Limpa as regras antigas do jogador local antes de aplicar o novo visual
	BrlTextureShader::ClearPlayer(0xFFFF);
}

void ClothesCustomization::ClearLocal()
{
	BrlTextureShader::ClearPlayer(0xFFFF);
}

bool ClothesCustomization::IsSupportedSkin(uint16_t modelId)
{
	return modelId == 9 || modelId == 163 || modelId == 246 || modelId == 280;
}

bool ClothesCustomization::IsSupportedLocalSkin()
{
	return IsSupportedSkin(CurrentLocalModelId());
}

void ClothesCustomization::ApplyCurrent()
{
	const uint16_t modelId = CurrentLocalModelId();
	if (!IsSupportedSkin(modelId) || !CurrentLocalPedReady()) {
		return;
	}

	if (modelId == 9) {
		ApplyFemaleCurrent();
		return;
	}

	if (modelId == 246) {
		ApplyPoliceFemaleCurrent();
		return;
	}

	if (modelId == 280) {
		ApplyPoliceMaleCurrent();
		return;
	}

	if (modelId != 163) return;

	ClampSelection();
	const std::string torso = MobileTextureName("torso", g_look.skinTone);
	const std::string leg = MobileTextureName("leg", g_look.skinTone);
	const std::string feet = MobileTextureName("feet", g_look.skinTone);
	const std::string head = MobileTextureName("head", g_look.skinTone, g_look.face);

	std::unordered_map<std::string, std::string> wanted;

	WantList(wanted, kTorsoMaterials, torso);
	WantList(wanted, kLegMaterials, leg);
	WantList(wanted, kFeetMaterials, feet);
	WantTexture(wanted, kFaceMaterialSlots[g_look.face - 1][0], head);

	WantClothing(wanted, kHairStyles[g_look.hairStyle - 1], g_look.hairColor);
	if (g_look.topStyle > 0) {
		WantClothing(wanted, kTopStyles[g_look.topStyle - 1], g_look.topColor);
	}
	if (g_look.bottomStyle > 0) {
		WantClothing(wanted, kBottomStyles[g_look.bottomStyle - 1], g_look.bottomColor);
	}
	if (g_look.shoesStyle > 0) {
		WantClothing(wanted, kShoesStyles[g_look.shoesStyle - 1], g_look.shoesColor);
	}
	ApplyMaleFaceAccessoriesAndTattoos(wanted);

	CommitWantedTextures(wanted);
}

void ClothesCustomization::RenderPanel()
{
	const uint16_t modelId = CurrentLocalModelId();
	const bool femaleSkin = modelId == 9;
	const bool policeFemaleSkin = modelId == 246;
	const bool policeMaleSkin = modelId == 280;
	const bool supportedSkin = IsSupportedSkin(modelId);

	ImGui::BeginChild("clothes_scroll", ImVec2(0.0f, 0.0f), true, ImGuiWindowFlags_AlwaysVerticalScrollbar);
	if (!supportedSkin) {
		ImGui::TextWrapped("Utilisez le skin 9, 163, 246 ou 280 pour tester les shaders de peau/vêtements.");
	}
	else if (femaleSkin) {
		DrawSelector("Couleur de peau", g_look.skinTone, 1, 6);
		ClampFemaleSelection();
		DrawSelector("Texture du visage", g_look.face, 1, kFemaleFaceCountByTone[g_look.skinTone]);
		ImGui::Separator();
		DrawSelectorResettingColor("Style de maquillage", g_look.makeupStyle, g_look.makeupColor, 0, kFemaleMakeupStyleCount);
		if (g_look.makeupStyle > 0) {
			DrawSelector("Couleur du maquillage", g_look.makeupColor, 1, kFemaleMakeupColorCount);
		}
		ImGui::Separator();
		DrawSelectorResettingColor("Style de sourcils", g_look.eyebrowStyle, g_look.eyebrowColor, 0, kMaleEyebrowStyleCount);
		if (g_look.eyebrowStyle > 0) {
			DrawSelector("Couleur des sourcils", g_look.eyebrowColor, 1, kMaleEyebrowColorCount);
		}
		ImGui::Separator();
		DrawSelectorResettingColor("Style de cheveux", g_look.hairStyle, g_look.hairColor, 1, static_cast<int>(kFemaleHairStyles.size()));
		DrawSelector("Couleur des cheveux", g_look.hairColor, 1, kFemaleHairStyles[g_look.hairStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelectorResettingColor("Hauts", g_look.topStyle, g_look.topColor, 1, static_cast<int>(kFemaleTopStyles.size()));
		DrawSelector("Couleur du haut", g_look.topColor, 1, kFemaleTopStyles[g_look.topStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelectorResettingColor("Bas", g_look.bottomStyle, g_look.bottomColor, 1, static_cast<int>(kFemaleBottomStyles.size()));
		DrawSelector("Couleur du bas", g_look.bottomColor, 1, kFemaleBottomStyles[g_look.bottomStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelectorResettingColor("Chaussures", g_look.shoesStyle, g_look.shoesColor, 1, static_cast<int>(kFemaleShoesStyles.size()));
		DrawSelector("Couleur des chaussures", g_look.shoesColor, 1, kFemaleShoesStyles[g_look.shoesStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelector("Tatouage bras gauche", g_look.tattooArmLeft, 0, kMaleTattooArmCount);
		DrawSelector("Tatouage bras droit", g_look.tattooArmRight, 0, kMaleTattooArmCount);
		DrawSelector("Tatouage jambe gauche", g_look.tattooLegLeft, 0, kMaleTattooLegCount);
		DrawSelector("Tatouage jambe droite", g_look.tattooLegRight, 0, kMaleTattooLegCount);
		DrawSelector("Tatouage dos", g_look.tattooBack, 0, kMaleTattooBackCount);
		DrawSelector("Tatouage ventre", g_look.tattooFront, 0, kMaleTattooFrontCount);
	}
	else if (policeFemaleSkin) {
		DrawSelector("Couleur de peau", g_look.skinTone, 1, 6);
		ClampPoliceFemaleSelection();
		DrawSelector("Texture du visage", g_look.face, 1, kFemaleFaceCountByTone[g_look.skinTone]);
		ImGui::Separator();
		DrawSelectorResettingColor("Style de maquillage", g_look.makeupStyle, g_look.makeupColor, 0, kFemaleMakeupStyleCount);
		if (g_look.makeupStyle > 0) {
			DrawSelector("Couleur du maquillage", g_look.makeupColor, 1, kFemaleMakeupColorCount);
		}
		ImGui::Separator();
		DrawSelectorResettingColor("Style de sourcils", g_look.eyebrowStyle, g_look.eyebrowColor, 0, kMaleEyebrowStyleCount);
		if (g_look.eyebrowStyle > 0) {
			DrawSelector("Couleur des sourcils", g_look.eyebrowColor, 1, kMaleEyebrowColorCount);
		}
		ImGui::Separator();
		DrawSelectorResettingColor("Style de cheveux", g_look.hairStyle, g_look.hairColor, 1, static_cast<int>(kPoliceFemaleHairStyles.size()));
		DrawSelector("Couleur des cheveux", g_look.hairColor, 1, kPoliceFemaleHairStyles[g_look.hairStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelectorResettingColor("Hauts", g_look.topStyle, g_look.topColor, 1, static_cast<int>(kPoliceFemaleTopStyles.size()));
		DrawSelector("Couleur du haut", g_look.topColor, 1, kPoliceFemaleTopStyles[g_look.topStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelectorResettingColor("Bas", g_look.bottomStyle, g_look.bottomColor, 1, static_cast<int>(kPoliceFemaleBottomStyles.size()));
		DrawSelector("Couleur du bas", g_look.bottomColor, 1, kPoliceFemaleBottomStyles[g_look.bottomStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelectorResettingColor("Chaussures", g_look.shoesStyle, g_look.shoesColor, 1, static_cast<int>(kPoliceFemaleShoesStyles.size()));
		DrawSelector("Couleur des chaussures", g_look.shoesColor, 1, kPoliceFemaleShoesStyles[g_look.shoesStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelector("Arme à la ceinture", g_look.policeBelt, 0, 1);
		DrawSelector("Gilet", g_look.policeVest, 0, 1);
		DrawSelector("Béret", g_look.policeHat, 0, 1);
		ImGui::Separator();
		DrawSelector("Tatouage bras gauche", g_look.tattooArmLeft, 0, kMaleTattooArmCount);
		DrawSelector("Tatouage bras droit", g_look.tattooArmRight, 0, kMaleTattooArmCount);
	}
	else if (policeMaleSkin) {
		DrawSelector("Couleur de peau", g_look.skinTone, 1, 6);
		ClampPoliceMaleSelection();
		DrawSelector("Forme du visage", g_look.face, 1, std::min(kFaceCountByTone[g_look.skinTone], kDffFaceSlots));
		ImGui::Separator();
		DrawSelectorResettingColor("Style de sourcils", g_look.eyebrowStyle, g_look.eyebrowColor, 0, kMaleEyebrowStyleCount);
		if (g_look.eyebrowStyle > 0) {
			DrawSelector("Couleur des sourcils", g_look.eyebrowColor, 1, kMaleEyebrowColorCount);
		}
		ImGui::Separator();
		DrawSelectorResettingColor("Style de barbe", g_look.beardStyle, g_look.beardColor, 0, kMaleBeardStyleCount);
		if (g_look.beardStyle > 0) {
			DrawSelector("Couleur de la barbe", g_look.beardColor, 1, kMaleBeardColorCount);
		}
		ImGui::Separator();
		DrawSelectorResettingColor("Style de cheveux", g_look.hairStyle, g_look.hairColor, 1, static_cast<int>(kPoliceMaleHairStyles.size()));
		DrawSelector("Couleur des cheveux", g_look.hairColor, 1, kPoliceMaleHairStyles[g_look.hairStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelectorResettingColor("Hauts", g_look.topStyle, g_look.topColor, 1, static_cast<int>(kPoliceMaleTopStyles.size()));
		DrawSelector("Couleur du haut", g_look.topColor, 1, kPoliceMaleTopStyles[g_look.topStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelectorResettingColor("Bas", g_look.bottomStyle, g_look.bottomColor, 1, static_cast<int>(kPoliceMaleBottomStyles.size()));
		DrawSelector("Couleur du bas", g_look.bottomColor, 1, kPoliceMaleBottomStyles[g_look.bottomStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelectorResettingColor("Chaussures", g_look.shoesStyle, g_look.shoesColor, 1, static_cast<int>(kPoliceMaleShoesStyles.size()));
		DrawSelector("Couleur des chaussures", g_look.shoesColor, 1, kPoliceMaleShoesStyles[g_look.shoesStyle - 1].maxValue);
		ImGui::Separator();
		DrawSelector("Arme à la ceinture", g_look.policeBelt, 0, 1);
		DrawSelector("Gilet", g_look.policeVest, 0, 1);
		DrawSelector("Béret", g_look.policeHat, 0, 1);
		ImGui::Separator();
		DrawSelector("Tatouage bras gauche", g_look.tattooArmLeft, 0, kMaleTattooArmCount);
		DrawSelector("Tatouage bras droit", g_look.tattooArmRight, 0, kMaleTattooArmCount);
		DrawSelector("Tatouage jambe gauche", g_look.tattooLegLeft, 0, kMaleTattooLegCount);
		DrawSelector("Tatouage jambe droite", g_look.tattooLegRight, 0, kMaleTattooLegCount);
		DrawSelector("Tatouage dos", g_look.tattooBack, 0, kMaleTattooBackCount);
		DrawSelector("Tatouage ventre", g_look.tattooFront, 0, kMaleTattooFrontCount);
	}
	else {
		DrawSelector("Couleur de peau", g_look.skinTone, 1, 6);
		ClampSelection();
		DrawSelector("Forme du visage", g_look.face, 1, std::min(kFaceCountByTone[g_look.skinTone], kDffFaceSlots));
		ImGui::Separator();
		DrawSelectorResettingColor("Style de sourcils", g_look.eyebrowStyle, g_look.eyebrowColor, 0, kMaleEyebrowStyleCount);
		if (g_look.eyebrowStyle > 0) {
			DrawSelector("Couleur des sourcils", g_look.eyebrowColor, 1, kMaleEyebrowColorCount);
		}
		ImGui::Separator();
		DrawSelectorResettingColor("Style de barbe", g_look.beardStyle, g_look.beardColor, 0, kMaleBeardStyleCount);
		if (g_look.beardStyle > 0) {
			DrawSelector("Couleur de la barbe", g_look.beardColor, 1, kMaleBeardColorCount);
		}
		ImGui::Separator();
		DrawSelectorResettingColor("Style de cheveux", g_look.hairStyle, g_look.hairColor, 1, static_cast<int>(kHairStyles.size()));
		DrawSelector("Couleur des cheveux", g_look.hairColor, 1, kHairStyles[g_look.hairStyle - 1].maxValue);
		ImGui::Separator();
		// 0 = sem camisa (personagem começa só de cueca)
		DrawSelectorResettingColor("Hauts", g_look.topStyle, g_look.topColor, 0, static_cast<int>(kTopStyles.size()));
		if (g_look.topStyle > 0) {
			DrawSelector("Couleur du haut", g_look.topColor, 1, kTopStyles[g_look.topStyle - 1].maxValue);
		}
		ImGui::Separator();
		DrawSelectorResettingColor("Bas", g_look.bottomStyle, g_look.bottomColor, 1, static_cast<int>(kBottomStyles.size()));
		DrawSelector("Couleur du bas", g_look.bottomColor, 1, kBottomStyles[g_look.bottomStyle - 1].maxValue);
		ImGui::Separator();
		// 0 = descalço
		DrawSelectorResettingColor("Chaussures", g_look.shoesStyle, g_look.shoesColor, 0, static_cast<int>(kShoesStyles.size()));
		if (g_look.shoesStyle > 0) {
			DrawSelector("Couleur des chaussures", g_look.shoesColor, 1, kShoesStyles[g_look.shoesStyle - 1].maxValue);
		}
		ImGui::Separator();
		DrawSelector("Tatouage bras gauche", g_look.tattooArmLeft, 0, kMaleTattooArmCount);
		DrawSelector("Tatouage bras droit", g_look.tattooArmRight, 0, kMaleTattooArmCount);
		DrawSelector("Tatouage jambe gauche", g_look.tattooLegLeft, 0, kMaleTattooLegCount);
		DrawSelector("Tatouage jambe droite", g_look.tattooLegRight, 0, kMaleTattooLegCount);
		DrawSelector("Tatouage dos", g_look.tattooBack, 0, kMaleTattooBackCount);
		DrawSelector("Tatouage ventre", g_look.tattooFront, 0, kMaleTattooFrontCount);
	}
	ImGui::EndChild();
}