package com.example.captprint

// YENİ: Arka Plan Servisleri için Şeffaf USB İzin Onay Ekranı

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class UsbPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val transport = CaptUsbTransport(this)
        val printer = transport.findPrinter()

        if (printer != null && !transport.hasPermission(printer)) {
            transport.requestPermission(printer) { granted ->
                if (granted) {
                    Toast.makeText(this, "Yazıcı izni verildi. Yazdırma devam ediyor...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Yazıcı izni reddedildi.", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        } else {
            finish()
        }
    }
}
