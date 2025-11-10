package com.marcos.cafecomagua.ui.home

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast // 👈 ADICIONADO
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog // 👈 ADICIONADO
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.marcos.cafecomagua.ui.help.HelpActivity
import com.marcos.cafecomagua.ui.history.HistoryActivity
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.ads.InterstitialAdManager
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.ui.evaluation.EvaluationHostActivity
import com.marcos.cafecomagua.databinding.ActivityHomeBinding
import com.marcos.cafecomagua.app.analytics.AnalyticsManager.Category
import com.marcos.cafecomagua.app.analytics.AnalyticsManager.Event
import com.marcos.cafecomagua.ui.onboarding.OnboardingActivity
import androidx.lifecycle.lifecycleScope // 👈 ADICIONADO
import com.marcos.cafecomagua.app.billing.PremiumBottomSheetFragment
import com.marcos.cafecomagua.billing.SubscriptionManager
import com.marcos.cafecomagua.ui.wateroptimizer.WaterOptimizerActivity
import com.marcos.cafecomagua.app.MyApplication // 👈 ADICIONADO
import kotlinx.coroutines.launch // 👈 ADICIONADO
import kotlinx.coroutines.flow.first // 👈 ADICIONADO

/**
 * HomeActivity (ex-WelcomeActivity)
 * Tela inicial do app com navegação principal
 */
class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private val interstitialManager: InterstitialAdManager by lazy {
        InterstitialAdManager(
            context = this,
            adUnitId = "ca-app-pub-7526020095328101/9326848140"
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
    private val subscriptionManager: SubscriptionManager by lazy {
        SubscriptionManager(this, lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState) // ✅ MOVIDO para cima

        // ✅ Verificar se onboarding foi concluído
        if (!OnboardingActivity.isCompleted(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root) // ✅ CHAMADO ANTES de setupListeners()

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

        // ✅ CHAMADA ÚNICA E CORRETA
        setupListeners()
        updateThemeIcon()
        countAppOpens()
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptionManager.destroy()
        // (O try/catch para o interstitialManager está correto)
        if (::binding.isInitialized) {
            try {
                interstitialManager.destroy()
            } catch (e: Exception) {
                // Ignorar
            }
        }
    }

    /**
     * ✅ FUNÇÃO COMPLETA
     * Agora inclui todos os botões do seu layout, com a nova lógica do Otimizador.
     */
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

        // Botões de Ação
        binding.buttonNewEvaluation.setOnClickListener {
            analytics().logEvent(
                Category.EVALUATION,
                Event.EVALUATION_STARTED
            )
            // Chama o novo fluxo de Fragments
            startActivity(Intent(this, EvaluationHostActivity::class.java))
        }

        // ✅ LÓGICA DO OTIMIZADOR ATUALIZADA
        binding.buttonWaterOptimizer.setOnClickListener {
            analytics().logEvent(
                Category.PREMIUM,
                Event.PREMIUM_FEATURE_ATTEMPTED,
                mapOf("feature" to "water_optimizer")
            )
            if (subscriptionManager.isPremiumActive()) {
                // Se for premium, busca as águas salvas
                showWaterSelectionDialog()
            } else {
                // Se não for premium, mostra o paywall
                PremiumBottomSheetFragment().show(supportFragmentManager, PremiumBottomSheetFragment.TAG)
            }
        }

        binding.buttonViewHistory.setOnClickListener {
            analytics().logEvent(
                Category.NAVIGATION,
                "history_button_clicked"
            )
            // Mostra o intersticial
            interstitialManager.showIfAvailable(
                activity = this,
                counterKey = "history_views",
                frequency = InterstitialAdManager.HISTORY_VIEW_FREQUENCY
            )
        }
    }

    /**
     * ✅ NOVA FUNÇÃO
     * Busca águas no DB e exibe um diálogo de seleção.
     */
    private fun showWaterSelectionDialog() {
        // Acesso ao DAO do Room (usando o Application cast)
        val dao = (application as MyApplication).database.avaliacaoDao()

        lifecycleScope.launch {
            // Coleta apenas a lista mais recente do Flow (uso de .first() requer kotlinx.coroutines.flow.first)
            val avaliacoesSalvas = dao.getAll().first()

            if (avaliacoesSalvas.isEmpty()) {
                // Informa o usuário que ele precisa salvar uma água primeiro
                Toast.makeText(
                    this@HomeActivity,
                    "Você precisa salvar uma avaliação no Histórico primeiro.", // TODO: Use R.string
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            // Cria a lista de nomes para o diálogo (Nome da Água (Fonte))
            val nomesDasAguas = avaliacoesSalvas.map {
                "${it.nomeAgua} (${it.fonteAgua})"
            }.toTypedArray()

            // Exibe o diálogo de seleção
            AlertDialog.Builder(this@HomeActivity)
                .setTitle("Otimizar Água Salva") // TODO: Use R.string
                .setItems(nomesDasAguas) { dialog, which ->
                    // 'which' é o índice da água selecionada
                    val avaliacaoSelecionada = avaliacoesSalvas[which]

                    // Navega para o Otimizador com os dados brutos como extras
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
                .setNegativeButton("Cancelar", null) // TODO: Use R.string
                .show()
        }
    }


    private fun navigateToHistory() {
        startActivity(Intent(this, HistoryActivity::class.java))
    }

    private fun toggleTheme() {
        // (Sua função toggleTheme está correta)
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        val newMode = if (isNightMode) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        prefs.edit().putInt("key_theme", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun updateThemeIcon() {
        // (Sua função updateThemeIcon está correta)
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
        // (Sua função countAppOpens está correta)
        val prefs = getSharedPreferences("app_ratings", MODE_PRIVATE)
        prefs.edit {
            putInt("open_count", prefs.getInt("open_count", 0) + 1)
        }
    }
}