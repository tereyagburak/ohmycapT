/*
 * Copyright (C) 2013 Alexey Galakhov <agalakhov@gmail.com>
 *
 * Licensed under the GNU General Public License Version 3
 */

// ORİJİNAL: hiscoa-common.h
// YENİ: Doğrudan korundu.

#pragma once

#include <stdint.h>
#include <stddef.h>

enum hiscoa_eob_type {
	HISCOA_EOB_NORMAL = 0x0,
	HISCOA_EOB_LAST = 0x01,
};

struct hiscoa_params {
	int origin_3;
	int origin_5;
	int origin_0;
	int origin_2;
	int origin_4;
};

extern const struct hiscoa_params hiscoa_default_params;
