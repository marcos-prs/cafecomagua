package com.marcos.cafecomagua.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.marcos.cafecomagua.app.data.AppDataSource

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Lê a preferência de tema salva e a aplica em todo o app na inicialização.
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val themeMode = sharedPref.getInt("key_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
        AppDataSource.init(this)
    }
}