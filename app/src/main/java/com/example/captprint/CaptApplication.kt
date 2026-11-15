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
            soundManager = soundManager,
            isRepositoryActive = { !isUsingFakeRepository }
        )
        fakeRepository = FakePrinterRepository(soundManager = soundManager)
        _activeRepository.value = realRepository
    }

    fun switchToFake(useFake: Boolean) {
        isUsingFakeRepository = useFake
        _isUsingFake.value = useFake
        _activeRepository.value = if (useFake) fakeRepository else realRepository
    }

    fun setDevModeUnlocked(unlocked: Boolean) {
        getSharedPreferences("capt_dev_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("dev_unlocked", unlocked)
            .apply()
        _isDevModeUnlocked.value = unlocked
    }

    companion object {
        lateinit var instance: CaptApplication
            private set
    }
}
