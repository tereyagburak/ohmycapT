// YENİ: native-lib.c - JNI G/Ç, Dithering ve Bitmap Tabanlı Sayfa Yazdırma

#include "capt_io.h"
#include "capt-status.h"
#include "capt-command.h"
#include "hiscoa-common.h"
#include "hiscoa-compress.h"
#include "prn_lbp2900.h"
#include "dither.h"

#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#define TAG "CaptNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM *g_vm = NULL;
static jmethodID g_mid_write = NULL;
static jmethodID g_mid_read = NULL;
static jobject g_transport_obj = NULL;
static jbyteArray g_reusable_byte_array = NULL;
static struct capt_ctx g_ctx;

// Floyd-Steinberg bantlar arası hata satırı
static int16_t *g_error_line = NULL;
static int g_error_line_width = 0;

static JNIEnv* get_current_jni_env(void)
{
    if (!g_vm) return NULL;
    JNIEnv *env = NULL;
    jint res = (*g_vm)->GetEnv(g_vm, (void**)&env, JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != JNI_OK) {
            return NULL;
        }
    } else if (res != JNI_OK) {
        return NULL;
    }
    return env;
}

static int jni_capt_write(void *user, const uint8_t *buf, size_t len)
{
    (void)user;
    JNIEnv *env = get_current_jni_env();
    if (!env) {
        return CAPT_ERR_IO;
    }

    if (!g_transport_obj || !g_mid_write || !g_reusable_byte_array) {
        return CAPT_ERR_IO;
    }

    if (len > 16384) len = 16384;
    (*env)->SetByteArrayRegion(env, g_reusable_byte_array, 0, (jsize)len, (const jbyte*)buf);

    jint written = (*env)->CallIntMethod(env, g_transport_obj, g_mid_write, g_reusable_byte_array, (jint)len);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return CAPT_ERR_IO;
    }
    return (int)written;
}

static int jni_capt_read(void *user, uint8_t *buf, size_t max_len)
{
    (void)user;
    JNIEnv *env = get_current_jni_env();
    if (!env) {
        return CAPT_ERR_IO;
    }

    if (!g_transport_obj || !g_mid_read || !g_reusable_byte_array) {
        return CAPT_ERR_IO;
    }

    if (max_len > 16384) max_len = 16384;
    jint n = (*env)->CallIntMethod(env, g_transport_obj, g_mid_read, g_reusable_byte_array, (jint)max_len);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return CAPT_ERR_IO;
    }
    if (n > 0) {
        (*env)->GetByteArrayRegion(env, g_reusable_byte_array, 0, n, (jbyte*)buf);
    }
    return (int)n;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)reserved;
    g_vm = vm;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass cls = (*env)->FindClass(env, "com/example/captprint/CaptUsbTransport");
    if (!cls) return JNI_ERR;

    g_mid_write = (*env)->GetMethodID(env, cls, "write", "([BI)I");
    g_mid_read = (*env)->GetMethodID(env, cls, "read", "([BI)I");
    if (!g_mid_write || !g_mid_read) return JNI_ERR;

    jbyteArray localArr = (*env)->NewByteArray(env, 16384);
    g_reusable_byte_array = (*env)->NewGlobalRef(env, localArr);
    (*env)->DeleteLocalRef(env, localArr);

    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL
Java_com_example_captprint_CaptNative_nativeOpen(JNIEnv *env, jobject thiz, jobject transport)
{
    (void)thiz;
    if (g_transport_obj) {
        (*env)->DeleteGlobalRef(env, g_transport_obj);
        g_transport_obj = NULL;
    }
    g_transport_obj = (*env)->NewGlobalRef(env, transport);

    struct capt_io io;
    io.write = jni_capt_write;
    io.read = jni_capt_read;
    io.user = NULL;

    return capt_ctx_init(&g_ctx, io);
}

