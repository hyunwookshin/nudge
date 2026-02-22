package com.hyunwookshin.nudge

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    companion object { lateinit var instance: App }
}