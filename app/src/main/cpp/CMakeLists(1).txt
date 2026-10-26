cmake_minimum_required(VERSION 3.12)

include_directories(
        ${CMAKE_CURRENT_LIST_DIR}/opus/include
)

find_package(shadowhook REQUIRED CONFIG)
add_subdirectory(opus)
# arm64-v8a : client Crestwood (moteur 2.11, libGame.so)
# armeabi-v7a : client 32 bits base kuzian (moteur 2.10, libGTASA.so + classes Java Nv/Wardrum), voir samp32/
if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    add_subdirectory(samp32)
else()
    add_subdirectory(samp)
endif()

target_compile_options(opus PRIVATE -O3)
