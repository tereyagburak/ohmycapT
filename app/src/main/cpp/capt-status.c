/*
 * Copyright (C) 2013 Alexey Galakhov <agalakhov@gmail.com>
 *
 * Licensed under the GNU General Public License Version 3
 */

// ORİJİNAL: capt-status.c
// YENİ: Global static status değişkeni kaldırıldı; struct capt_ctx ile parametrik yapıldı.

#include "capt-status.h"
#include "capt-command.h"
#include "word.h"

#include <string.h>

void capt_decode_status(const uint8_t *s, size_t size, struct capt_status *status)
{
	if (!s || !status || size < 2) return;
	memset(status, 0, sizeof(*status));

	status->status[0] = WORD(s[0], s[1]);

	if (size <= 2)
		return;

	status->status[1] = WORD(s[8], s[9]);

	if (size <= 10)
		return;

	status->status[2] = WORD(s[10], s[11]);
	status->status[3] = WORD(s[12], s[13]);

	status->page_decoding  = WORD(s[14], s[15]);
	status->page_printing  = WORD(s[16], s[17]);
	status->page_out       = WORD(s[18], s[19]);
	status->page_completed = WORD(s[20], s[21]);

	if (size >= 26) {
		status->status[4] = WORD(s[24], s[25]);
	}
	if (size >= 32) {
		status->status[5] = WORD(s[30], s[31]);
	}
	if (size >= 36) {
		status->page_received  = WORD(s[34], s[35]);
	}
	if (size >= 40) {
		status->status[6] = WORD(s[38], s[39]);
	}
}

int capt_get_status(struct capt_ctx *c, struct capt_status *out)
{
	uint8_t buf[512];
	size_t size = sizeof(buf);
	int ret = capt_sendrecv(c, CAPT_CHKXSTATUS, NULL, 0, buf, &size);
	if (ret != CAPT_SUCCESS) {
		return ret;
	}
	capt_decode_status(buf, size, out);
	return CAPT_SUCCESS;
}