JNIEXPORT jstring JNICALL
Java_com_example_captprint_CaptNative_nativeQueryStatus(JNIEnv *env, jobject thiz)
{
    (void)thiz;
    struct capt_status status;
    int ret = capt_get_status(&g_ctx, &status);
    if (ret != CAPT_SUCCESS) {
        char err[128];
        snprintf(err, sizeof(err), "C Katmanı Hatası: %d", ret);
        return (*env)->NewStringUTF(env, err);
    }

    bool no_paper = FLAG(&status, CAPT_FL_NOPAPER1) || FLAG(&status, CAPT_FL_NOPAPER2);
    bool buffer_full = FLAG(&status, CAPT_FL_BUFFERFULL);
    bool busy = FLAG(&status, CAPT_FL_BUSY);
    bool printing = FLAG(&status, CAPT_FL_PRINTING);
    bool has_error = !FLAG(&status, CAPT_FL_nERROR);

    char res[512];
    snprintf(res, sizeof(res),
             "C-DURUM (0xA0A8):\n"
             "  STATUS0=0x%04X, STATUS1=0x%04X, STATUS2=0x%04X\n"
             "  Kağıt Yok: %s | Tampon Dolu: %s | Meşgul: %s\n"
             "  Yazıyor: %s | Hata: %s\n"
             "  Sayaçlar (Çözülen/Basılan/Çıkan): %u / %u / %u",
             status.status[0], status.status[1], status.status[2],
             no_paper ? "EVET" : "HAYIR",
             buffer_full ? "EVET" : "HAYIR",
             busy ? "EVET" : "HAYIR",
             printing ? "EVET" : "HAYIR",
             has_error ? "VAR (nERROR=0)" : "YOK",
             status.page_decoding, status.page_printing, status.page_out);

    return (*env)->NewStringUTF(env, res);
}

// ------------------- PDF BANT YAZDIRMA METOTLARI -------------------

JNIEXPORT jint JNICALL
Java_com_example_captprint_CaptNative_nativeStartPage(JNIEnv *env, jobject thiz, jint widthBytes, jint heightLines, jint tonerDensity, jboolean invert)
{
    (void)env;
    (void)thiz;

    struct lbp2900_page_params p;
    p.width_bytes = widthBytes;
    p.height_lines = heightLines;
    p.toner_density = tonerDensity;
    p.invert = invert ? 1 : 0;

    // Hata tamponunu sıfırla
    if (g_error_line) {
        free(g_error_line);
        g_error_line = NULL;
    }
    int pixel_width = widthBytes * 8;
    g_error_line = (int16_t *)calloc(pixel_width + 2, sizeof(int16_t));
    g_error_line_width = pixel_width;

    return lbp2900_print_begin(&g_ctx, &p);
}

JNIEXPORT jint JNICALL
Java_com_example_captprint_CaptNative_nativeDitherAndPrintBand(JNIEnv *env, jobject thiz, jobject bitmap, jint lines, jboolean isLast, jboolean invert)
{
    (void)thiz;
    AndroidBitmapInfo info;
    void *pixels = NULL;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Bitmap bilgisi alınamadı");
        return CAPT_ERR_IO;
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) {
        LOGE("Bitmap pikselleri kilitlenemedi");
        return CAPT_ERR_IO;
    }

    int width = (int)info.width;
    int height = (int)lines;
    int line_bytes = LBP2900_LINE_SIZE;

    // 1-bit monochrome tampon
    size_t mono_size = (size_t)line_bytes * (size_t)height;
    uint8_t *mono_buf = (uint8_t *)malloc(mono_size);
    if (!mono_buf) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return CAPT_ERR_IO;
    }

    if (!g_error_line || g_error_line_width != width) {
        if (g_error_line) free(g_error_line);
        g_error_line = (int16_t *)calloc(width + 2, sizeof(int16_t));
        g_error_line_width = width;
    }

    // Floyd-Steinberg dither
    dither_argb_to_1bit((const uint32_t *)pixels, width, height, line_bytes, mono_buf, g_error_line, invert ? 1 : 0);

    AndroidBitmap_unlockPixels(env, bitmap);

    // Sıkıştır ve bulk transfer ile gönder
    int ret = lbp2900_print_band(&g_ctx, mono_buf, height, isLast);
    free(mono_buf);

    return ret;
}

