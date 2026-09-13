#include <jni.h>
#include <pthread.h>
#include <syscall.h>
#include <signal.h>
#include <cstdlib>
#include <cstdint>
#include <stdexcept>

#include "main.h"
#include "game/game.h"
#include "net/netgame.h"
#include "gui/gui.h"
#include "playertags.h"
#include "audiostream.h"
#include "java/jniutil.h"
#include <dlfcn.h>
#include "StackTrace.h"
#include "game/CrossHair.h"
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <chrono>
#include <cmath>
#include "game/playerped.h"
#include "game/Entity/CVehicleGTA.h"

// voice
#include "voice/Plugin.h"

#include "vendor/armhook/patch.h"
#include "vendor/obfuscate/str_obfuscator.hpp"

#include "settings.h"

#include "crashlytics.h"
#include "game/CFirstPersonCamera.hpp"
#include "CServerManager.h"
#ifdef HAS_CEF
#include "vendor/cef/SAMPMobileCef.h"
#endif

/*
Peerapol Unarak
*/

JavaVM* javaVM;


static char g_storageBuffer[512] = "/storage/emulated/0/data/";
char* g_pszStorage = g_storageBuffer;

UI* pUI = nullptr;
CGame *pGame = nullptr;

CNetGame *pNetGame = nullptr;
CPlayerTags* pPlayerTags = nullptr;
CSnapShotHelper* pSnapShotHelper = nullptr;
CAudioStream* pAudioStream = nullptr;
CJavaWrapper* pJavaWrapper = nullptr;
CSettings* pSettings = nullptr;
//CVoice* pVoice = nullptr;

MaterialTextGenerator* pMaterialTextGenerator = nullptr;

bool bDebug = false;
bool bGameInited = false;
bool bNetworkInited = false;

uintptr_t g_libGTASA = 0x00;
uintptr_t g_libSAMP = 0x00;

void ApplyGlobalPatches();
void ApplyPatches_level0();
void ApplyMultiTouchPatches();
void InstallGlobalHooks();
void InstallSpecialHooks();
void InitRenderWareFunctions();
void InitializeGraphicsSystem();
void InstallCrashFixHooks();
void Log(const char* fmt, ...);

int work = 0;

void ReadSettingFile()
{
	pSettings = new CSettings();
}

int hashing(const char* str) {
	int hashing = 5381;
	int c;
	while (c = *str++) {
		hashing = ((hashing << 5) + hashing) + c; /* hash * 33 + c */
		if (hashing < 0) hashing = 100;
	}
	if (hashing < 0) hashing = 100;
	return hashing;
}

void DoDebugLoop()
{
	// ...
}

void DoDebugStuff()
{
	// ...

	RwMatrix mat = pGame->FindPlayerPed()->m_pPed->GetMatrix().ToRwMatrix();
	
	for (int i = 0; i < 100; i++)
	{
		CPlayerPed* ped = pGame->NewPlayer(i, mat.pos.x + i, mat.pos.y, mat.pos.z, 0.0f, false, false);
		//ped->SetCollisionChecking(false);
		//ped->SetGravityProcessing(false);
	}
}
struct sigaction act_old;
struct sigaction act1_old;
struct sigaction act2_old;
struct sigaction act3_old;

