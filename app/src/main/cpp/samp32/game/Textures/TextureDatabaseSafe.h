#pragma once

// =============================================================================================
// Chargement robuste des bases de textures (texdb) - corrige le crash au demarrage :
//
//   SIGSEGV | Fault address: 0x8d8
//   TextureDatabaseRuntime::SortEntries(bool)   <- TextureDatabaseRuntime::Load("samp")
//   <- CGame__InitialiseRenderWare_hook          (juste apres "Loading SAMP texture database..")
//
// TextureDatabaseRuntime::Load() du moteur fait :
//   LoadEntries()  -> texdb/<db>/<db>.txt
//   LoadThumbs(f)  -> texdb/<db>/<db>.<f>.tmb      (resultat NON verifie par le moteur)
//   LoadDataOffsets(f) -> texdb/<db>/<db>.<f>.dat (+ .toc)  (resultat NON verifie non plus)
//   SortEntries(false) -> lit fullDataOffsets.dataPtr[i] SANS tester s'il est nul.
// Si le .dat (ou le .tmb) du format choisi pour ce GPU (dxt / etc / pvr) est absent ou ne
// correspond pas au .txt, fullDataOffsets reste NULL => lecture a l'adresse 0 + index*4 => crash.
//
// Correctif :
//  1) LoadThumbs : si le format demande n'a pas son .dat ou si son .tmb est invalide, on essaie
//     les autres formats presents (etc, dxt, pvr, unc) et on bascule loadedFormat dessus ;
//  1b) LoadDataOffsets : si le .toc / .dat du format choisi ne donne aucun offset (fichier .toc absent,
//     tronque, ou .dat abime - vu sur Android 11, GPU PowerVR -> format pvr), on recharge la base dans
//     un autre format complet (etc, dxt, pvr, unc) : vignettes + offsets + fichier de streaming.
//     Sans ca : SortEntries plantait (0x8d8), puis MobileMenu::Load (textures "menu" absentes, 0x64).
//  2) SortEntries : si malgre tout les offsets n'ont pas ete charges, la base est desactivee
//     (0 entree) au lieu de planter. Les textures manquantes sont deja gerees par le client
//     ("WARNING! No tex = ...").
// Header partage samp/ (arm64) et samp32/ (armeabi-v7a) : TDBSAFE_LOG = fonction de log du client.
// =============================================================================================

#include <cstdio>
#include <cstring>
#include "TextureDatabase.h"
#include "TextureDatabaseRuntime.h"
#include "../../vendor/armhook/patch.h"

#ifndef TDBSAFE_LOG
#define TDBSAFE_LOG Log
#endif

namespace TextureDatabaseSafe
{
    inline const char* FormatExt(int f)
    {
        static const char* ext[] = { "unc", "dxt", "360", "ps3", "pvr", "etc" };
        return (f >= 0 && f < 6) ? ext[f] : "?";
    }

    // Meme fonction d'ouverture que le moteur (OS_FileOpen, zone de donnees 0, lecture).
    inline bool GameFileExists(const char* path)
    {
        void* h = nullptr;
        int err = CHook::CallFunction<int>("_Z11OS_FileOpen14OSFileDataAreaPPvPKc16OSFileAccessType", 0, &h, path, 0);
        if (err != 0 || !h) return false;
        CHook::CallFunction<int>("_Z12OS_FileClosePv", h);
        return true;
    }

    inline bool HasPart(const char* db, int f, const char* part)
    {
        char path[256];
        snprintf(path, sizeof(path), "texdb/%s/%s.%s.%s", db, db, FormatExt(f), part);
        return GameFileExists(path);
    }

    inline bool (*LoadThumbs_orig)(TextureDatabase*, TextureDatabaseFormat, bool) = nullptr;
    inline bool (*LoadDataOffsets_orig)(TextureDatabase*, TextureDatabaseFormat, TDBArray<uint32>*, void**, bool) = nullptr;
    inline void (*SortEntries_orig)(TextureDatabaseRuntime*, bool) = nullptr;

    inline bool TryFormat(TextureDatabase* thiz, int f, bool setEntries)
    {
        const char* db = thiz->name ? thiz->name : "";
        if (!HasPart(db, f, "dat") || !HasPart(db, f, "tmb")) return false;
        return LoadThumbs_orig(thiz, (TextureDatabaseFormat)f, setEntries);
    }

