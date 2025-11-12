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
import com.marcos.cafecomagua.app.analytics.AnalyticsManager
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.databinding.ActivityHomeBinding
import com.marcos.cafecomagua.ui.waterinput.ActivityWaterInputBinding
import com.marcos.cafecomagua.app.analytics.AnalyticsManager.Category
import com.marcos.cafecomagua.app.analytics.AnalyticsManager.Event

/**
 * HomeActivity (ex-WelcomeActivity)
 * Tela inicial do app com navegação principal
 *
 * MUDANÇAS DA REFATORAÇÃO:
 * ✅ Removido banner (conforme estratégia de monetização)
 * ✅ Removido intersticial para "Nova Avaliação"
 * ✅ Adicionado intersticial inteligente para "Ver Histórico" (a cada 3 visualizações)
 * ✅ Integrado sistema de analytics
 * ✅ Integrado verificação de onboarding
 */
class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var interstitialManager: InterstitialAdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // ✅ NOVO: Verificar se onboarding foi concluído
        if (!OnboardingActivity.Companion.isCompleted(this)) {
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

        // ✅ NOVO: Analytics - registrar abertura do app
        analytics().logSession()
        analytics().logEvent(
            Category.NAVIGATION,
            Event.SCREEN_VIEWED,
            mapOf("screen_name" to "home")
        )

        setupListeners()
        updateThemeIcon()
        countAppOpens()

        // ✅ NOVO: Inicializar gerenciador de intersticiais
        setupInterstitialManager()

        // ✅ REMOVIDO: Banner não é mais exibido nesta tela
        // Banner foi removido conforme estratégia de monetização
    }

    override fun onResume() {
        super.onResume()
        // Banner removido - não há mais código de anúncio aqui
    }

    override fun onPause() {
        super.onPause()
        // Banner removido - não há mais código de anúncio aqui
    }

    override fun onDestroy() {
        super.onDestroy()
        interstitialManager.destroy()
    }

    /**
     * ✅ NOVO: Configura gerenciador de intersticiais
     */
    private fun setupInterstitialManager() {
        interstitialManager = InterstitialAdManager(
            context = this,
            adUnitId = "ca-app-pub-7526020095328101/9326848140"
        )

        interstitialManager.onAdDismissed = {
            // Navega para o histórico após anúncio
            navigateToHistory()
        }

        interstitialManager.onAdFailedToShow = {
            // Se anúncio falhar, navega mesmo assim
            navigateToHistory()
        }

        interstitialManager.onAdShown = {
            // ✅ NOVO: Registrar exibição de anúncio no analytics
            analytics().logEvent(
                Category.USER_ACTION,
                Event.AD_SHOWN,
                mapOf("ad_type" to "interstitial", "location" to "home_to_history")
            )
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

        // ✅ MODIFICADO: Navega direto sem intersticial
        binding.buttonNewEvaluation.setOnClickListener {
            analytics().logEvent(
                Category.EVALUATION,
                Event.EVALUATION_STARTED
            )
            startActivity(Intent(this, ActivityWaterInputBinding::class.java))
        }

        // ✅ MODIFICADO: Usa intersticial inteligente (a cada 3 visualizações)
        binding.buttonViewHistory.setOnClickListener {
            analytics().logEvent(
                Category.NAVIGATION,
                "history_button_clicked"
            )

            // Mostrar intersticial a cada 3 visualizações
            interstitialManager.showIfAvailable(
                activity = this,
                counterKey = "history_views",
                frequency = InterstitialAdManager.Companion.HISTORY_VIEW_FREQUENCY
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