/*
 * Copyright (C) 2013 Alexey Galakhov <agalakhov@gmail.com>
 *
 * Licensed under the GNU General Public License Version 3
 */

// ORİJİNAL: capt-command.h
// YENİ: capt_multi_* fonksiyonları eklendi.

#pragma once

#include "capt_io.h"
#include <stdint.h>
#include <stddef.h>

enum capt_command {
	CAPT_NOP             = 0xA0A0,
	CAPT_CHKJOBSTAT      = 0xA0A1,
	CAPT_CHKXSTATUS      = 0xA0A8,

	CAPT_IEEE_IDENT      = 0xA1A0,
	CAPT_IDENT           = 0xA1A1,

	CAPT_JOB_BEGIN       = 0xA2A0,
	CAPT_START_0         = 0xA3A2,

	CAPT_PRINT_DATA      = 0xC0A0,
	CAPT_PRINT_DATA_END  = 0xC0A4,

	CAPT_SET_PARM_PAGE   = 0xD0A0,
	CAPT_SET_PARM_1      = 0xD0A1,
	CAPT_SET_PARM_2      = 0xD0A2,
	CAPT_SET_PARM_HISCOA = 0xD0A4,
	CAPT_SET_PARMS       = 0xD0A9,

	CAPT_CHKSTATUS       = 0xE0A0,
	CAPT_START_2         = 0xE0A2,
	CAPT_START_1         = 0xE0A3,
	CAPT_START_3         = 0xE0A4,
	CAPT_UPLOAD_2        = 0xE0A5,
	CAPT_LBP3000_SETUP_0 = 0xE0A6,
	CAPT_FIRE            = 0xE0A7,
	CAPT_JOB_END         = 0xE0A9,
	CAPT_LBP6000_SETUP_0 = 0xE0BA,

	CAPT_JOB_SETUP       = 0xE1A1,
	CAPT_GPIO            = 0xE1A2,
};

int capt_send(struct capt_ctx *ctx, uint16_t cmd, const void *buf, size_t size);
int capt_sendrecv(struct capt_ctx *ctx, uint16_t cmd, const void *buf, size_t size, void *reply, size_t *reply_size);

int capt_multi_begin(struct capt_ctx *ctx, uint16_t cmd);
int capt_multi_add(struct capt_ctx *ctx, uint16_t cmd, const void *buf, size_t size);
int capt_multi_send(struct capt_ctx *ctx);
