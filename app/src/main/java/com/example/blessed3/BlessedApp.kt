package com.example.blessed3

import android.app.Application

class BlessedApp: Application() {
    override fun onCreate() {
        super.onCreate()
        BluetoothHandler.initialize(this.applicationContext)
    }
}