extern int g_iLastProcessedSkinCollision, g_iLastProcessedEntityCollision, g_iLastRenderedObject;
extern uintptr_t g_dwLastRetAddrCrash;
void handler(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if(info->si_signo == SIGSEGV)
	{
		Log("SIGSEGV | Fault address: 0x%x", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	if (act_old.sa_sigaction)
	{
		act_old.sa_sigaction(signum, info, contextPtr);
	}

	return;
}

void handler1(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if(info->si_signo == SIGABRT)
	{
		Log("SIGABRT | Fault address: 0x%x", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	if (act1_old.sa_sigaction)
	{
		act1_old.sa_sigaction(signum, info, contextPtr);
	}

	return;
}

void handler2(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if(info->si_signo == SIGFPE)
	{
		Log("SIGFPE | Fault address: 0x%x", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	if (act2_old.sa_sigaction)
	{
		act2_old.sa_sigaction(signum, info, contextPtr);
	}

	return;
}

void handler3(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if(info->si_signo == SIGBUS)
	{
		Log("SIGBUS | Fault address: 0x%x", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	if (act3_old.sa_sigaction)
	{
		act3_old.sa_sigaction(signum, info, contextPtr);
	}

	return;
}

void DoInitStuff()
{
	if (!pGame || !pUI) return;
	if (bGameInited == false)
	{
#ifdef HAS_CEF
		cef::setGamePath(g_pszStorage);
#endif

		pPlayerTags = new CPlayerTags();
		pSnapShotHelper = new CSnapShotHelper();
		pMaterialTextGenerator = new MaterialTextGenerator();
		pAudioStream = new CAudioStream();
		pAudioStream->Initialize();

		pUI->splashscreen()->setVisible(false);
			pUI->chat()->setVisible(true);
		pUI->buttonpanel()->setVisible(false);
		pUI->voicebutton()->setVisible(false);
		pUI->spawn()->setVisible(true);

		pGame->Initialize();
		pGame->SetMaxStats();
		pGame->ToggleThePassingOfTime(false);

		LogVoice("[dbg:samp:load] : module loaded");

		if (bDebug)
		{
            CCamera& TheCamera = *reinterpret_cast<CCamera*>(g_libGTASA + 0x9F86F8);
            //TheCamera.Restore();
            CCamera::SetBehindPlayer();
			pGame->DisplayHUD(true);
			pGame->EnableClock(false);
            DoDebugStuff();
		}

		bGameInited = true;
	}

	if (!bNetworkInited && !bDebug)
	{
					ReadSettingFile();
			if (!pSettings) return;
			//CServerInstance::initConnection(1);

		Log("Pre-CNetGame: host='%s' port=%d nick='%s'",
			pSettings->Get().szHost,
			pSettings->Get().iPort,
			pSettings->Get().szNickName);

		try
		{
			pNetGame = new CNetGame(
					pSettings->Get().szHost,
					pSettings->Get().iPort,
					pSettings->Get().szNickName,
					pSettings->Get().szPassword
			);
		}
		catch (const std::exception& e)
		{
			Log("[FATAL]: exception C++ pendant la creation de CNetGame: %s", e.what());
			throw;
		}
		catch (...)
		{
			Log("[FATAL]: exception inconnue (non std::exception) pendant la creation de CNetGame");
			throw;
		}

		Log("Post-CNetGame: pNetGame=%p", (void*)pNetGame);

		bNetworkInited = true;

        Log("DoInitStuff end");
	}
}

extern "C" {
		static void SetNativeStoragePath(JNIEnv* env, jstring path)
		{
			if (!env || !path) return;
			const char* value = env->GetStringUTFChars(path, nullptr);
			if (!value) return;
			snprintf(g_storageBuffer, sizeof(g_storageBuffer), "%s%s", value,
				(value[0] && value[strlen(value) - 1] == '/') ? "" : "/");
			g_pszStorage = g_storageBuffer;
			env->ReleaseStringUTFChars(path, value);
		}

		JNIEXPORT void JNICALL Java_com_rockstargames_oswrapper_GameNative_setNativeStoragePath(JNIEnv* env, jclass clazz, jstring path)
		{
			SetNativeStoragePath(env, path);
		}

		JNIEXPORT void JNICALL Java_com_rockstargames_oswrapper_GameNative_bindSampActivity(JNIEnv* env, jclass clazz, jobject activity)
		{
			if (!env || !activity || pJavaWrapper) return;
			pJavaWrapper = new CJavaWrapper(env, activity);
		}

		JNIEXPORT void JNICALL Java_com_gta_game_SAMP_initializeSAMP(JNIEnv *pEnv, jobject thiz)
		{
		        if (!pEnv || !thiz) return;
		        if (!pJavaWrapper) pJavaWrapper = new CJavaWrapper(pEnv, thiz);
		}

		JNIEXPORT void JNICALL Java_com_gta_game_SAMP_setNativeStoragePath(JNIEnv* env, jobject thiz, jstring path)
		{
			SetNativeStoragePath(env, path);
		}

		JNIEXPORT void JNICALL Java_com_gta_game_SAMP_openChatInput(JNIEnv *pEnv, jobject thiz)
		{
			if (pUI && pUI->chat()) pUI->chat()->touchPopEvent();
		}

		JNIEXPORT void JNICALL Java_com_gta_game_SAMP_sendChatInput(JNIEnv *pEnv, jobject thiz, jstring input)
		{
			if (!input || !pUI || !pUI->chat()) return;
			const char* text = pEnv->GetStringUTFChars(input, nullptr);
			if (text) {
				pUI->chat()->keyboardEvent(std::string(text));
				pEnv->ReleaseStringUTFChars(input, text);
			}
		}
	JNIEXPORT void JNICALL Java_com_gta_game_ui_dialog_DialogManager_sendDialogResponse(JNIEnv* pEnv, jobject thiz, jint i3, jint i, jint i2, jbyteArray str)
	{
		jboolean isCopy = true;

		if (!pEnv || !str) return;
		jbyte* pMsg = pEnv->GetByteArrayElements(str, &isCopy);
		jsize length = pEnv->GetArrayLength(str);
		if (!pMsg || length < 0) return;

		std::string szStr((char*)pMsg, length);
		
		if(pNetGame) {
			pNetGame->SendDialogResponse(i, i3, i2, (char*)szStr.c_str());
			//pGame->FindPlayerPed()->TogglePlayerControllableWithoutLock(true);
		}

		pEnv->ReleaseByteArrayElements(str, pMsg, JNI_ABORT);
	}
}

void MainLoop()
{
	if (!pGame || pGame->bIsGameExiting) return;

	DoInitStuff();

	static auto lastHudUpdate = std::chrono::steady_clock::now();
	const auto nowHud = std::chrono::steady_clock::now();
	const float hudDelta = std::chrono::duration<float>(nowHud - lastHudUpdate).count();
	if (pJavaWrapper && pGame && hudDelta >= 0.10f) {
		lastHudUpdate = nowHud;
		CPlayerPed* localPed = pGame->FindPlayerPed();
		if (localPed && localPed->m_pPed) {
							const bool dead = localPed->IsDead();
				const bool running = !dead && (localPed->m_pPed->m_nMoveState == PEDMOVE_RUN || localPed->m_pPed->m_nMoveState == PEDMOVE_SPRINT);
				CVehicleGTA* vehicle = localPed->GetGtaVehicle();
				const bool inVehicle = vehicle != nullptr;
				float vehicleSpeed = 0.0f;
				float vehicleHealth = 100.0f;
				bool handbrake = false;
				if (vehicle) {
					const CVector velocity = vehicle->m_vecMoveSpeed;
					vehicleSpeed = std::sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z) * 180.0f;
					if (vehicleSpeed < 0.0f) vehicleSpeed = 0.0f;
					if (vehicleSpeed > 240.0f) vehicleSpeed = 240.0f;
					vehicleHealth = vehicle->physicalFlags.bDestroyed ? 0.0f : 100.0f;
					handbrake = (vehicle->m_nVehicleUpperFlags & (1u << 5)) != 0;
				}

			static float energy = 100.0f;
			static bool wasDead = false;
			if (dead) energy = 0.0f;
			else if (wasDead) energy = 100.0f;
			else if (running) energy -= 24.0f * hudDelta;
			else energy += 18.0f * hudDelta;
			if (energy < 0.0f) energy = 0.0f;
			if (energy > 100.0f) energy = 100.0f;
			const bool exhausted = energy <= 0.1f;
			wasDead = dead;
							pJavaWrapper->UpdateHudState(localPed->GetHealth(), localPed->GetArmour(), 100.0f, 100.0f, energy, dead, running, exhausted, inVehicle, vehicleSpeed, vehicleHealth, handbrake);

		}
	}

	if (bDebug) {
		DoDebugLoop();
	}

	if (pNetGame) {
		pNetGame->Process();
	}

	if (pAudioStream) {
		pAudioStream->Process();
	}

}

void InitGui()
{
	Plugin::OnPluginLoad();
	Plugin::OnSampLoad();

    std::string font_path = string_format("%sSAMP/fonts/%s", g_pszStorage, FONT_NAME);
    pUI = new UI(ImVec2(RsGlobal->maximumWidth, RsGlobal->maximumHeight), font_path.c_str());
	pUI->initialize();
    pUI->performLayout();
}

#include "armhook/patch.h"
#include "util/CUtil.h"
//void SetUpGLHooks();
jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
	javaVM = vm;
	LOGI("SA-MP library loaded! Build time: " __DATE__ " " __TIME__);

	g_libGTASA = CUtil::FindLib("libGame.so");
	if (g_libGTASA == 0x00) {
		LOGE("libGame.so address was not found! ");
		return JNI_VERSION_1_6;
	}

	g_libSAMP = CUtil::FindLib("libsamp.so");
	if (g_libSAMP == 0x00) {
		LOGE("libsamp.so address was not found! ");
		return JNI_VERSION_1_6;
	}

	//firebase::crashlytics::Initialize();

	uintptr_t libGame = CUtil::FindLib("libGame.so");
	uintptr_t libmultiplayer = CUtil::FindLib("libsamp.so");
	uintptr_t libc = CUtil::FindLib("libc.so");

	Log("libGame.so: 0x%x", libGame);
	Log("libsamp.so: 0x%x", libmultiplayer);
	Log("libc.so: 0x%x", libc);

	char str[100];

	sprintf(str, "0x%x", libGame);
	//firebase::crashlytics::SetCustomKey("libGame.so", str);
	
	sprintf(str, "0x%x", libmultiplayer);
	//firebase::crashlytics::SetCustomKey("libsamp.so", str);

	sprintf(str, "0x%x", libc);
	//firebase::crashlytics::SetCustomKey("libc.so", str);

	CHook::InitHookStuff();
	InstallSpecialHooks();
	InstallCrashFixHooks();
	ApplyPatches_level0();
    //SetUpGLHooks();
    InitRenderWareFunctions();

	pGame = new CGame();

	//pVoice = new CVoice();
	//pVoice->Initialize(VOICE_FREQUENCY, CODEC_FREQUENCY, VOICE_SENDRRATE);

	//pthread_t thread;
	//pthread_create(&thread, 0, Init, 0);

	// IMPORTANT: sans pile alternative, un crash qui survient pendant un
	// stack overflow ne peut PAS etre livre a nos handlers (le noyau n'a
	// plus de place sur la pile courante pour empiler le contexte du
	// signal). Resultat: le process meurt en silence, sans aucun de nos
	// logs "SIGSEGV | Fault address...". On alloue donc une pile dediee
	// et on l'active avec SA_ONSTACK sur chaque handler.
	static uint8_t altStackBuffer[SIGSTKSZ * 4];
	stack_t altStack;
	altStack.ss_sp = altStackBuffer;
	altStack.ss_size = sizeof(altStackBuffer);
	altStack.ss_flags = 0;
	if (sigaltstack(&altStack, nullptr) != 0)
	{
		Log("[WARN]: sigaltstack a echoue, les crashs stack-overflow ne seront pas logues");
	}

	struct sigaction act;
	act.sa_sigaction = handler;
	sigemptyset(&act.sa_mask);
	act.sa_flags = SA_SIGINFO | SA_ONSTACK;
	sigaction(SIGSEGV, &act, &act_old);

	struct sigaction act1;
	act1.sa_sigaction = handler1;
	sigemptyset(&act1.sa_mask);
	act1.sa_flags = SA_SIGINFO | SA_ONSTACK;
	sigaction(SIGABRT, &act1, &act1_old);

	struct sigaction act2;
	act2.sa_sigaction = handler2;
	sigemptyset(&act2.sa_mask);
	act2.sa_flags = SA_SIGINFO | SA_ONSTACK;
	sigaction(SIGFPE, &act2, &act2_old);

	struct sigaction act3;
	act3.sa_sigaction = handler3;
	sigemptyset(&act3.sa_mask);
	act3.sa_flags = SA_SIGINFO | SA_ONSTACK;
	sigaction(SIGBUS, &act3, &act3_old);

	// SIGILL et SIGTRAP n'etaient pas du tout interceptes auparavant.
	// Un hook ARM/patch memoire mal ecrit (mauvais opcode, mauvais mode
	// thumb/arm) declenche typiquement SIGILL, pas SIGSEGV -> c'etait un
	// angle mort complet de ce systeme de logging.
	static struct sigaction act4_old;
	struct sigaction act4;
	act4.sa_sigaction = [](int signum, siginfo_t* info, void* contextPtr) {
		Log("SIGILL | Fault address: 0x%x", info->si_addr);
		CStackTrace::printBacktrace();
		if (act4_old.sa_sigaction) act4_old.sa_sigaction(signum, info, contextPtr);
	};
	sigemptyset(&act4.sa_mask);
	act4.sa_flags = SA_SIGINFO | SA_ONSTACK;
	sigaction(SIGILL, &act4, &act4_old);
		
	return JNI_VERSION_1_6;
}

uint32_t GetTickCount()
{
    return CTimer::m_snTimeInMillisecondsNonClipped;
}

void Log(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = g_pszStorage;


	if (flLog == nullptr && pszStorage != nullptr)
	{
		snprintf(buffer, sizeof(buffer), "%slogcat.txt", (pszStorage && pszStorage[0]) ? pszStorage : "/storage/emulated/0/data/");
		flLog = fopen(buffer, "a");
	}

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	LOGI("%s", buffer);
	firebase::crashlytics::Log(buffer);

	if (flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);

	return;
}

void LogVoice(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = g_pszStorage;

	if (flLog == nullptr)
	{
		const char* base = (pszStorage && pszStorage[0]) ? pszStorage : "/storage/emulated/0/data/";
		snprintf(buffer, sizeof(buffer), "%sSAMP/svlog.txt", base);
		flLog = fopen(buffer, "a");
	}

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	__android_log_write(ANDROID_LOG_INFO, "AXL", buffer);

	if (flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);

	return;
}
