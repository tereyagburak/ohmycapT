package com.example.captprint

import android.app.Application
import com.example.captprint.data.repository.FakePrinterRepository
import com.example.captprint.data.repository.PrinterRepository
import com.example.captprint.data.repository.RealPrinterRepository

class CaptApplication : Application() {

    lateinit var printerRepository: PrinterRepository
        private set

    lateinit var realRepository: RealPrinterRepository
        private set

    lateinit var fakeRepository: FakePrinterRepository
        private set

    var isUsingFakeRepository: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        realRepository = RealPrinterRepository(this)
        fakeRepository = FakePrinterRepository()
        // Varsayılan olarak Real repository kullanılır
        printerRepository = realRepository
    }

    fun switchToFake(useFake: Boolean) {
        isUsingFakeRepository = useFake
        printerRepository = if (useFake) fakeRepository else realRepository
    }

    companion object {
        lateinit var instance: CaptApplication
            private set
    }
}
