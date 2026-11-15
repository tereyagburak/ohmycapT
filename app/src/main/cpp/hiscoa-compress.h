/*
 * Copyright (C) 2013 Alexey Galakhov <agalakhov@gmail.com>
 *
 * Licensed under the GNU General Public License Version 3
 */

// ORİJİNAL: hiscoa-compress.h
// YENİ: hiscoa_decode_band testi için eklendi.

#pragma once

#include "hiscoa-common.h"
#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

size_t hiscoa_compress_band(void *buf, size_t size,
	const void *band, unsigned line_size, unsigned nlines,
	enum hiscoa_eob_type eob_type,
	const struct hiscoa_params *params);

size_t hiscoa_format_params(void *buf, size_t size,
	const struct hiscoa_params *params);

// YENİ: 3a Doğrulama Testi için çözücü
int hiscoa_decode_band(const void *comp_buf, size_t comp_size,
	void *out_band, unsigned line_size, unsigned nlines);
