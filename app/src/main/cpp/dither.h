/*
 * YENİ: dither.h - Floyd-Steinberg Dithering ve 1-bit Rasterlaştırma
 */

#pragma once

#include <stdint.h>
#include <stddef.h>

/**
 * ARGB_8888 piksellerini 1-bit monochrome (1=siyah, 0=beyaz) formatına çevirir.
 * Floyd-Steinberg hata difüzyonu bantlar arasında korunur.
 *
 * @param argb_pixels Android Bitmap ARGB_8888 piksel dizisi
 * @param width Piksel genişliği (örn. 4736)
 * @param height Bant satır sayısı (örn. 70 veya 140)
 * @param out_line_bytes Çıkış 1-bit satır boyutu (örn. LBP2900 için 592 bayt)
 * @param out_1bit_buf Sıkıştırmaya gidecek 1-bit çıkış tamponu
 * @param error_line_buf Bantlar arası hata satırı (Genişlik + 2 elemanlı int16 dizisi)
 * @param invert 1 ise siyah/beyaz polaritesini ters çevirir
 */
void dither_argb_to_1bit(const uint32_t *argb_pixels,
                         int width, int height,
                         int out_line_bytes,
                         uint8_t *out_1bit_buf,
                         int16_t *error_line_buf,
                         int invert);
