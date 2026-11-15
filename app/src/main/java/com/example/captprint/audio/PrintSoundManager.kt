package com.example.captprint.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import androidx.annotation.RawRes
import com.example.captprint.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Canon LBP2900 Windows sürücüsünden (CNAB4) alınan gerçek .wav seslerini
 * yazdırma olaylarına göre çalan ses yöneticisi.
 *
 * - cnab4dn.wav : Yazdırma tamamlandı
 * - cnab4rm.wav : Tek tek yazdırmada sonraki sayfa / kağıt bekleme uyarısı
 * - cnab4er.wav : Yazdırma hatası
 * - cnab4se.wav : Donanım / kağıt uyarısı (Kağıt yok, sıkışma, kapak açık)
 */
class PrintSoundManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isSoundsEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUNDS_ENABLED, true))
    val isSoundsEnabled: StateFlow<Boolean> = _isSoundsEnabled.asStateFlow()

    private val loadedSounds = ConcurrentHashMap<Int, Boolean>()

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(audioAttributes)
        .build()
        .apply {
            setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    loadedSounds[sampleId] = true
                }
            }
        }

    private val doneSoundId: Int = loadSound(R.raw.cnab4dn)
    private val stepPageSoundId: Int = loadSound(R.raw.cnab4rm)
    private val errorSoundId: Int = loadSound(R.raw.cnab4er)
    private val warningSoundId: Int = loadSound(R.raw.cnab4se)

    private fun loadSound(@RawRes resId: Int): Int {
        return try {
            soundPool.load(appContext, resId, 1)
        } catch (e: Exception) {
            Log.w(TAG, "Ses yüklenemedi (resId=$resId): ${e.message}")
            0
        }
    }

    fun setSoundsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUNDS_ENABLED, enabled).apply()
        _isSoundsEnabled.value = enabled
    }

    /** Yazdırma başarıyla tamamlandığında çalar (cnab4dn.wav). */
    fun playPrintDone(forcePreview: Boolean = false) {
        playInternal(doneSoundId, R.raw.cnab4dn, forcePreview)
    }

    /** Tek tek yazdırmada sayfa basılıp sonraki kağıt beklendiğinde çalar (cnab4rm.wav). */
    fun playStepPageReady(forcePreview: Boolean = false) {
        playInternal(stepPageSoundId, R.raw.cnab4rm, forcePreview)
    }

    /** Yazdırma sırasında hata oluştuğunda çalar (cnab4er.wav). */
    fun playPrintError(forcePreview: Boolean = false) {
        playInternal(errorSoundId, R.raw.cnab4er, forcePreview)
    }

    /** Kağıt bitti, kağıt sıkıştı veya kapak açık gibi uyarılarda çalar (cnab4se.wav). */
    fun playWarning(forcePreview: Boolean = false) {
        playInternal(warningSoundId, R.raw.cnab4se, forcePreview)
    }

    private fun playInternal(soundId: Int, @RawRes fallbackResId: Int, forcePreview: Boolean) {
        if (!forcePreview && !_isSoundsEnabled.value) return

        try {
            if (soundId != 0 && loadedSounds[soundId] == true) {
                soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
                return
            }

            // SoundPool henüz yüklemeyi bitirmediyse MediaPlayer ile çal
            val mp = MediaPlayer.create(appContext, fallbackResId)
            mp?.apply {
                setAudioAttributes(audioAttributes)
                setOnCompletionListener { it.release() }
                setOnErrorListener { player, _, _ ->
                    player.release()
                    true
                }
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ses çalma hatası: ${e.message}")
        }
    }

    fun release() {
        try {
            soundPool.release()
        } catch (ignored: Exception) {}
    }

    companion object {
        private const val TAG = "PrintSoundManager"
        private const val PREFS_NAME = "capt_sound_prefs"
        private const val KEY_SOUNDS_ENABLED = "print_sounds_enabled"
    }
}
