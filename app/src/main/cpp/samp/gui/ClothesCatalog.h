#pragma once

#include <cstddef>
#include <cstdint>
#include <cstdio>

// ============================================================
// ClothesCatalog - vetements du pack Clothes.zip (skin personnalisable Crestwood).
// FICHIER GENERE par tools/build_catalog.py : modifie CATALOG dans le script, relance-le.
//
// Le jeu cherche chaque texture PAR SON NOM dans la texdb :
//     brl_cw_clothes_<cle>_<NN>        ex. brl_cw_clothes_top1_02
// Le materiau cible est celui de cwskin.dff. Texture introuvable => piece masquee.
// Chaque style ET chaque variante a un nom affiche en jeu.
// ============================================================

namespace ClothesCatalog
{
    constexpr uint16_t kCustomMaleSkinId = 289;

    struct Style
    {
        const char*        name;          // nom du style
        const char*        key;           // brl_cw_clothes_<key>_<NN>
        const char*        materials[2];  // materiaux du dff recevant la texture
        int                materialCount;
        int                variants;      // nombre de textures / couleurs
        const char* const* variantNames;  // nom de chaque variante (variants entrees)
    };

    constexpr const char* kBodyMaterials[] = { "uppr_diff_015_a_whi", "lowr_diff_000_a_whi" };
    constexpr const char* kFaceMaterial = "cara3";
    constexpr const char* kFaceKey      = "face";
    constexpr int         kFaceCount    = 3;

    constexpr const char* kFaceNames[] = { "Visage 1", "Visage tatoué (cou)", "Visage barbu" };
    constexpr const char* kBeard1Names[] = { "Barbe noire" };
    constexpr const char* kHair1Names[] = { "Châtain clair", "Gris", "Rouge", "Vert" };
    constexpr const char* kHair2Names[] = { "Brun", "Noir", "Rouge", "Bleu", "Châtain" };
    constexpr const char* kHair3Names[] = { "Noire", "Noire 2" };
    constexpr const char* kHair4Names[] = { "Noir", "Châtain" };
    constexpr const char* kTop1Names[] = { "Fleurs rouges", "Noir logo rouge", "Tropical", "Noir groupe rock" };
    constexpr const char* kTop2Names[] = { "Gilet sombre", "Polo blanc", "Tie-dye", "Gris" };
    constexpr const char* kTop3Names[] = { "Bleu 1", "Rouge 2", "Gris 3", "Gris 4", "Gris 5", "Blanc 6", "Noir 7", "Gris 8", "Noir 9", "Noir 10", "Marron 11", "Rouge 12", "Noir et rouge 13", "Noir et vert 14", "Rose et rouge 15" };
    constexpr const char* kBottom1Names[] = { "Rose" };
    constexpr const char* kBottom2Names[] = { "Noir", "Jean bleu", "Cargo gris" };
    constexpr const char* kBottom3Names[] = { "Violet 1", "Turquoise 2", "Bleu et rose 3", "Rouge 4", "Rouge 5", "Gris 6", "Gris 7", "Noir et gris 8", "Bleu et gris 9", "Noir 10", "Noir 11", "Noir 12", "Noir 13", "Jaune et marron 14", "Blanc et gris 15", "Jaune 16", "Gris 17", "Noir 18" };
    constexpr const char* kBottom4Names[] = { "Gris et noir 1", "Violet 2", "Gris et noir 3" };
    constexpr const char* kBottom5Names[] = { "Noir et gris 1" };
    constexpr const char* kBottom6Names[] = { "Noir 1", "Noir 2", "Noir 3", "Turquoise 4", "Noir 5", "Bleu 6", "Rose 7", "Orange et marron 8", "Violet 9", "Vert 10", "Rouge 11" };
    constexpr const char* kShoes1Names[] = { "Bleues" };
    constexpr const char* kShoes2Names[] = { "Roses", "Vert et gris", "Rouges", "Rouge et noir", "Blanc et rouge", "Bleues", "Noir et rouge", "Gris et orange", "Noir et blanc" };
    constexpr const char* kWatch1Names[] = { "Montre noire" };

    constexpr Style kBeard[] = {
        { "Barbe", "beard1", { "accs_diff_003_a_uni", nullptr }, 1, 1, kBeard1Names },
    };
    constexpr Style kHair[] = {
        { "Cheveux courts", "hair1", { "hair_diff_002_d_uni", nullptr }, 1, 4, kHair1Names },
        { "Cheveux mi-longs", "hair2", { "hair_diff_003_a_uni", nullptr }, 1, 5, kHair2Names },
        { "Casquette", "hair3", { "LACOSTE15", nullptr }, 1, 2, kHair3Names },
        { "Cheveux en pointes", "hair4", { "hair_diff_000_e_uni", nullptr }, 1, 2, kHair4Names },
    };
    constexpr Style kTop[] = {
        { "T-shirt imprimé", "top1", { "jbib_diff_007_x_uni", nullptr }, 1, 4, kTop1Names },
        { "Haut", "top2", { "jbib_diff_004_a_uni", nullptr }, 1, 4, kTop2Names },
        { "Sweat", "top3", { "jbib_diff_004_a_uni", nullptr }, 1, 15, kTop3Names },
    };
    constexpr Style kBottom[] = {
        { "Short", "bottom1", { "perna", nullptr }, 1, 1, kBottom1Names },
        { "Pantalon", "bottom2", { "lowr_diff_004_a_whi", nullptr }, 1, 3, kBottom2Names },
        { "Short d'été", "bottom3", { "perna", nullptr }, 1, 18, kBottom3Names },
        { "Short camouflage", "bottom4", { "perna", nullptr }, 1, 3, kBottom4Names },
        { "Caleçon", "bottom5", { "perna", nullptr }, 1, 1, kBottom5Names },
        { "Jean", "bottom6", { "lowr_diff_004_a_whi", nullptr }, 1, 11, kBottom6Names },
    };
    constexpr Style kShoes[] = {
        { "Claquettes", "shoes1", { "pesandalia", "san" }, 2, 1, kShoes1Names },
        { "Baskets", "shoes2", { "NIKE14", nullptr }, 1, 9, kShoes2Names },
    };
    constexpr Style kWatch[] = {
        { "Montre", "watch1", { "p_lwrist_diff_000_a", nullptr }, 1, 1, kWatch1Names },
    };

    template <size_t N> constexpr int Count(const Style (&)[N]) { return static_cast<int>(N); }

    inline void TextureName(char* out, size_t size, const char* key, int value)
    {
        std::snprintf(out, size, "brl_cw_clothes_%s_%02d", key, value);
    }
}
