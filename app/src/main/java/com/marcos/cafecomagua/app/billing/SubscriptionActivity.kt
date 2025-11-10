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
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.billing.SubscriptionManager
import com.marcos.cafecomagua.databinding.ActivitySubscriptionBinding
import com.marcos.cafecomagua.databinding.ItemNativeAdBinding
import androidx.lifecycle.lifecycleScope

/**
 * Activity refatorada para gerenciar assinaturas
 * Foco em conversão para assinatura premium
 */
class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding
    private lateinit var subscriptionManager: SubscriptionManager
    private var avaliacaoAtual: AvaliacaoResultado? = null
    private var nativeAd: NativeAd? = null

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val PREF_SUPPORT_VIEW_COUNT = "support_view_count"
        private const val SUPPORT_VIEW_FREQUENCY = 13
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110" // Test ID - substitua pelo seu
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ❌ REMOVIDO: Bloco de recuperação do 'avaliacaoAtual'
        // ❌ REMOVIDO: Chamada 'shouldSkipSubscriptionScreen()'

        enableEdgeToEdge()
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            // (Lógica do EdgeToEdge)
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
        loadNativeAd()
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

    /**
     * Configura o gerenciador de assinaturas
     */
    private fun setupSubscriptionManager() {
        subscriptionManager = SubscriptionManager(this, lifecycleScope)
        subscriptionManager.onPurchaseSuccess = { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            updateUI()
            // ❌ REMOVIDA: Lógica 'if (avaliacaoAtual != null)'
        }

        // ✅ LINHA CORRIGIDA (PREENCHIDA)
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
        binding.badgePopular.visibility = View.VISIBLE
    }

    /**
     * Atualiza UI baseado no status de assinatura
     */
    private fun updateUI() {
        val isPremium = subscriptionManager.isPremiumActive()
        val isLegacy = subscriptionManager.isLegacyUser()

        if (isPremium) {
            // Esconde seção de assinatura, mostra status premium
            binding.layoutSubscriptionSection.visibility = View.GONE
            binding.cardPremiumStatus.visibility = View.VISIBLE
            binding.adContainer.visibility = View.GONE

            if (isLegacy) {
                binding.textPremiumStatus.text = getString(com.marcos.cafecomagua.R.string.premium_status_legacy)
            } else {
                binding.textPremiumStatus.text = getString(com.marcos.cafecomagua.R.string.premium_status_active)
            }
        } else {
            // Mostra opções de assinatura
            binding.layoutSubscriptionSection.visibility = View.VISIBLE
            binding.cardPremiumStatus.visibility = View.GONE
        }
    }

    /**
     * Atualiza preços dos produtos
     */
    private fun updatePrices() {
        binding.textSubscriptionPrice.text = subscriptionManager.getProductPrice(
            SubscriptionManager.SKU_SUBSCRIPTION_MONTHLY
        ) ?: getString(com.marcos.cafecomagua.R.string.preco_indisponivel)
    }

    /**
     * Configura listeners dos botões
     */
    private fun setupListeners() {
        // Card de assinatura (clique em qualquer lugar)
        binding.cardSubscription.setOnClickListener {
            launchSubscription()
        }

        // Botão de assinar dentro do card
        binding.buttonSubscribe.setOnClickListener {
            launchSubscription()
        }

        binding.buttonContinueToResults.setOnClickListener {
            finish()
        }

        binding.textManageSubscription.setOnClickListener { openSubscriptionManagement() }
    }

    /**
     * Inicia o fluxo de assinatura
     */
    private fun launchSubscription() {
        subscriptionManager.launchPurchaseFlow(
            this,
            SubscriptionManager.SKU_SUBSCRIPTION_MONTHLY
        )
    }

    /**
     * Carrega anúncio nativo
     */
    private fun loadNativeAd() {
        // Não carrega anúncio se for premium
        if (subscriptionManager.isPremiumActive()) {
            binding.adContainer.visibility = View.GONE
            return
        }

        val adLoader = AdLoader.Builder(this, AD_UNIT_ID)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
                populateNativeAd(ad)
            }
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Popula o layout do anúncio nativo
     */
    private fun populateNativeAd(ad: NativeAd) {
        val adBinding = ItemNativeAdBinding.inflate(layoutInflater)
        val nativeAdView = adBinding.root as NativeAdView

        // Preenche os componentes do anúncio
        adBinding.adHeadline.text = ad.headline
        nativeAdView.headlineView = adBinding.adHeadline

        ad.body?.let {
            adBinding.adBody.text = it
            adBinding.adBody.visibility = View.VISIBLE
            nativeAdView.bodyView = adBinding.adBody
        }

        ad.icon?.let {
            adBinding.adIcon.setImageDrawable(it.drawable)
            adBinding.adIcon.visibility = View.VISIBLE
            nativeAdView.iconView = adBinding.adIcon
        }

        ad.starRating?.let {
            adBinding.adStars.rating = it.toFloat()
            adBinding.adStars.visibility = View.VISIBLE
            nativeAdView.starRatingView = adBinding.adStars
        }

        ad.callToAction?.let {
            adBinding.adCallToAction.text = it
            nativeAdView.callToActionView = adBinding.adCallToAction
        }

        nativeAdView.setNativeAd(ad)
        binding.adContainer.removeAllViews()
        binding.adContainer.addView(nativeAdView)
        binding.adContainer.visibility = View.VISIBLE
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
        nativeAd?.destroy()
        subscriptionManager.destroy()
    }
}