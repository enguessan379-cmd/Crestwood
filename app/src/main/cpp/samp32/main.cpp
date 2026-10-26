#include <jni.h>
#include <pthread.h>
#include <syscall.h>

#include "main.h"
#include "game/game.h"
#include "net/netgame.h"
#include "gui/gui.h"
#include "playertags.h"
#include "audiostream.h"
#include "java/jniutil.h"
#include <dlfcn.h>
#include "StackTrace.h"

// voice
#include "voice_new/Plugin.h"

#include "vendor/armhook/patch.h"
#include "vendor/str_obfuscator/str_obfuscator.hpp"

#include "settings.h"

#include "crashlytics.h"

#include <mutex>
#include <string>
#include <vector>
#include <chrono>
#include <cmath>


/*
Peerapol Unarak
*/

JavaVM* javaVM;

char* g_pszStorage = nullptr;

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
void FLog(const char* fmt, ...);
//void MyLog(const char* fmt, ...);

int work = 0;

void ReadSettingFile()
{
	/*char path[255] = { 0 };
	//sprintf(path, "%ssamp.set", g_pszStorage);
	sprintf(path, "%sNickName.ini", g_pszStorage);

	FILE* fp = fopen(path, "r");
	if (fp == NULL) return;

	char buf[1024];

	// nickname
	if (fgets(buf, 1024, fp) != NULL) {
		buf[strcspn(buf, "\n\r")] = 0;
		strcpy(g_nick, buf);
	}

	fclose(fp);*/

	pSettings = new CSettings();

	firebase::crashlytics::SetUserId(pSettings->Get().szNickName);
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

	if (act_old.sa_sigaction)
	{
		act_old.sa_sigaction(signum, info, contextPtr);
	}

	if(info->si_signo == SIGSEGV)
	{
		FLog("SIGSEGV | Fault address: 0x%x", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	return;
}

void handler1(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if (act1_old.sa_sigaction)
	{
		act1_old.sa_sigaction(signum, info, contextPtr);
	}

	if(info->si_signo == SIGABRT)
	{
		FLog("SIGABRT | Fault address: 0x%x", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	return;
}

void handler2(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if (act2_old.sa_sigaction)
	{
		act2_old.sa_sigaction(signum, info, contextPtr);
	}

	if(info->si_signo == SIGFPE)
	{
		FLog("SIGFPE | Fault address: 0x%x", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	return;
}

void handler3(int signum, siginfo_t *info, void* contextPtr)
{
	ucontext* context = (ucontext_t*)contextPtr;

	if (act3_old.sa_sigaction)
	{
		act3_old.sa_sigaction(signum, info, contextPtr);
	}

	if(info->si_signo == SIGBUS)
	{
		FLog("SIGBUS | Fault address: 0x%x", info->si_addr);

		PRINT_CRASH_STATES(context);

		CStackTrace::printBacktrace();
	}

	return;
}

// ---------------------------------------------------------------------------------------------
// [Crestwood] Pont avec le HUD HTML du client 32 bits (com.gta.game32.SAMP) :
//  - commandes / messages envoyes par les pages HTML (file d'attente videe dans MainLoop, thread du jeu)
//  - ouverture du chat SA-MP natif depuis le bouton "chat" du HUD
//  - g_bHtmlHud : le HUD HTML remplace celui de GTA (radar, vie, argent) -> CHud::Draw n'est plus appele
// ---------------------------------------------------------------------------------------------
bool g_bHtmlHud = false;
static std::mutex g_hudCmdMutex;
static std::vector<std::string> g_hudCmdQueue;
static bool g_hudOpenChat = false;

static void FlushHudCommands()
{
	std::vector<std::string> cmds;
	bool openChat = false;
	{
		std::lock_guard<std::mutex> lock(g_hudCmdMutex);
		cmds.swap(g_hudCmdQueue);
		openChat = g_hudOpenChat;
		g_hudOpenChat = false;
	}
	if (openChat && pUI && pUI->keyboard() && pUI->chat()) pUI->keyboard()->show(pUI->chat());
	if (!pNetGame) return;
	for (const std::string& c : cmds) {
		if (c.empty()) continue;
		if (c[0] == '/') pNetGame->SendChatCommand(c.c_str());
		else pNetGame->SendChatMessage(c.c_str());
	}
}

// Etat du joueur pour le HUD HTML (vie, gilet, vehicule, position + camera pour la minimap/GPS), 10 fois par seconde.
static void PushHudState()
{
	if (!pJavaWrapper || !pGame) return;
	static auto lastHudUpdate = std::chrono::steady_clock::now();
	const auto nowHud = std::chrono::steady_clock::now();
	const float hudDelta = std::chrono::duration<float>(nowHud - lastHudUpdate).count();
	if (hudDelta < 0.10f) return;
	lastHudUpdate = nowHud;

	CPlayerPed* localPed = pGame->FindPlayerPed();
	if (!localPed || !localPed->m_pPed) return;
	const bool dead = localPed->IsDead();
	const bool running = !dead && (localPed->m_pPed->m_nMoveState == PEDMOVE_RUN || localPed->m_pPed->m_nMoveState == PEDMOVE_SPRINT);
	CVehicleGTA* vehicle = localPed->IsInVehicle() ? localPed->GetGtaVehicle() : nullptr;
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

	CCamera& hudCamera = *reinterpret_cast<CCamera*>(g_libGTASA + (VER_x32 ? 0x00951FA8 : 0xBBA8D0));
	const CVector hudPos = vehicle ? vehicle->GetPosition() : localPed->m_pPed->GetPosition();
	const CVector hudCamFwd = hudCamera.GetForwardVector();
	const float hudHeading = vehicle ? vehicle->GetHeading() : localPed->m_pPed->GetHeading();
	pJavaWrapper->UpdateHudState(localPed->GetHealth(), localPed->GetArmour(), 100.0f, 100.0f, energy, dead, running, exhausted,
		inVehicle, vehicleSpeed, vehicleHealth, handbrake, hudPos.x, hudPos.y, hudCamFwd.x, hudCamFwd.y, hudHeading);
}

void DoInitStuff()
{
	if (bGameInited == false)
	{
		pPlayerTags = new CPlayerTags();
		pSnapShotHelper = new CSnapShotHelper();
		pMaterialTextGenerator = new MaterialTextGenerator();
		pAudioStream = new CAudioStream();
		pAudioStream->Initialize();

		pUI->splashscreen()->setVisible(false);
		pUI->chat()->setVisible(true);
		pUI->buttonpanel()->setVisible(true);

		pGame->Initialize();
		pGame->SetMaxStats();
		pGame->ToggleThePassingOfTime(false);

		// voice
		/*LogVoice("[dbg:samp:load] : module loading...");

		for (const auto& loadCallback : Samp::loadCallbacks) {
			if (loadCallback != nullptr) {
				loadCallback();
			}
		}

		Samp::loadStatus = true;*/

		LogVoice("[dbg:samp:load] : module loaded");

		if (bDebug)
		{
            CCamera& TheCamera = *reinterpret_cast<CCamera*>(g_libGTASA + (VER_x32 ? 0x00951FA8 : 0xBBA8D0));
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
		//ReadSettingFile();

		// [Crestwood] serveur lu dans SAMP/settings.ini (ecrit par le launcher) au lieu d'une IP en dur
		FLog("Pre-CNetGame: host='%s' port=%d nick='%s'", pSettings->Get().szHost, pSettings->Get().iPort, pSettings->Get().szNickName);
		pNetGame = new CNetGame(pSettings->Get().szHost, pSettings->Get().iPort, pSettings->Get().szNickName, pSettings->Get().szPassword);
		bNetworkInited = true;

        FLog("DoInitStuff end");
	}
}

extern "C" {
	JNIEXPORT void JNICALL Java_com_gta_game32_SAMP_initializeSAMP(JNIEnv *pEnv, jobject thiz)
	{
		pJavaWrapper = new CJavaWrapper(pEnv, thiz);

	}
	JNIEXPORT void JNICALL Java_com_gta_game32_SAMP_onInputEnd(JNIEnv *pEnv, jobject thiz, jbyteArray str)
	{
		if(pUI)
		{
			pUI->keyboard()->sendForGB(pEnv, thiz, str);
		}
	}
	// [Crestwood] HUD HTML -> serveur : commande ou message (octets Windows-1252)
	JNIEXPORT void JNICALL Java_com_gta_game32_SAMP_sendChatBytes(JNIEnv *pEnv, jobject thiz, jbyteArray str)
	{
		if (!pEnv || !str) return;
		jsize length = pEnv->GetArrayLength(str);
		if (length <= 0 || length > 255) return;
		jbyte* data = pEnv->GetByteArrayElements(str, nullptr);
		if (!data) return;
		std::string text((const char*)data, (size_t)length);
		pEnv->ReleaseByteArrayElements(str, data, JNI_ABORT);
		std::lock_guard<std::mutex> lock(g_hudCmdMutex);
		if (g_hudCmdQueue.size() < 64) g_hudCmdQueue.push_back(text);
	}
	JNIEXPORT void JNICALL Java_com_gta_game32_SAMP_openChatNative(JNIEnv *pEnv, jobject thiz)
	{
		std::lock_guard<std::mutex> lock(g_hudCmdMutex);
		g_hudOpenChat = true;
	}
	JNIEXPORT void JNICALL Java_com_gta_game32_SAMP_setHtmlHud(JNIEnv *pEnv, jobject thiz, jboolean enabled)
	{
		g_bHtmlHud = enabled;
	}
	// [Crestwood] dialogues HTML (com.gta.game.ui.dialog.DialogManager, partage avec le client 64 bits)
	JNIEXPORT void JNICALL Java_com_gta_game_ui_dialog_DialogManager_sendDialogResponse(JNIEnv* pEnv, jobject thiz, jint i3, jint i, jint i2, jbyteArray str)
	{
		if (!pEnv || !str) return;
		jbyte* pMsg = pEnv->GetByteArrayElements(str, nullptr);
		jsize length = pEnv->GetArrayLength(str);
		if (!pMsg || length < 0) return;
		std::string szStr((char*)pMsg, length);
		if (pNetGame) pNetGame->SendDialogResponse(i, i3, i2, (char*)szStr.c_str());
		pEnv->ReleaseByteArrayElements(str, pMsg, JNI_ABORT);
	}
	JNIEXPORT void JNICALL Java_com_gta_game32_SAMP_onEventBackPressed(JNIEnv *pEnv, jobject thiz)
	{
		if(pSettings)
		{
			if(pSettings->Get().iAndroidKeyboard)
				pJavaWrapper->HideKeyboard();
		}
	}
	JNIEXPORT void JNICALL Java_com_gta_game32_ui_dialog_DialogManager_sendDialogResponse(JNIEnv* pEnv, jobject thiz, jint i3, jint i, jint i2, jbyteArray str)
	{
		jboolean isCopy = true;

		jbyte* pMsg = pEnv->GetByteArrayElements(str, &isCopy);
		jsize length = pEnv->GetArrayLength(str);

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
	if (pGame->bIsGameExiting) return;

	DoInitStuff();

	FlushHudCommands();
	PushHudState();

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
	// new voice
	Plugin::OnPluginLoad();
	Plugin::OnSampLoad();

	std::string font_path = string_format("%sSAMP/fonts/%s", g_pszStorage, FONT_NAME);
	pUI = new UI(ImVec2(RsGlobal->maximumWidth, RsGlobal->maximumHeight), font_path.c_str());
	pUI->initialize();
	pUI->performLayout();
}

#include "game/multitouch.h"
#include "armhook/patch.h"
#include "util/CUtil.h"
void SetUpGLHooks();
jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
	javaVM = vm;
	LOGI("SA-MP library loaded! Build time: " __DATE__ " " __TIME__);

	g_libGTASA = CUtil::FindLib("libGTASA.so");
	if (g_libGTASA == 0x00) {
		LOGE("libGTASA.so address was not found! ");
		return JNI_VERSION_1_6;
	}

	g_libSAMP = CUtil::FindLib("libsamp.so");
	if (g_libSAMP == 0x00) {
		LOGE("libsamp.so address was not found! ");
		return JNI_VERSION_1_6;
	}

	firebase::crashlytics::Initialize();

	uintptr_t libgtasa = CUtil::FindLib("libGTASA.so");
	uintptr_t libsamp = CUtil::FindLib("libsamp.so");
	uintptr_t libc = CUtil::FindLib("libc.so");

	FLog("libGTASA.so: 0x%x", libgtasa);
	FLog("libsamp.so: 0x%x", libsamp);
	FLog("libc.so: 0x%x", libc);

	char str[100];

	sprintf(str, "0x%x", libgtasa);
	firebase::crashlytics::SetCustomKey("libGTASA.so", str);
	
	sprintf(str, "0x%x", libsamp);
	firebase::crashlytics::SetCustomKey("libsamp.so", str);

	sprintf(str, "0x%x", libc);
	firebase::crashlytics::SetCustomKey("libc.so", str);

	CHook::InitHookStuff();
	InstallSpecialHooks();
	ApplyPatches_level0();
    //SetUpGLHooks();
    InitRenderWareFunctions();
    MultiTouch::initialize();

	pGame = new CGame();

	//pVoice = new CVoice();
	//pVoice->Initialize(VOICE_FREQUENCY, CODEC_FREQUENCY, VOICE_SENDRRATE);

	//pthread_t thread;
	//pthread_create(&thread, 0, Init, 0);

	struct sigaction act;
	act.sa_sigaction = handler;
	sigemptyset(&act.sa_mask);
	act.sa_flags = SA_SIGINFO;
	sigaction(SIGSEGV, &act, &act_old);

	struct sigaction act1;
	act1.sa_sigaction = handler1;
	sigemptyset(&act1.sa_mask);
	act1.sa_flags = SA_SIGINFO;
	sigaction(SIGABRT, &act1, &act1_old);

	struct sigaction act2;
	act2.sa_sigaction = handler2;
	sigemptyset(&act2.sa_mask);
	act2.sa_flags = SA_SIGINFO;
	sigaction(SIGFPE, &act2, &act2_old);

	struct sigaction act3;
	act3.sa_sigaction = handler3;
	sigemptyset(&act3.sa_mask);
	act3.sa_flags = SA_SIGINFO;
	sigaction(SIGBUS, &act3, &act3_old);
		
	return JNI_VERSION_1_6;
}

uint32_t GetTickCount()
{
    return CTimer::m_snTimeInMillisecondsNonClipped;
}	

void FLog(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = g_pszStorage;


	if (flLog == nullptr && pszStorage != nullptr)
	{
		sprintf(buffer, "%s/samp_log.txt", pszStorage);
		//LOGI("buffer: %s", buffer);
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

void ChatLog(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = g_pszStorage;


	if (flLog == nullptr && pszStorage != nullptr)
	{
		sprintf(buffer, "%s/chat_log.txt", pszStorage);
		flLog = fopen(buffer, "a");
	}

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	if (flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);

	return;
}

void MyLog(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = g_pszStorage;


	if (flLog == nullptr && pszStorage != nullptr)
	{
		sprintf(buffer, "%s/samp_log.txt", pszStorage);
		//LOGI("buffer: %s", buffer);
		flLog = fopen(buffer, "a");
	}

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	if (flLog == nullptr) return;
	fprintf(flLog, "%s\n", buffer);
	fflush(flLog);

	return;
}

void MyLog2(const char* fmt, ...)
{
	char buffer[0xFF];
	static FILE* flLog = nullptr;
	const char* pszStorage = g_pszStorage;


	if (flLog == nullptr && pszStorage != nullptr)
	{
		sprintf(buffer, "%s/samp_log.txt", pszStorage);
		//LOGI("buffer: %s", buffer);
		flLog = fopen(buffer, "a");
	}

	memset(buffer, 0, sizeof(buffer));

	va_list arg;
	va_start(arg, fmt);
	vsnprintf(buffer, sizeof(buffer), fmt, arg);
	va_end(arg);

	if (pUI) pUI->chat()->addDebugMessage(buffer);

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

	if (flLog == nullptr && pszStorage != nullptr)
	{
		sprintf(buffer, "%sSAMP/%s", pszStorage, SV::kLogFileName);
		flLog = fopen(buffer, "w");
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