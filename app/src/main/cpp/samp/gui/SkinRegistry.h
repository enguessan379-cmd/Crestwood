#pragma once

#include <cstdint>
#include <cstddef>

// ============================================================
// SkinRegistry - noms/infos des skins (doit rester synchro avec
// data/skins.dat et server/include/skins.inc).
// ============================================================

namespace SkinRegistry
{
    enum class Gender : uint8_t { Male, Female };
    enum class Category : uint8_t { Base, Civil, Police, Custom };

    struct Entry
    {
        uint16_t    id;
        const char* model;   // nom du modele dans peds.ide (= nom dff/txd)
        const char* name;    // nom affiche en jeu
        Gender      gender;
        Category    category;
        bool        clothes; // true = supporte le systeme de clothes (ClothesCustomization)
    };

    static const Entry kEntries[] = {
        { 9, "bfori", "Femme (base création)", Gender::Female, Category::Base, true },
        { 10, "bfost", "Femme âgée", Gender::Female, Category::Civil, false },
        { 31, "dwfolc", "Habitante du désert", Gender::Female, Category::Civil, false },
        { 38, "hfori", "Femme latina chic", Gender::Female, Category::Civil, false },
        { 39, "hfost", "Femme latina mature", Gender::Female, Category::Civil, false },
        { 53, "ofori", "Femme orientale chic", Gender::Female, Category::Civil, false },
        { 54, "ofost", "Femme orientale mature", Gender::Female, Category::Civil, false },
        { 56, "ofyst", "Jeune femme orientale", Gender::Female, Category::Civil, false },
        { 69, "sbfyst", "Jeune femme décontractée", Gender::Female, Category::Civil, false },
        { 76, "wfystew", "Hôtesse de l'air", Gender::Female, Category::Civil, false },
        { 88, "wfori", "Femme blanche chic", Gender::Female, Category::Civil, false },
        { 89, "wfost", "Femme blanche mature", Gender::Female, Category::Civil, false },
        { 91, "wfyri", "Femme riche", Gender::Female, Category::Civil, false },
        { 92, "wfyro", "Patineuse", Gender::Female, Category::Civil, false },
        { 129, "dnfolc1", "Fermière 1", Gender::Female, Category::Civil, false },
        { 130, "dnfolc2", "Fermière 2", Gender::Female, Category::Civil, false },
        { 131, "dnfylc", "Jeune fermière", Gender::Female, Category::Civil, false },
        { 138, "wfybe", "Baigneuse blanche", Gender::Female, Category::Civil, false },
        { 139, "bfybe", "Baigneuse noire", Gender::Female, Category::Civil, false },
        { 140, "hfybe", "Baigneuse latina", Gender::Female, Category::Civil, false },
        { 141, "sofybu", "Femme d'affaires 1", Gender::Female, Category::Civil, false },
        { 148, "bfybu", "Femme d'affaires 2", Gender::Female, Category::Civil, false },
        { 150, "wfybu", "Femme d'affaires 3", Gender::Female, Category::Civil, false },
        { 151, "dwfylc1", "Jeune citadine 1", Gender::Female, Category::Civil, false },
        { 157, "cwfyhb", "Campagnarde", Gender::Female, Category::Civil, false },
        { 163, "bmyboun", "Homme (base création)", Gender::Male, Category::Base, true },
        { 169, "sofyri", "Femme élégante", Gender::Female, Category::Civil, false },
        { 190, "copgrl3", "Barbara", Gender::Female, Category::Civil, false },
        { 191, "gungrl3", "Helena", Gender::Female, Category::Civil, false },
        { 194, "crogrl3", "Millie", Gender::Female, Category::Civil, false },
        { 195, "gangrl3", "Denise", Gender::Female, Category::Civil, false },
        { 196, "cwfofr", "Fermière âgée", Gender::Female, Category::Civil, false },
        { 197, "cwfohb", "Campagnarde âgée", Gender::Female, Category::Civil, false },
        { 198, "cwfyfr1", "Jeune fermière 1", Gender::Female, Category::Civil, false },
        { 199, "cwfyfr2", "Jeune fermière 2", Gender::Female, Category::Civil, false },
        { 201, "dwfylc2", "Jeune citadine 2", Gender::Female, Category::Civil, false },
        { 215, "sbfori", "Femme noire chic", Gender::Female, Category::Civil, false },
        { 218, "sbfost", "Femme noire mature", Gender::Female, Category::Civil, false },
        { 219, "sbfyri", "Femme noire riche", Gender::Female, Category::Civil, false },
        { 224, "sofori", "Femme chic 2", Gender::Female, Category::Civil, false },
        { 225, "sofost", "Femme mature 2", Gender::Female, Category::Civil, false },
        { 226, "sofyst", "Jeune femme 2", Gender::Female, Category::Civil, false },
        { 231, "swfori", "Femme blanche chic 2", Gender::Female, Category::Civil, false },
        { 232, "swfost", "Femme blanche mature 2", Gender::Female, Category::Civil, false },
        { 245, "vbfypro", "Femme tenue de soirée", Gender::Female, Category::Civil, false },
        { 246, "vhfyst3", "Policière (base)", Gender::Female, Category::Police, true },
        { 251, "wfylg", "Maître-nageuse", Gender::Female, Category::Civil, false },
        { 263, "vwfywa2", "Femme décontractée 3", Gender::Female, Category::Civil, false },
        { 280, "lapd1", "Policier LSPD (base)", Gender::Male, Category::Police, true },
        { 281, "sfpd1", "Policier SFPD", Gender::Male, Category::Police, false },
        { 282, "lvpd1", "Policier LVPD", Gender::Male, Category::Police, false },
        { 283, "csher", "Shérif du comté", Gender::Male, Category::Police, false },
        { 284, "lapdm1", "Policière LSPD", Gender::Female, Category::Police, false },
        { 285, "swat", "Policière SWAT", Gender::Female, Category::Police, false },
        { 286, "fbi", "Agente fédérale", Gender::Female, Category::Police, false },
        { 288, "dsher", "Pilote hélico police", Gender::Male, Category::Police, false },
        { 289, "cwskin", "Homme Crestwood (clothes)", Gender::Male, Category::Custom, true },
    };

    static const size_t kCount = sizeof(kEntries) / sizeof(kEntries[0]);

    inline const Entry* Find(uint16_t id)
    {
        for (size_t i = 0; i < kCount; ++i) {
            if (kEntries[i].id == id) return &kEntries[i];
        }
        return nullptr;
    }

    // Nom affichable ("Skin 123" si le skin n'est pas dans le registre).
    inline const char* Name(uint16_t id, char* fallback, size_t fallbackSize)
    {
        const Entry* e = Find(id);
        if (e) return e->name;
        if (fallback && fallbackSize) {
            int n = 0; char tmp[8]; uint16_t v = id; do { tmp[n++] = char('0' + v % 10); v /= 10; } while (v && n < 7);
            size_t p = 0; const char* pre = "Skin "; while (*pre && p + 1 < fallbackSize) fallback[p++] = *pre++;
            while (n && p + 1 < fallbackSize) fallback[p++] = tmp[--n];
            fallback[p] = 0;
        }
        return fallback;
    }
}