    inline bool LoadThumbs_hook(TextureDatabase* thiz, TextureDatabaseFormat fmt, bool setEntries)
    {
        if (!thiz || !thiz->name || (int)fmt < 0 || (int)fmt > 5)
            return LoadThumbs_orig(thiz, fmt, setEntries);

        const char* db = thiz->name;
        const bool hasDat = HasPart(db, fmt, "dat");
        if (hasDat && LoadThumbs_orig(thiz, fmt, setEntries)) return true;

        TDBSAFE_LOG("[TexDB] '%s' : format %s inutilisable (%s) - essai des autres formats",
            db, FormatExt(fmt), hasDat ? ".tmb absent ou ne correspondant pas au .txt" : ".dat absent");

        // ETC1 est lu par quasiment tous les GPU Android : on le tente en premier.
        static const int fallbacks[] = { DF_ETC, DF_DXT, DF_PVR, DF_UNC };
        for (int f : fallbacks) {
            if (f == (int)fmt) continue;
            if (TryFormat(thiz, f, setEntries)) {
                if (thiz->loadedFormat == fmt) thiz->loadedFormat = (TextureDatabaseFormat)f;
                TDBSAFE_LOG("[TexDB] '%s' : charge au format %s a la place de %s", db, FormatExt(f), FormatExt(fmt));
                return true;
            }
        }

        TDBSAFE_LOG("[TexDB] '%s' : aucun autre format complet (.tmb + .dat) dans texdb/%s/", db, db);
        // Dernier recours : comportement d'origine (SortEntries_hook evite le crash si ca echoue).
        return hasDat ? false : LoadThumbs_orig(thiz, fmt, setEntries);
    }

    inline bool OffsetsOk(const TDBArray<uint32>* offs)
    {
        return offs && offs->dataPtr != nullptr && offs->numEntries > 0;
    }

    inline bool LoadDataOffsets_hook(TextureDatabase* thiz, TextureDatabaseFormat fmt, TDBArray<uint32>* offs, void** file, bool write)
    {
        bool r = LoadDataOffsets_orig(thiz, fmt, offs, file, write);
        if (write || !thiz || !thiz->name || !offs || OffsetsOk(offs) || (int)fmt < 0 || (int)fmt > 5 || !LoadThumbs_orig)
            return r;

        const char* db = thiz->name;
        TDBSAFE_LOG("[TexDB] '%s' : offsets du format %s non charges (dat=%d tmb=%d toc=%d) - essai des autres formats",
            db, FormatExt(fmt), (int)HasPart(db, fmt, "dat"), (int)HasPart(db, fmt, "tmb"), (int)HasPart(db, fmt, "toc"));

        static const int fallbacks[] = { DF_ETC, DF_DXT, DF_PVR, DF_UNC };
        for (int f : fallbacks) {
            if (f == (int)fmt) continue;
            if (!HasPart(db, f, "dat") || !HasPart(db, f, "tmb")) continue;
            // fichier de streaming ouvert pour le format rate : on le ferme avant de changer de format
            if (file && *file) {
                CHook::CallFunction<int>("_Z12OS_FileClosePv", *file);
                *file = nullptr;
            }
            offs->numEntries = 0;
            if (!LoadThumbs_orig(thiz, (TextureDatabaseFormat)f, true)) continue;
            LoadDataOffsets_orig(thiz, (TextureDatabaseFormat)f, offs, file, write);
            if (OffsetsOk(offs)) {
                thiz->loadedFormat = (TextureDatabaseFormat)f;
                TDBSAFE_LOG("[TexDB] '%s' : base rechargee au format %s a la place de %s", db, FormatExt(f), FormatExt(fmt));
                return true;
            }
        }
        TDBSAFE_LOG("[TexDB] '%s' : aucun format complet (.dat + .tmb + .toc) dans texdb/%s/ - REINSTALLE LES DONNEES", db, db);
        return r;
    }

    inline void SortEntries_hook(TextureDatabaseRuntime* thiz, bool flag)
    {
        if (thiz && !flag && thiz->entries.numEntries > 0 && thiz->fullDataOffsets.dataPtr == nullptr) {
            TDBSAFE_LOG("[TexDB] '%s' : offsets de texture non charges (%u entrees) - base desactivee pour eviter le crash",
                thiz->name ? thiz->name : "?", thiz->entries.numEntries);
            thiz->entries.numEntries = 0;
        }
        SortEntries_orig(thiz, flag);
    }

    // withFormatFallback=false : seulement la protection anti-crash de SortEntries (client 32 bits, dont
    // les noms de fichiers texdb sont rediriges vers dxt par InstallUrezHooks).
    inline void Install(bool withFormatFallback = true)
    {
        static bool s_done = false;
        if (s_done) return;
        s_done = true;
        if (withFormatFallback) {
            CHook::InlineHook("_ZN15TextureDatabase10LoadThumbsE21TextureDatabaseFormatb", &LoadThumbs_hook, &LoadThumbs_orig);
            CHook::InlineHook("_ZN15TextureDatabase15LoadDataOffsetsE21TextureDatabaseFormatR8TDBArrayIjERPvb", &LoadDataOffsets_hook, &LoadDataOffsets_orig);
        }
        CHook::InlineHook("_ZN22TextureDatabaseRuntime11SortEntriesEb", &SortEntries_hook, &SortEntries_orig);
    }
}
