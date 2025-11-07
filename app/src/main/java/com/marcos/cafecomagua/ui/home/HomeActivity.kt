package com.marcos.cafecomagua.ui.home

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.marcos.cafecomagua.ui.help.HelpActivity
import com.marcos.cafecomagua.ui.history.HistoryActivity
import com.marcos.cafecomagua.ui.onboarding.OnboardingActivity
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.ads.InterstitialAdManager
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.databinding.ActivityHomeBinding
import com.marcos.cafecomagua.app.analytics.AnalyticsManager.Category
import com.marcos.cafecomagua.app.analytics.AnalyticsManager.Event
import com.marcos.cafecomagua.ui.waterinput.WaterInputActivity

/**
 * HomeActivity (ex-WelcomeActivity)
 * Tela inicial do app com navegação principal
 */
class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    // ✅ CORRIGIDO: Usar lazy delegate para inicialização tardia segura
    private val interstitialManager: InterstitialAdManager by lazy {
        InterstitialAdManager(
            context = this,
            adUnitId = "ca-app-pub-7526020095328101/9326848140"
        ).apply {
            onAdDismissed = {
                navigateToHistory()
            }
            onAdFailedToShow = {
                navigateToHistory()
            }
            onAdShown = {
                analytics().logEvent(
                    Category.USER_ACTION,
                    Event.AD_SHOWN,
                    mapOf("ad_type" to "interstitial", "location" to "home_to_history")
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // ✅ Verificar se onboarding foi concluído
        if (!OnboardingActivity.isCompleted(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                right = systemBars.right,
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }

        supportActionBar?.hide()

        // Analytics - registrar abertura do app
        analytics().logSession()
        analytics().logEvent(
            Category.NAVIGATION,
            Event.SCREEN_VIEWED,
            mapOf("screen_name" to "home")
        )

        setupListeners()
        updateThemeIcon()
        countAppOpens()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ CORRIGIDO: Verificar se foi inicializado antes de destruir
        if (::binding.isInitialized) {
            // O interstitialManager usa lazy, então só será destruído se foi acessado
            // Podemos verificar se foi inicializado indiretamente checando se já foi usado
            try {
                interstitialManager.destroy()
            } catch (e: Exception) {
                // Ignorar se não foi inicializado
            }
        }
    }

    private fun setupListeners() {
        binding.buttonHelp.setOnClickListener {
            analytics().logEvent(
                Category.NAVIGATION,
                "help_opened"
            )
            startActivity(Intent(this, HelpActivity::class.java))
        }

        // Navega direto sem intersticial
        binding.buttonNewEvaluation.setOnClickListener {
            analytics().logEvent(
                Category.EVALUATION,
                Event.EVALUATION_STARTED
            )
            startActivity(Intent(this, WaterInputActivity::class.java))
        }

        // Usa intersticial inteligente (a cada 3 visualizações)
        binding.buttonViewHistory.setOnClickListener {
            analytics().logEvent(
                Category.NAVIGATION,
                "history_button_clicked"
            )

            // Mostrar intersticial a cada 3 visualizações
            interstitialManager.showIfAvailable(
                activity = this,
                counterKey = "history_views",
                frequency = InterstitialAdManager.HISTORY_VIEW_FREQUENCY
            )
        }

        binding.buttonToggleTheme.setOnClickListener {
            toggleTheme()
        }
    }

    /**
     * Navega para a tela de histórico
     */
    private fun navigateToHistory() {
        startActivity(Intent(this, HistoryActivity::class.java))
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        val newMode = if (isNightMode) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        prefs.edit().putInt("key_theme", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun updateThemeIcon() {
        val isNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        if (isNightMode) {
            binding.buttonToggleTheme.setImageResource(R.drawable.ic_sun_day)
        } else {
            binding.buttonToggleTheme.setImageResource(R.drawable.ic_moon_night)
        }
    }

    private fun countAppOpens() {
        val prefs = getSharedPreferences("app_ratings", MODE_PRIVATE)
        prefs.edit {
            putInt("open_count", prefs.getInt("open_count", 0) + 1)
        }
    }
}