JNIEXPORT jint JNICALL
Java_com_example_captprint_CaptNative_nativeEndPage(JNIEnv *env, jobject thiz)
{
    (void)env;
    (void)thiz;

    if (g_error_line) {
        free(g_error_line);
        g_error_line = NULL;
    }

    return lbp2900_print_end(&g_ctx);
}

// -------------------------------------------------------------------

JNIEXPORT jint JNICALL
Java_com_example_captprint_CaptNative_nativePrintTestPattern(JNIEnv *env, jobject thiz, jint patternType, jboolean invert)
{
    (void)env;
    (void)thiz;

    int width_bytes = LBP2900_LINE_SIZE;
    int height_lines = LBP2900_PAGE_LINES;

    struct lbp2900_page_params p;
    p.width_bytes = width_bytes;
    p.height_lines = height_lines;
    p.toner_density = 7;
    p.invert = invert ? 1 : 0;

    int ret = lbp2900_print_begin(&g_ctx, &p);
    if (ret != CAPT_SUCCESS) {
        LOGE("nativePrintTestPattern begin hatası: %d", ret);
        return ret;
    }

    int band_lines = LBP2900_BAND_LINES;
    size_t band_bytes = (size_t)band_lines * (size_t)width_bytes;
    uint8_t *band_buf = (uint8_t *)malloc(band_bytes);
    if (!band_buf) return CAPT_ERR_IO;

    int current_line = 0;
    while (current_line < height_lines) {
        if (g_ctx.cancelled) {
            free(band_buf);
            return CAPT_ERR_CANCELLED;
        }

        int lines = band_lines;
        if (current_line + lines > height_lines) {
            lines = height_lines - current_line;
        }
        bool is_last = (current_line + lines >= height_lines);

        memset(band_buf, 0x00, band_bytes);

        for (int l = 0; l < lines; ++l) {
            int page_y = current_line + l;
            uint8_t *line_ptr = band_buf + (l * width_bytes);

            if (patternType == 0) {
                if (page_y >= 300 && page_y < 330) {
                    memset(line_ptr, 0xFF, width_bytes);
                }
            } else if (patternType == 1) {
                if ((page_y % 256) < 64) {
                    memset(line_ptr, 0xFF, width_bytes);
                }
            } else if (patternType == 2) {
                int row_block = (page_y / 128) % 2;
                for (int col = 0; col < width_bytes; ++col) {
                    int col_block = (col / 16) % 2;
                    if (row_block ^ col_block) {
                        line_ptr[col] = 0xFF;
                    } else {
                        line_ptr[col] = 0x00;
                    }
                }
            }
        }

        if (invert) {
            for (size_t i = 0; i < band_bytes; ++i) {
                band_buf[i] = ~band_buf[i];
            }
        }

        ret = lbp2900_print_band(&g_ctx, band_buf, lines, is_last);
        if (ret != CAPT_SUCCESS) {
            LOGE("nativePrintTestPattern bant gönderme hatası: %d", ret);
            free(band_buf);
            return ret;
        }

        current_line += lines;
    }

    free(band_buf);
    return lbp2900_print_end(&g_ctx);
}

JNIEXPORT void JNICALL
Java_com_example_captprint_CaptNative_nativeCancel(JNIEnv *env, jobject thiz)
{
    (void)env;
    (void)thiz;
    capt_ctx_cancel(&g_ctx);
}

JNIEXPORT void JNICALL
Java_com_example_captprint_CaptNative_nativeClose(JNIEnv *env, jobject thiz)
{
    (void)thiz;
    if (g_transport_obj) {
        (*env)->DeleteGlobalRef(env, g_transport_obj);
        g_transport_obj = NULL;
    }
}
