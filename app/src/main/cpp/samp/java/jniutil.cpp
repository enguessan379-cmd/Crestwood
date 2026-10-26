#include "jniutil.h"
#include "game/game.h"
extern CGame *pGame;

JNIEnv* CJavaWrapper::GetEnv()
{
    JNIEnv* env = nullptr;
    int getEnvStat = javaVM->GetEnv((void**)& env, JNI_VERSION_1_4);

    if (getEnvStat == JNI_EDETACHED)
    {
        Log("GetEnv: not attached");
        if (javaVM->AttachCurrentThread(&env, NULL) != 0)
        {
            Log("Failed to attach");
            return nullptr;
        }
    }
    if (getEnvStat == JNI_EVERSION)
    {
        Log("GetEnv: version not supported");
        return nullptr;
    }

    if (getEnvStat == JNI_ERR)
    {
        Log("GetEnv: JNI_ERR");
        return nullptr;
    }

    return env;
}

CJavaWrapper::CJavaWrapper(JNIEnv *env, jobject activity)
{
    this->activity = env->NewGlobalRef(activity);

    jclass clas = env->GetObjectClass(activity);
    if(!clas)
    {
        Log("no clas");
        return;
    }

    s_showLoadingScreen = env->GetMethodID(clas, "showLoadingScreen", "()V");
    s_hideLoadingScreen = env->GetMethodID(clas, "hideLoadingScreen", "()V");

    s_setPauseState = env->GetMethodID(clas, "setPauseState", "(Z)V");
    
    s_ShowDialog = env->GetMethodID(clas, "showDialog", "(II[B[B[B[B)V");

	 s_exitGame = env->GetMethodID(clas, "exitGame", "()V");

	 s_showEditObject = env->GetMethodID(clas, "showEditObject", "()V");
	 s_hideEditObject = env->GetMethodID(clas, "hideEditObject", "()V");
	 s_updateHudState = env->GetMethodID(clas, "updateHudState", "(FFFFFZZZZFFZFFFFF)V");

	 s_clothesOpen = env->GetMethodID(clas, "clothesOpen", "(Ljava/lang/String;)V");
	 s_clothesState = env->GetMethodID(clas, "clothesState", "(Ljava/lang/String;)V");
	 s_clothesClose = env->GetMethodID(clas, "clothesClose", "()V");
	 if (env->ExceptionCheck()) env->ExceptionClear(); // methodes absentes (ancien APK) : le panneau ImGui reste le repli

	 env->DeleteLocalRef(clas);
}

static void CallClothesString(CJavaWrapper* w, jmethodID mid, const std::string& json)
{
    if (!javaVM || !w->activity || !mid) return;
    JNIEnv* p = w->GetEnv();
    if (!p) return;
    jstring js = p->NewStringUTF(json.c_str());
    if (!js) { if (p->ExceptionCheck()) p->ExceptionClear(); return; }
    p->CallVoidMethod(w->activity, mid, js);
    p->DeleteLocalRef(js);
    if (p->ExceptionCheck()) { p->ExceptionDescribe(); p->ExceptionClear(); }
}

void CJavaWrapper::ClothesPanelOpen(const std::string& json)  { CallClothesString(this, s_clothesOpen, json); }
void CJavaWrapper::ClothesPanelState(const std::string& json) { CallClothesString(this, s_clothesState, json); }

void CJavaWrapper::ClothesPanelClose()
{
    if (!javaVM || !activity || !s_clothesClose) return;
    JNIEnv* p = GetEnv();
    if (!p) return;
    p->CallVoidMethod(activity, s_clothesClose);
    if (p->ExceptionCheck()) { p->ExceptionDescribe(); p->ExceptionClear(); }
}

void CJavaWrapper::ShowLoadingScreen()
{
    if (!javaVM || !activity || !s_showLoadingScreen) return;
    JNIEnv* p = GetEnv();
    if (!p) return;
    p->CallVoidMethod(activity, s_showLoadingScreen);
    EXCEPTION_CHECK(p);
}

void CJavaWrapper::HideLoadingScreen()
{
    if (!javaVM || !activity || !s_hideLoadingScreen) return;
    JNIEnv* p = GetEnv();
    if (!p) return;
    p->CallVoidMethod(activity, s_hideLoadingScreen);
    EXCEPTION_CHECK(p);
}

void CJavaWrapper::UpdateHudState(float health, float armour, float thirst, float hunger, float energy,
                                  bool dead, bool running, bool exhausted, bool inVehicle,
                                  float speed, float vehicleHealth, bool handbrake,
                                  float posX, float posY, float camFx, float camFy, float heading)
{
    if (!javaVM || !activity || !s_updateHudState) return;
    JNIEnv* env = GetEnv();
    if (!env) return;
    env->CallVoidMethod(activity, s_updateHudState, health, armour, thirst, hunger, energy,
                        dead, running, exhausted, inVehicle, speed, vehicleHealth, handbrake,
                        posX, posY, camFx, camFy, heading);
    EXCEPTION_CHECK(env);
}

void CJavaWrapper::SetPauseState(bool pause)
{
    JNIEnv* p;
    javaVM->GetEnv((void**)&p, JNI_VERSION_1_6);
    p->CallVoidMethod(activity, s_setPauseState, pause);
    EXCEPTION_CHECK(p);
}

void CJavaWrapper::ShowDialog(int dialogStyle, int dialogID, char* title, char* text, char* button1, char* button2)
{
    JNIEnv* env;
    javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (!env)
    {
        Log("No env");
        return;
    }

    std::string sTitle(title);
    std::string sText(text);
    std::string sButton1(button1);
    std::string sButton2(button2);

    jbyteArray jstrTitle = as_byte_array((unsigned char*)sTitle.c_str(), sTitle.length());
    jbyteArray jstrText = as_byte_array((unsigned char*)sText.c_str(), sText.length());
    jbyteArray jstrButton1 = as_byte_array((unsigned char*)sButton1.c_str(), sButton1.length());
    jbyteArray jstrButton2 = as_byte_array((unsigned char*)sButton2.c_str(), sButton2.length());

    env->CallVoidMethod(activity, s_ShowDialog, dialogID, dialogStyle, jstrTitle, jstrText, jstrButton1, jstrButton2);

    EXCEPTION_CHECK(env);
}

void CJavaWrapper::exitGame() {

    JNIEnv* env;
    javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (!env)
    {
        Log("No env");
        return;
    }

    env->CallVoidMethod(this->activity, this->s_exitGame);
}

void CJavaWrapper::ShowEditObject() {

    JNIEnv* env;
    javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (!env)
    {
        Log("No env");
        return;
    }

    env->CallVoidMethod(this->activity, this->s_showEditObject);
}

void CJavaWrapper::HideEditObject() {

    JNIEnv* env;
    javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (!env)
    {
        Log("No env");
        return;
    }

    env->CallVoidMethod(this->activity, this->s_hideEditObject);
}
