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

// YENİ: capt_io.c - İptal destekli uyku ve bağlam başlatma

#include "capt_io.h"

#include <string.h>
#include <unistd.h>

int capt_ctx_init(struct capt_ctx *c, struct capt_io io)
{
    if (!c) return CAPT_ERR_IO;
    memset(c, 0, sizeof(*c));
    c->io = io;
    c->cancelled = 0;
    return CAPT_SUCCESS;
}

void capt_ctx_cancel(struct capt_ctx *c)
{
    if (c) {
        c->cancelled = 1;
    }
}

int capt_sleep_ms(struct capt_ctx *c, int ms)
{
    while (ms > 0) {
        if (c && c->cancelled) {
            return CAPT_ERR_CANCELLED;
        }
        int step = (ms > 10) ? 10 : ms;
        usleep(step * 1000);
        ms -= step;
    }
    return (c && c->cancelled) ? CAPT_ERR_CANCELLED : CAPT_SUCCESS;
}
