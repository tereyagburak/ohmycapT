# YENİ: Faz 4 Native Kütüphane Yapılandırması (16 KB Sayfa Boyutu ve Dither dahil)
cmake_minimum_required(VERSION 3.22.1)

project("captnative")

add_library(captnative SHARED
    native-lib.c
    capt_io.c
    capt-command.c
    capt-status.c
    hiscoa-common.c
    hiscoa-compress.c
    prn_lbp2900.c
    dither.c
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_include_directories(captnative PRIVATE .)

target_compile_options(captnative PRIVATE -std=c11 -Wall -Wextra)

# Android 15+ ve 16 KB sayfa boyutu (page size) uyumluluğu için 16384 bayt hizalama
target_link_options(captnative PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(captnative
    ${log-lib}
    ${jnigraphics-lib}
)
