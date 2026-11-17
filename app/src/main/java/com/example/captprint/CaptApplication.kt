package com.example.captprint

import android.app.Application
import android.content.Context
import com.example.captprint.audio.PrintSoundManager
import com.example.captprint.data.repository.FakePrinterRepository
import com.example.captprint.data.repository.PrinterRepository
import com.example.captprint.data.repository.RealPrinterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CaptApplication : Application() {

    lateinit var realRepository: RealPrinterRepository
        private set

    lateinit var fakeRepository: FakePrinterRepository
        private set

    lateinit var soundManager: PrintSoundManager
        private set

    private val _activeRepository = MutableStateFlow<PrinterRepository?>(null)
    val activeRepository: StateFlow<PrinterRepository?> = _activeRepository.asStateFlow()

    val printerRepository: PrinterRepository
        get() = _activeRepository.value ?: realRepository

    private val _isUsingFake = MutableStateFlow(false)
    val isUsingFake: StateFlow<Boolean> = _isUsingFake.asStateFlow()

    var isUsingFakeRepository: Boolean = false
        private set

    private val _isDevModeUnlocked = MutableStateFlow(false)
    val isDevModeUnlocked: StateFlow<Boolean> = _isDevModeUnlocked.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        val devPrefs = getSharedPreferences("capt_dev_prefs", Context.MODE_PRIVATE)
        _isDevModeUnlocked.value = devPrefs.getBoolean("dev_unlocked", false)

        soundManager = PrintSoundManager(this)
        realRepository = RealPrinterRepository(
            context = this,
            soundManager = soundManager
        )
        fakeRepository = FakePrinterRepository(soundManager = soundManager)
        _activeRepository.value = realRepository
    }

    fun switchToFake(useFake: Boolean) {
        isUsingFakeRepository = useFake
        _isUsingFake.value = useFake
        realRepository.transport.isVirtualDeviceEnabled = useFake
        realRepository.refreshStatus()
        _activeRepository.value = realRepository
    }

    fun setDevModeUnlocked(unlocked: Boolean) {
        getSharedPreferences("capt_dev_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("dev_unlocked", unlocked)
            .apply()
        _isDevModeUnlocked.value = unlocked
    }

    private val _currentLanguage = MutableStateFlow("tr")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun setAppLanguage(langCode: String) {
        val appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(langCode)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
        _currentLanguage.value = langCode
        getSharedPreferences("capt_dev_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("app_language", langCode)
            .apply()
    }

    fun getAppLanguage(): String {
        val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            val tag = locales.get(0)?.language
            if (!tag.isNullOrEmpty()) return tag
        }
        val saved = getSharedPreferences("capt_dev_prefs", Context.MODE_PRIVATE)
            .getString("app_language", null)
        if (!saved.isNullOrEmpty()) return saved
        return java.util.Locale.getDefault().language.let { if (it.startsWith("tr")) "tr" else "en" }
    }

    companion object {
        lateinit var instance: CaptApplication
            private set
    }
}
