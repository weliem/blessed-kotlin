package com.example.blessed3

import android.app.Application
import com.mlc.nordic_sdk.XlogUtils

class BlessedApp: Application() {
    override fun onCreate() {
        super.onCreate()
        XlogUtils.initXlog(this, true)
        BluetoothHandler.initialize(this.applicationContext)
    }
}