package com.marcos.cafecomagua.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.marcos.cafecomagua.app.data.AppDatabase

class MyApplication : Application() {

    // A instância do DB será criada "lazy" (na primeira vez que for acessada)
    // e compartilhada por todo o app.
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()

        // Lê a preferência de tema salva
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val themeMode = sharedPref.getInt("key_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)

        // ✅ REMOVIDO: A linha AppDataSource.init(this) foi deletada.
        // O Room agora gerencia sua própria inicialização.
    }
}