/*
 * Copyright (C) 2013 Alexey Galakhov <agalakhov@gmail.com>
 *
 * Licensed under the GNU General Public License Version 3
 */

// ORİJİNAL: prn_lbp2900.c
// YENİ: capt_multi_send ile 0xD0A9 sayfa parametreleri ve doğru margin değerleri.

#include "prn_lbp2900.h"
#include "capt-command.h"
#include "capt-status.h"
#include "hiscoa-common.h"
#include "hiscoa-compress.h"
#include "word.h"

#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>

#define TAG "CaptNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static uint16_t s_job_id = 0;
static struct lbp2900_page_params s_page_params;
static int s_band_index = 0;
static int s_total_lines_sent = 0;

static const uint8_t magicbuf_0[] = {
	0x00, 0x00, 0x1E, 0x00, 0x00, 0x00, 0x00, 0x00
};

static const uint8_t magicbuf_2[] = {
	0xEE, 0xDB, 0xEA, 0xAD, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
};

static const uint8_t lbp3010_gpio_init[] = {
	0x13, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
};

static int wait_ready(struct capt_ctx *c)
{
	struct capt_status st;
	int retries = 50;
	while (retries-- > 0) {
		if (c->cancelled) return CAPT_ERR_CANCELLED;
		int ret = capt_get_status(c, &st);
		if (ret != CAPT_SUCCESS) return ret;

		if (!FLAG(&st, CAPT_FL_BUSY) && !FLAG(&st, CAPT_FL_BUFFERFULL)) {
			return CAPT_SUCCESS;
		}
		capt_sleep_ms(c, 100);
	}
	LOGE("wait_ready: Zaman aşımı (yazıcı meşgul)");
	return CAPT_ERR_TIMEOUT;
}

static int send_job_start(struct capt_ctx *c, uint8_t fg, uint16_t page)
{
	time_t rawtime = time(NULL);
	struct tm tm_buf;
	localtime_r(&rawtime, &tm_buf);

	uint8_t buf[72];
	memset(buf, 0, sizeof(buf));

	buf[4] = LO(page);
	buf[5] = HI(page);
	buf[16] = fg;
	buf[17] = 0x01;
	buf[18] = LO(s_job_id);
	buf[19] = HI(s_job_id);
	buf[20] = 0xC4;
	buf[21] = 0xFF;
	buf[22] = 0x88;
	buf[23] = 0xFF;

	uint16_t year = (uint16_t)tm_buf.tm_year;
	buf[24] = LO(year);
	buf[25] = HI(year);
	buf[26] = (uint8_t)tm_buf.tm_mon;
	buf[27] = (uint8_t)tm_buf.tm_mday;
	buf[28] = (uint8_t)tm_buf.tm_hour;
	buf[29] = (uint8_t)tm_buf.tm_min;
	buf[30] = (uint8_t)tm_buf.tm_sec;
	buf[31] = 0x01;

	return capt_sendrecv(c, CAPT_JOB_SETUP, buf, sizeof(buf), NULL, 0);
}

