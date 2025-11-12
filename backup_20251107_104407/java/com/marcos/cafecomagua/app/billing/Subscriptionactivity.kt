package com.marcos.cafecomagua.app.billing

import android.R
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.billing.SubscriptionManager
import com.marcos.cafecomagua.databinding.ActivitySubscriptionBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.marcos.cafecomagua.ui.results.ResultsActivity

/**
 * Activity refatorada para gerenciar assinaturas e doações
 * Substitui o SupportActivity com modelo de assinatura
 */
class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding
    private lateinit var subscriptionManager: SubscriptionManager
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var avaliacaoAtual: AvaliacaoResultado? = null

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val PREF_SUPPORT_VIEW_COUNT = "support_view_count"
        private const val SUPPORT_VIEW_FREQUENCY = 13
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recupera avaliação se vier de fluxo de avaliação
        @Suppress("DEPRECATION")
        avaliacaoAtual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("avaliacaoAtual", AvaliacaoResultado::class.java)
        } else {
            intent.getSerializableExtra("avaliacaoAtual") as? AvaliacaoResultado
        }

        // Verifica se deve pular esta tela
        if (shouldSkipSubscriptionScreen()) {
            navigateToResults()
            return
        }

        enableEdgeToEdge()
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
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

        setupToolbar()
        setupSubscriptionManager()
        setupUI()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Verifica se deve exibir a tela baseado na frequência
     */
    private fun shouldSkipSubscriptionScreen(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val adsRemoved = prefs.getBoolean("ads_removed", false)

        if (!adsRemoved) {
            return false
        }

        val currentCount = prefs.getInt(PREF_SUPPORT_VIEW_COUNT, 0)
        val newCount = currentCount + 1
        prefs.edit().putInt(PREF_SUPPORT_VIEW_COUNT, newCount).apply()

        // Exibe na primeira vez e depois a cada 13 vezes
        return (newCount - 1) % SUPPORT_VIEW_FREQUENCY != 0
    }

    private fun navigateToResults() {
        startActivity(Intent(this, ResultsActivity::class.java).apply {
            putExtra("avaliacaoAtual", avaliacaoAtual)
        })
        finish()
    }

    /**
     * Configura o gerenciador de assinaturas
     */
    private fun setupSubscriptionManager() {
        subscriptionManager = SubscriptionManager(this, coroutineScope)

        subscriptionManager.onPurchaseSuccess = { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            updateUI()
            // Se veio do fluxo de avaliação, continua
            if (avaliacaoAtual != null) {
                navigateToResults()
            }
        }

        subscriptionManager.onPurchaseError = { error ->
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }

        subscriptionManager.onProductsLoaded = {
            updatePrices()
        }
    }

    /**
     * Configura UI inicial
     */
    private fun setupUI() {
        updateUI()

        // Exibe badge "POPULAR" na assinatura
        binding.badgePopular.visibility = View.VISIBLE
    }

    /**
     * Atualiza UI baseado no status de assinatura
     */
    private fun updateUI() {
        val isPremium = subscriptionManager.isPremiumActive()
        val isLegacy = subscriptionManager.isLegacyUser()

        if (isPremium) {
            // Esconde assinatura, mostra apenas doações
            binding.layoutSubscriptionSection.visibility = View.GONE
            binding.textSubtitleDonation.text = getString(com.marcos.cafecomagua.R.string.thank_you_premium_user)

            if (isLegacy) {
                binding.textPremiumStatus.text = getString(com.marcos.cafecomagua.R.string.premium_status_legacy)
            } else {
                binding.textPremiumStatus.text = getString(com.marcos.cafecomagua.R.string.premium_status_active)
            }
            binding.textPremiumStatus.visibility = View.VISIBLE
        } else {
            // Mostra opções de assinatura
            binding.layoutSubscriptionSection.visibility = View.VISIBLE
            binding.textPremiumStatus.visibility = View.GONE
        }
    }

    /**
     * Atualiza preços dos produtos
     */
    private fun updatePrices() {
        binding.textSubscriptionPrice.text = subscriptionManager.getProductPrice(
            SubscriptionManager.SKU_SUBSCRIPTION_MONTHLY
        ) ?: getString(com.marcos.cafecomagua.R.string.preco_indisponivel)

        binding.textSmallCoffeePrice.text = subscriptionManager.getProductPrice(
            SubscriptionManager.SKU_SMALL_COFFEE
        ) ?: getString(com.marcos.cafecomagua.R.string.preco_indisponivel)

        binding.textMediumCoffeePrice.text = subscriptionManager.getProductPrice(
            SubscriptionManager.SKU_MEDIUM_COFFEE
        ) ?: getString(com.marcos.cafecomagua.R.string.preco_indisponivel)

        binding.textLargeCoffeePrice.text = subscriptionManager.getProductPrice(
            SubscriptionManager.SKU_LARGE_COFFEE
        ) ?: getString(com.marcos.cafecomagua.R.string.preco_indisponivel)
    }

    /**
     * Configura listeners dos botões
     */
    private fun setupListeners() {
        // Botão de assinatura mensal
        binding.cardSubscription.setOnClickListener {
            subscriptionManager.launchPurchaseFlow(
                this,
                SubscriptionManager.SKU_SUBSCRIPTION_MONTHLY
            )
        }

        // Botões de doação
        binding.cardSupportSmall.setOnClickListener {
            subscriptionManager.launchPurchaseFlow(
                this,
                SubscriptionManager.SKU_SMALL_COFFEE
            )
        }

        binding.cardSupportMedium.setOnClickListener {
            subscriptionManager.launchPurchaseFlow(
                this,
                SubscriptionManager.SKU_MEDIUM_COFFEE
            )
        }

        binding.cardSupportLarge.setOnClickListener {
            subscriptionManager.launchPurchaseFlow(
                this,
                SubscriptionManager.SKU_LARGE_COFFEE
            )
        }

        // Botão de continuar sem assinar
        binding.buttonContinueToResults.setOnClickListener {
            if (avaliacaoAtual != null) {
                navigateToResults()
            } else {
                finish()
            }
        }

        // Link para gerenciar assinatura
        binding.textManageSubscription.setOnClickListener {
            openSubscriptionManagement()
        }
    }

    /**
     * Abre página de gerenciamento de assinaturas do Google Play
     */
    private fun openSubscriptionManagement() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(
                "https://play.google.com/store/account/subscriptions"
            )
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptionManager.destroy()
    }
}