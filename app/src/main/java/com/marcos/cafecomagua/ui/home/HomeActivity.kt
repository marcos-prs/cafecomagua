package com.marcos.cafecomagua.ui.home

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
import com.marcos.cafecomagua.ui.help.HelpActivity
import com.marcos.cafecomagua.ui.history.HistoryActivity
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.ads.InterstitialAdManager
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.ui.evaluation.EvaluationHostActivity
import com.marcos.cafecomagua.databinding.ActivityHomeBinding
import com.marcos.cafecomagua.app.analytics.Category
import com.marcos.cafecomagua.app.analytics.Event
import com.marcos.cafecomagua.ui.onboarding.OnboardingActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.marcos.cafecomagua.app.billing.PremiumBottomSheetFragment
import com.marcos.cafecomagua.billing.SubscriptionManager
import com.marcos.cafecomagua.ui.wateroptimizer.WaterOptimizerActivity
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.ui.wateroptimizer.SavedRecipesActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding

    private var interstitialManager: InterstitialAdManager? = null

    private val subscriptionManager: SubscriptionManager by lazy {
        SubscriptionManager(this, lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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

        // Analytics
        analytics().logSession()
        analytics().logEvent(
            Category.NAVIGATION,
            Event.SCREEN_VIEWED,
            mapOf("screen_name" to "home")
        )

        // Configurar premium UI e interstitial
        setupPremiumFeatures()
        setupListeners()
        updateThemeIcon()
        countAppOpens()
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptionManager.destroy()
        interstitialManager?.destroy()
    }

    private fun setupPremiumFeatures() {
        val isPremium = subscriptionManager.isPremiumActive()

        // Controla visibilidade dos recursos premium
        binding.buttonSavedRecipes.isVisible = isPremium
        binding.imagePremiumBadge.isVisible = isPremium

        // Inicializa anúncios apenas para usuários não-premium
        if (!isPremium) {
            interstitialManager = InterstitialAdManager(
                context = this,
                adUnitId = "ca-app-pub-7526020095328101/8118075461"
            ).apply {
                onAdDismissed = { navigateToHistory() }
                onAdFailedToShow = { navigateToHistory() }
                onAdShown = {
                    analytics().logEvent(
                        Category.USER_ACTION,
                        Event.AD_SHOWN,
                        mapOf("ad_type" to "interstitial", "location" to "home_to_history")
                    )
                }
            }
        }
    }

    private fun setupListeners() {
        // Botões do topo
        binding.buttonHelp.setOnClickListener {
            analytics().logEvent(
                Category.NAVIGATION,
                "help_opened"
            )
            startActivity(Intent(this, HelpActivity::class.java))
        }

        binding.buttonToggleTheme.setOnClickListener {
            toggleTheme()
        }
        binding.buttonSettings.setOnClickListener {
            // Você precisará criar esta Activity (passo 5)
            startActivity(Intent(this, com.marcos.cafecomagua.ui.settings.SettingsActivity::class.java))
        }

        // Botões de Ação
        binding.buttonNewEvaluation.setOnClickListener {
            analytics().logEvent(
                Category.EVALUATION,
                Event.EVALUATION_STARTED
            )
            startActivity(Intent(this, EvaluationHostActivity::class.java))
        }

        // Otimizador de Água
        binding.buttonWaterOptimizer.setOnClickListener {
            analytics().logEvent(
                Category.PREMIUM,
                Event.PREMIUM_FEATURE_ATTEMPTED,
                mapOf("feature" to "water_optimizer")
            )
            if (subscriptionManager.isPremiumActive()) {
                showWaterSelectionDialog()
            } else {
                PremiumBottomSheetFragment().show(
                    supportFragmentManager,
                    PremiumBottomSheetFragment.TAG
                )
            }
        }

        // Minhas Receitas (Premium)
        binding.buttonSavedRecipes.setOnClickListener {
            analytics().logEvent(
                Category.PREMIUM,
                "recipes_opened"
            )
            if (subscriptionManager.isPremiumActive()) {
                startActivity(Intent(this, SavedRecipesActivity::class.java))
            } else {
                // Fallback de segurança (não deveria acontecer pois botão está invisível)
                PremiumBottomSheetFragment().show(
                    supportFragmentManager,
                    PremiumBottomSheetFragment.TAG
                )
            }
        }

        // Histórico (com anúncio intersticial)
        binding.buttonViewHistory.setOnClickListener {
            analytics().logEvent(
                Category.NAVIGATION,
                "history_opened"
            )

            val isPremium = subscriptionManager.isPremiumActive()

            // Premium vai direto sem anúncio
            if (isPremium) {
                navigateToHistory()
                return@setOnClickListener
            }

            // Não-premium tenta mostrar anúncio
            val adWasShown = interstitialManager?.showIfAvailable(
                activity = this,
                counterKey = "history_views",
                frequency = InterstitialAdManager.HISTORY_VIEW_FREQUENCY
            ) ?: false

            // Se não mostrou anúncio, navega imediatamente
            if (!adWasShown) {
                navigateToHistory()
            }
        }
    }

    private fun showWaterSelectionDialog() {
        val dao = (application as MyApplication).database.avaliacaoDao()

        lifecycleScope.launch {
            val avaliacoesSalvas = dao.getAll().first()

            // ✅ REFATORADO: Substituído Toast por MaterialAlertDialog
            if (avaliacoesSalvas.isEmpty()) {
                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("Nenhuma Água Salva")
                    .setMessage("Você precisa primeiro avaliar e salvar uma água para poder otimizá-la.")
                    .setPositiveButton("Avaliar Água") { _, _ ->
                        startActivity(Intent(this@HomeActivity, EvaluationHostActivity::class.java))
                    }
                    .setNegativeButton(R.string.button_cancelar, null)
                    .show()
                return@launch
            }

            val nomesDasAguas = avaliacoesSalvas.map {
                "${it.nomeAgua} (${it.fonteAgua})"
            }.toTypedArray()

            AlertDialog.Builder(this@HomeActivity)
                .setTitle(R.string.title_optimize_saved_water)
                .setItems(nomesDasAguas) { _, which ->
                    val avaliacaoSelecionada = avaliacoesSalvas[which]

                    val intent = Intent(this@HomeActivity, WaterOptimizerActivity::class.java).apply {
                        putExtra("calcio", avaliacaoSelecionada.calcio)
                        putExtra("magnesio", avaliacaoSelecionada.magnesio)
                        putExtra("bicarbonato", avaliacaoSelecionada.bicarbonato)
                        putExtra("sodio", avaliacaoSelecionada.sodio)
                        putExtra("ph", avaliacaoSelecionada.ph)
                        putExtra("residuo", avaliacaoSelecionada.residuoEvaporacao)
                    }
                    startActivity(intent)
                }
                .setNegativeButton(R.string.button_cancelar, null)
                .show()
        }
    }

    private fun navigateToHistory() {
        startActivity(Intent(this, HistoryActivity::class.java))
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        val newMode = if (isNightMode) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }

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