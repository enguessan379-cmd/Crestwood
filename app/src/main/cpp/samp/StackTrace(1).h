//
// Created by x1y2z on 20.03.2023.
//

#ifndef LIVERUSSIA_CSTACKTRACE_H
#define LIVERUSSIA_CSTACKTRACE_H

#include <stdio.h>
#include <dlfcn.h>
#include <dlfcn.h>
#include <unwind.h>
#include "../main.h"

#if VER_x32
#define PRINT_CRASH_STATES(context) \
	Log("register states:"); \
	Log("r0: 0x%X, r1: 0x%X, r2: 0x%X, r3: 0x%X", (context)->uc_mcontext.arm_r0, (context)->uc_mcontext.arm_r1, (context)->uc_mcontext.arm_r2, (context)->uc_mcontext.arm_r3); \
	Log("r4: 0x%x, r5: 0x%x, r6: 0x%x, r7: 0x%x", (context)->uc_mcontext.arm_r4, (context)->uc_mcontext.arm_r5, (context)->uc_mcontext.arm_r6, (context)->uc_mcontext.arm_r7); \
	Log("r8: 0x%x, r9: 0x%x, sl: 0x%x, fp: 0x%x", (context)->uc_mcontext.arm_r8, (context)->uc_mcontext.arm_r9, (context)->uc_mcontext.arm_r10, (context)->uc_mcontext.arm_fp); \
	Log("ip: 0x%x, sp: 0x%x, lr: 0x%x, pc: 0x%x", (context)->uc_mcontext.arm_ip, (context)->uc_mcontext.arm_sp, (context)->uc_mcontext.arm_lr, (context)->uc_mcontext.arm_pc); \
    Log("1: libGame.so + 0x%X", context->uc_mcontext.arm_pc - g_libGTASA); \
    Log("2: libGame.so + 0x%X", context->uc_mcontext.arm_lr - g_libGTASA); \
    Log("1: libsamp.so + 0x%X", context->uc_mcontext.arm_pc - g_libSAMP); \
    Log("2: libsamp.so + 0x%X", context->uc_mcontext.arm_lr - g_libSAMP);
#else
#define PRINT_CRASH_STATES(context) \
    Log("x0: 0x%llx x1: 0x%llx x2: 0x%llx x3: 0x%llx", (unsigned long long)(context)->uc_mcontext.regs[0], (unsigned long long)(context)->uc_mcontext.regs[1], (unsigned long long)(context)->uc_mcontext.regs[2], (unsigned long long)(context)->uc_mcontext.regs[3]); \
    Log("x19: 0x%llx x20: 0x%llx x21: 0x%llx x22: 0x%llx", (unsigned long long)(context)->uc_mcontext.regs[19], (unsigned long long)(context)->uc_mcontext.regs[20], (unsigned long long)(context)->uc_mcontext.regs[21], (unsigned long long)(context)->uc_mcontext.regs[22]); \
    Log("sp: 0x%llx pc: 0x%llx lr: 0x%llx", (unsigned long long)(context)->uc_mcontext.sp, (unsigned long long)(context)->uc_mcontext.pc, (unsigned long long)(context)->uc_mcontext.regs[30]); \
    Log("1: libGame.so + 0x%llx", context->uc_mcontext.pc - g_libGTASA); \
    Log("2: libGame.so + 0x%llx", context->uc_mcontext.regs[30] - g_libGTASA); \
    Log("1: libsamp.so + 0x%llx", context->uc_mcontext.pc - g_libSAMP); \
    Log("2: libsamp.so + 0x%llx", context->uc_mcontext.regs[30] - g_libSAMP);
#endif

void Log(const char* fmt, ...);
extern int g_iLastRenderedObject;
class CStackTrace
{
public:
    static void printBacktrace()
    {
        Log("Last rendered object: %d", g_iLastRenderedObject);
        Log("------------ START BACKTRACE ------------");
        Log(" ");
        PrintStackTrace();
    }

private:
    static _Unwind_Reason_Code TraceFunction(_Unwind_Context* context, void* arg) {
        uintptr_t pc = _Unwind_GetIP(context);

        Dl_info info;
        if (dladdr(reinterpret_cast<void*>(pc), &info) && info.dli_sname != nullptr) {
            Log("[adr: %p samp: %p gta: %p] %s\n",
                     reinterpret_cast<void*>(pc),
                     reinterpret_cast<void*>(pc - g_libSAMP),
                     reinterpret_cast<void*>(pc - g_libGTASA),
                     info.dli_sname);
        } else {
            Log("[adr: %p samp: %p gta: %p] name not found\n",
                     reinterpret_cast<void*>(pc),
                     reinterpret_cast<void*>(pc - g_libSAMP),
                     reinterpret_cast<void*>(pc - g_libGTASA));
        }

        return _URC_NO_REASON;
    }

    static void PrintStackTrace() {
        _Unwind_Backtrace(TraceFunction, nullptr);
    }

};

#endif //LIVERUSSIA_CSTACKTRACE_H
