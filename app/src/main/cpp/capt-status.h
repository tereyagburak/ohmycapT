/*
 * Copyright (C) 2013 Alexey Galakhov <agalakhov@gmail.com>
 *
 * Licensed under the GNU General Public License Version 3
 */

// ORİJİNAL: capt-status.h
// YENİ: struct capt_ctx ile global durum kaldırıldı.

#pragma once

#include "capt_io.h"
#include <stdint.h>
#include <stdbool.h>

struct capt_status {
	uint16_t status[7];

	uint16_t page_decoding;
	uint16_t page_printing;
	uint16_t page_out;
	uint16_t page_completed;
	uint16_t page_received;
};

#define _FL(s, b) ((s << 16) | (1 << b))
enum capt_flags
{
	/* status[0] */
	CAPT_FL_READY1       = _FL(0, 15),
	CAPT_FL_READY2       = _FL(0, 12),
	CAPT_FL_JOBSTAT_CHNG = _FL(0, 9),
	CAPT_FL_XSTATUS_CHNG = _FL(0, 8),
	CAPT_FL_BUSY         = _FL(0, 7),
	CAPT_FL_UNINIT1      = _FL(0, 5),
	CAPT_FL_UNINIT2      = _FL(0, 4),
	CAPT_FL_BUFFERFULL   = _FL(0, 2),
	CAPT_FL_NOPAPER1     = _FL(0, 1),
	CAPT_FL_PROCESSING   = _FL(0, 0),
	/* status[1] */
	CAPT_FL_NOPAPER2     = _FL(1, 14),
	CAPT_FL_PROCESSING1  = _FL(1, 7),
	CAPT_FL_BUTTON       = _FL(1, 5),
	CAPT_FL_PRINTING     = _FL(1, 2),
	CAPT_FL_POWERUP      = _FL(1, 0),
	/* status[2] */
	CAPT_FL_nERROR       = _FL(2, 7),
	CAPT_FL_BUTTON1      = _FL(2, 8),
	/* status[3] */
	CAPT_FL_POWERUP1     = _FL(3, 12),
	/* status[4] */
	CAPT_FL_BUTTON_ON    = _FL(4, 0),
};
#undef _FL

static inline bool FLAG(const struct capt_status *status, enum capt_flags flag)
{
	return !! (status->status[flag >> 16] & (flag & 0xFFFF));
}

int capt_get_status(struct capt_ctx *c, struct capt_status *out);
void capt_decode_status(const uint8_t *s, size_t size, struct capt_status *status);
