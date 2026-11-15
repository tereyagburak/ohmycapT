/*
 * Copyright (C) 2013 Alexey Galakhov <agalakhov@gmail.com>
 *
 * Licensed under the GNU General Public License Version 3
 */

// YENİ: prn_lbp2900.h - Canon LBP2900 yazdırma iş akışı ve sayfa kontrolü

#pragma once

#include "capt_io.h"
#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#define LBP2900_LINE_SIZE    592  // Canon LBP2900 600 DPI LINESIZE (bayt)
#define LBP2900_BAND_LINES   70   // Canon LBP2900 varsayılan bant yüksekliği (satır)
#define LBP2900_PAGE_LINES   7016 // A4 600 DPI satır sayısı (yaklaşık 297mm)

struct lbp2900_page_params {
    int width_bytes;
    int height_lines;
    int toner_density; // 0-7, varsayılan 7
    int invert;        // 1: ters çevir (siyah/beyaz polarite)
};

int lbp2900_print_begin(struct capt_ctx *c, const struct lbp2900_page_params *p);
int lbp2900_print_band(struct capt_ctx *c, const uint8_t *data, int lines, bool is_last);
int lbp2900_print_end(struct capt_ctx *c);
int lbp2900_print_bitmap(struct capt_ctx *c, const uint8_t *raw_1bit, int width_bytes, int height_lines, int invert);
