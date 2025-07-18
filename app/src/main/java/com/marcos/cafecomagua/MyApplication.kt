package com.marcos.cafecomagua

import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDataSource.init(this)
    }
}