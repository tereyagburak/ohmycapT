/*
 * YENİ: dither.c - Floyd-Steinberg Hata Difüzyonu
 */

#include "dither.h"
#include <string.h>
#include <stdlib.h>

void dither_argb_to_1bit(const uint32_t *argb_pixels,
                         int width, int height,
                         int out_line_bytes,
                         uint8_t *out_1bit_buf,
                         int16_t *error_line_buf,
                         int invert)
{
    // out_1bit_buf sıfırla (0 = beyaz)
    memset(out_1bit_buf, 0x00, (size_t)height * (size_t)out_line_bytes);

    // Her satır için geçici sonraki satır hata dizisi
    int16_t *curr_err = error_line_buf;
    int16_t *next_err = (int16_t *)calloc(width + 2, sizeof(int16_t));
    if (!next_err) return;

    for (int y = 0; y < height; ++y) {
        memset(next_err, 0, (width + 2) * sizeof(int16_t));
        uint8_t *out_line = out_1bit_buf + (y * out_line_bytes);

        for (int x = 0; x < width; ++x) {
            uint32_t c = argb_pixels[y * width + x];
            // ARGB -> Gri ton (Luminance)
            // L = (77*R + 150*G + 29*B) >> 8
            uint32_t r = (c >> 16) & 0xFF;
            uint32_t g = (c >> 8) & 0xFF;
            uint32_t b = c & 0xFF;
            int gray = (int)((77 * r + 150 * g + 29 * b) >> 8);

            // Önceki piksellerden gelen hatayı ekle
            int pixel_val = gray + curr_err[x + 1];
            if (pixel_val < 0) pixel_val = 0;
            if (pixel_val > 255) pixel_val = 255;

            // Eşikleme (128 eşik değeri)
            int bit;
            int error;
            if (pixel_val < 128) {
                // Siyah piksel
                bit = 1;
                error = pixel_val - 0;
            } else {
                // Beyaz piksel
                bit = 0;
                error = pixel_val - 255;
            }

            if (invert) {
                bit = !bit;
            }

            if (bit) {
                int byte_idx = x / 8;
                int bit_idx = 7 - (x % 8); // MSB-first
                if (byte_idx < out_line_bytes) {
                    out_line[byte_idx] |= (1 << bit_idx);
                }
            }

            // Floyd-Steinberg Hata Dağıtımı:
            //       X   7/16
            // 3/16 5/16 1/16
            curr_err[x + 2] += (error * 7) / 16;
            next_err[x]     += (error * 3) / 16;
            next_err[x + 1] += (error * 5) / 16;
            next_err[x + 2] += (error * 1) / 16;
        }

        // Satır bittiğinde next_err'ı curr_err'a kopyala
        memcpy(curr_err, next_err, (width + 2) * sizeof(int16_t));
    }

    free(next_err);
}