int lbp2900_print_begin(struct capt_ctx *c, const struct lbp2900_page_params *p)
{
	if (!c || !p) return CAPT_ERR_IO;
	s_page_params = *p;
	s_band_index = 0;
	s_total_lines_sent = 0;

	LOGD("lbp2900_print_begin: Genişlik=%d bayt, Yükseklik=%d satır", p->width_bytes, p->height_lines);

	uint8_t reply_buf[16];
	size_t reply_size = sizeof(reply_buf);

	// 1. CAPT_IDENT ve START_0
	capt_sendrecv(c, CAPT_IDENT, NULL, 0, NULL, 0);
	capt_sleep_ms(c, 500);

	capt_sendrecv(c, CAPT_START_0, NULL, 0, NULL, 0);

	// 2. İş Başlatma (CAPT_JOB_BEGIN)
	reply_size = sizeof(reply_buf);
	int ret = capt_sendrecv(c, CAPT_JOB_BEGIN, magicbuf_0, sizeof(magicbuf_0), reply_buf, &reply_size);
	if (ret != CAPT_SUCCESS) {
		LOGE("CAPT_JOB_BEGIN başarısız: %d", ret);
		return ret;
	}
	s_job_id = (reply_size >= 4) ? WORD(reply_buf[2], reply_buf[3]) : 1;
	LOGD("İş ID alındı: 0x%04X", s_job_id);

	capt_sendrecv(c, CAPT_GPIO, lbp3010_gpio_init, sizeof(lbp3010_gpio_init), NULL, 0);
	wait_ready(c);

	// 3. send_job_start(1, 0)
	send_job_start(c, 1, 0);
	wait_ready(c);

	// 4. Durum kontrolü ve açılış sekansı
	struct capt_status st;
	capt_get_status(c, &st);
	if (FLAG(&st, CAPT_FL_UNINIT1) || FLAG(&st, CAPT_FL_UNINIT2)) {
		LOGD("Yazıcı başlatılmamış durumda; açılış sekansı gönderiliyor...");
		capt_sendrecv(c, CAPT_START_1, NULL, 0, NULL, 0);
		capt_sendrecv(c, CAPT_START_2, NULL, 0, NULL, 0);
		capt_sendrecv(c, CAPT_START_3, NULL, 0, NULL, 0);
		wait_ready(c);

		capt_sendrecv(c, CAPT_UPLOAD_2, magicbuf_2, sizeof(magicbuf_2), NULL, 0);
		wait_ready(c);
	}

	// 5. Sayfa Parametreleri (0xD0A9 Çoklu Komut Bloğu)
	// ORİJİNAL: prn_lbp2900.c lbp2900_page_prologue
	uint8_t sz = 0x02; // A4
	uint8_t ink_k = (uint8_t)(p->toner_density << 2);
	uint16_t line_size = (uint16_t)p->width_bytes;
	uint16_t num_lines = (uint16_t)p->height_lines;
	uint16_t paper_width = 4960;  // A4 @ 600 DPI (yaklaşık 210mm)
	uint16_t paper_height = 7016; // A4 @ 600 DPI (yaklaşık 297mm)
	uint16_t margin_h = 118;      // 5mm @ 600 DPI (5 * 600 / 25.4 ≈ 118 px)
	uint16_t margin_w = 118;

	uint8_t pageparms[40] = {
		0x00, 0x00, 0x30, 0x2A, sz, 0x00, 0x00, 0x00,
		ink_k, 0x1C, 0x1C, 0x1C, 0x00, 0x11, 0x04, 0x00,
		0x01, 0x01, 0x02, 0x00, 0x00, 0x00,
		/* Kenar boşlukları */
		LO(margin_h), HI(margin_h),
		LO(margin_w), HI(margin_w),
		/* Resim genişlik/yükseklik */
		LO(line_size), HI(line_size),
		LO(num_lines), HI(num_lines),
		/* Kağıt genişlik/yükseklik */
		LO(paper_width), HI(paper_width),
		LO(paper_height), HI(paper_height),
		0x00, 0x00, 0x01, 0x00, 0x00, 0x00
	};

	uint8_t hiscoa_buf[8];
	size_t hs = hiscoa_format_params(hiscoa_buf, sizeof(hiscoa_buf), &hiscoa_default_params);

	// ORİJİNAL: capt_multi_begin(CAPT_SET_PARMS = 0xD0A9) ile tek hamlede gönderilir
	capt_multi_begin(c, CAPT_SET_PARMS);
	capt_multi_add(c, CAPT_SET_PARM_PAGE, pageparms, sizeof(pageparms));
	capt_multi_add(c, CAPT_SET_PARM_HISCOA, hiscoa_buf, hs);
	capt_multi_add(c, CAPT_SET_PARM_1, NULL, 0);
	capt_multi_add(c, CAPT_SET_PARM_2, NULL, 0);
	capt_multi_send(c);

	LOGD("lbp2900_print_begin başarıyla tamamlandı (0xD0A9).");
	return CAPT_SUCCESS;
}

