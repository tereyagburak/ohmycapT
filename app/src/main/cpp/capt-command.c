/*
 * Copyright (C) 2013 Alexey Galakhov <agalakhov@gmail.com>
 *
 * Licensed under the GNU General Public License Version 3
 */

// ORİJİNAL: capt-command.c
// YENİ: capt_multi_* komut paketleme eklendi.

#include "capt-command.h"
#include "word.h"

#include <android/log.h>
#include <string.h>
#include <stdio.h>

#define TAG "CaptNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void log_hex(const char *prefix, const uint8_t *buf, size_t len)
{
    char hex[512];
    size_t pos = 0;
    for (size_t i = 0; i < len && pos + 4 < sizeof(hex); ++i) {
        pos += snprintf(hex + pos, sizeof(hex) - pos, "%02X ", buf[i]);
    }
    LOGD("%s %zuB: %s", prefix, len, hex);
}

static int capt_send_buf(struct capt_ctx *ctx)
{
    const uint8_t *iopos = ctx->iobuf;
    size_t iosize = ctx->iosize;

    log_hex("TX", ctx->iobuf, ctx->iosize);

    while (iosize > 0) {
        if (ctx->cancelled) return CAPT_ERR_CANCELLED;
        size_t sendsize = (iosize > 4096) ? 4096 : iosize;
        int written = ctx->io.write(ctx->io.user, iopos, sendsize);
        if (written <= 0) {
            LOGE("USB yazma hatası (written=%d)", written);
            return CAPT_ERR_IO;
        }
        iopos += written;
        iosize -= written;
    }
    return CAPT_SUCCESS;
}

static int capt_recv_buf(struct capt_ctx *ctx, size_t offset, size_t expected)
{
    if (offset + expected > sizeof(ctx->iobuf)) {
        LOGE("Tampon taşma hatası (offset=%zu, expected=%zu)", offset, expected);
        return CAPT_ERR_IO;
    }

    uint8_t tmp[512];
    int n = ctx->io.read(ctx->io.user, tmp, sizeof(tmp));
    if (n <= 0) {
        LOGE("USB okuma hatası / zaman aşımı (read=%d)", n);
        return CAPT_ERR_TIMEOUT;
    }

    log_hex("RX", tmp, (size_t)n);

    if (offset + (size_t)n > sizeof(ctx->iobuf)) {
        LOGE("Tampon aşımı");
        return CAPT_ERR_IO;
    }

    memcpy(ctx->iobuf + offset, tmp, (size_t)n);
    ctx->iosize = offset + (size_t)n;
    return CAPT_SUCCESS;
}

static void capt_copy_cmd(struct capt_ctx *ctx, uint16_t cmd, const void *buf, size_t size)
{
    if (buf) {
        memcpy(ctx->iobuf + ctx->iosize + 4, buf, size);
    } else {
        size = 0;
    }
    ctx->iobuf[ctx->iosize + 0] = LO(cmd);
    ctx->iobuf[ctx->iosize + 1] = HI(cmd);
    ctx->iobuf[ctx->iosize + 2] = LO(size + 4);
    ctx->iobuf[ctx->iosize + 3] = HI(size + 4);
    ctx->iosize += size + 4;
}

int capt_send(struct capt_ctx *ctx, uint16_t cmd, const void *buf, size_t size)
{
    if (!ctx) return CAPT_ERR_IO;
    ctx->iosize = 0;
    capt_copy_cmd(ctx, cmd, buf, size);
    return capt_send_buf(ctx);
}

int capt_sendrecv(struct capt_ctx *ctx, uint16_t cmd, const void *buf, size_t size, void *reply, size_t *reply_size)
{
    if (!ctx) return CAPT_ERR_IO;
    int ret = capt_send(ctx, cmd, buf, size);
    if (ret < 0) return ret;

    ret = capt_recv_buf(ctx, 0, 6);
    if (ret < 0) return ret;

    if (ctx->iosize < 6 || WORD(ctx->iobuf[0], ctx->iobuf[1]) != cmd) {
        LOGE("Hatalı yazıcı cevabı! Beklenen %02X %02X, Gelen: %02X %02X",
             LO(cmd), HI(cmd), ctx->iobuf[0], ctx->iobuf[1]);
        return CAPT_ERR_PROTOCOL;
    }

    size_t expected_total = WORD(ctx->iobuf[2], ctx->iobuf[3]);

    while (ctx->iosize < expected_total) {
        if (ctx->cancelled) return CAPT_ERR_CANCELLED;
        size_t needed = expected_total - ctx->iosize;
        ret = capt_recv_buf(ctx, ctx->iosize, needed);
        if (ret < 0) return ret;
    }

    if (reply && reply_size) {
        size_t payload_len = (ctx->iosize >= 4) ? (ctx->iosize - 4) : 0;
        size_t copysize = (*reply_size < payload_len) ? *reply_size : payload_len;
        memcpy(reply, ctx->iobuf + 4, copysize);
        *reply_size = copysize;
    }
    return CAPT_SUCCESS;
}

int capt_multi_begin(struct capt_ctx *ctx, uint16_t cmd)
{
    if (!ctx) return CAPT_ERR_IO;
    ctx->iobuf[0] = LO(cmd);
    ctx->iobuf[1] = HI(cmd);
    ctx->iosize = 4;
    return CAPT_SUCCESS;
}

int capt_multi_add(struct capt_ctx *ctx, uint16_t cmd, const void *buf, size_t size)
{
    if (!ctx) return CAPT_ERR_IO;
    capt_copy_cmd(ctx, cmd, buf, size);
    return CAPT_SUCCESS;
}

int capt_multi_send(struct capt_ctx *ctx)
{
    if (!ctx) return CAPT_ERR_IO;
    ctx->iobuf[2] = LO(ctx->iosize);
    ctx->iobuf[3] = HI(ctx->iosize);
    return capt_send_buf(ctx);
}
