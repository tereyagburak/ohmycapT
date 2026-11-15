/*
 * Copyright (C) 2013 Alexey Galakhov <agalakhov@gmail.com>
 *
 * Licensed under the GNU General Public License Version 3
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

// YENİ: Android NDK için global durum içermeyen G/Ç soyutlaması ve hata kodları

#pragma once

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#define CAPT_SUCCESS         0
#define CAPT_ERR_IO         -1
#define CAPT_ERR_TIMEOUT    -2
#define CAPT_ERR_CANCELLED  -3
#define CAPT_ERR_PROTOCOL   -4
#define CAPT_ERR_PRINTER    -5

/**
 * Android JNI köprüsü üzerinden sağlanan G/Ç fonksiyon göstericileri.
 */
struct capt_io {
    int (*write)(void *user, const uint8_t *buf, size_t len);
    int (*read)(void *user, uint8_t *buf, size_t max_len);
    void *user;
};

/**
 * C katmanındaki tüm oturum durumunu taşıyan bağlam yapısı (sıfır global durum kuralı).
 */
struct capt_ctx {
    struct capt_io io;
    volatile int cancelled;
    uint8_t iobuf[0x10000];
    size_t iosize;
    char last_error[256];
};

int capt_ctx_init(struct capt_ctx *c, struct capt_io io);
void capt_ctx_cancel(struct capt_ctx *c);
int capt_sleep_ms(struct capt_ctx *c, int ms);