int lbp2900_print_band(struct capt_ctx *c, const uint8_t *data, int lines, bool is_last)
{
	if (!c || !data || lines <= 0) return CAPT_ERR_IO;

	struct capt_status st;
	while (1) {
		if (c->cancelled) return CAPT_ERR_CANCELLED;
		capt_get_status(c, &st);
		if (!FLAG(&st, CAPT_FL_BUFFERFULL)) break;
		capt_sleep_ms(c, 100);
	}

	size_t raw_size = (size_t)s_page_params.width_bytes * (size_t)lines;
	size_t comp_buf_size = raw_size * 2 + 1024;
	uint8_t *comp_buf = (uint8_t *)malloc(comp_buf_size);
	if (!comp_buf) return CAPT_ERR_IO;

	enum hiscoa_eob_type eob = is_last ? HISCOA_EOB_LAST : HISCOA_EOB_NORMAL;
	size_t comp_size = hiscoa_compress_band(comp_buf, comp_buf_size, data,
	                                        s_page_params.width_bytes, lines,
	                                        eob, &hiscoa_default_params);

	LOGD("Bant #%d sıkıştırıldı: Ham=%zu B -> Sıkışmış=%zu B (Son bant: %d)",
	     s_band_index, raw_size, comp_size, is_last);

	const uint8_t *pdata = comp_buf;
	size_t remaining = comp_size;
	while (remaining > 0) {
		if (c->cancelled) {
			free(comp_buf);
			return CAPT_ERR_CANCELLED;
		}
		size_t chunk = (remaining > 0xFF00) ? 0xFF00 : remaining;
		capt_send(c, CAPT_PRINT_DATA, pdata, chunk);
		pdata += chunk;
		remaining -= chunk;
	}

	free(comp_buf);
	s_band_index++;
	s_total_lines_sent += lines;
	return CAPT_SUCCESS;
}

int lbp2900_print_end(struct capt_ctx *c)
{
	if (!c) return CAPT_ERR_IO;
	LOGD("lbp2900_print_end: Yazdırma tamamlanıyor, kağıt sürülüyor...");

	capt_send(c, CAPT_PRINT_DATA_END, NULL, 0);

	struct capt_status st;
	int retries = 60;
	while (retries-- > 0) {
		if (c->cancelled) return CAPT_ERR_CANCELLED;
		capt_get_status(c, &st);
		if (st.page_received == st.page_decoding) break;
		capt_sleep_ms(c, 500);
	}

	send_job_start(c, 2, st.page_decoding);
	wait_ready(c);

	uint8_t fire_buf[2] = { LO(st.page_decoding), HI(st.page_decoding) };
	capt_sendrecv(c, CAPT_FIRE, fire_buf, sizeof(fire_buf), NULL, 0);
	wait_ready(c);

	send_job_start(c, 6, st.page_decoding);

	retries = 90;
	while (retries-- > 0) {
		if (c->cancelled) return CAPT_ERR_CANCELLED;
		capt_get_status(c, &st);
		if (st.page_out == st.page_decoding) {
			LOGD("Kağıt başarıyla dışarı çıktı!");
			break;
		}
		if (FLAG(&st, CAPT_FL_NOPAPER1) || FLAG(&st, CAPT_FL_NOPAPER2)) {
			LOGE("Kağıt bitti hatası!");
			return CAPT_ERR_PRINTER;
		}
		capt_sleep_ms(c, 500);
	}

	send_job_start(c, 4, st.page_completed);
	uint8_t jbuf[2] = { LO(s_job_id), HI(s_job_id) };
	capt_sendrecv(c, CAPT_JOB_END, jbuf, sizeof(jbuf), NULL, 0);

	LOGD("Yazdırma işi başarıyla bitti.");
	return CAPT_SUCCESS;
}

int lbp2900_print_bitmap(struct capt_ctx *c, const uint8_t *raw_1bit, int width_bytes, int height_lines, int invert)
{
	struct lbp2900_page_params p;
	p.width_bytes = width_bytes;
	p.height_lines = height_lines;
	p.toner_density = 7;
	p.invert = invert;

	int ret = lbp2900_print_begin(c, &p);
	if (ret != CAPT_SUCCESS) return ret;

	int line = 0;
	while (line < height_lines) {
		if (c->cancelled) return CAPT_ERR_CANCELLED;
		int lines_in_band = LBP2900_BAND_LINES;
		if (line + lines_in_band > height_lines) {
			lines_in_band = height_lines - line;
		}
		bool is_last = (line + lines_in_band >= height_lines);

		const uint8_t *band_ptr = raw_1bit + (size_t)line * (size_t)width_bytes;
		uint8_t *temp_band = NULL;

		if (invert) {
			size_t band_bytes = (size_t)lines_in_band * (size_t)width_bytes;
			temp_band = (uint8_t *)malloc(band_bytes);
			if (temp_band) {
				for (size_t i = 0; i < band_bytes; ++i) {
					temp_band[i] = ~band_ptr[i];
				}
				band_ptr = temp_band;
			}
		}

		ret = lbp2900_print_band(c, band_ptr, lines_in_band, is_last);
		if (temp_band) free(temp_band);
		if (ret != CAPT_SUCCESS) return ret;

		line += lines_in_band;
	}

	return lbp2900_print_end(c);
}
