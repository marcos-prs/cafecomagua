package com.marcos.cafecomagua

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Lê a preferência de tema salva e a aplica em todo o app na inicialização.
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val themeMode = sharedPref.getInt("key_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        AppDataSource.init(this)
    }
}