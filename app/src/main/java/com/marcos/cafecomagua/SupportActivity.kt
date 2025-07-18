package com.marcos.cafecomagua

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.android.billingclient.api.*
import com.google.android.gms.ads.AdRequest
import com.marcos.cafecomagua.databinding.ActivitySupportBinding

class SupportActivity : AppCompatActivity(), PurchasesUpdatedListener {

    private lateinit var binding: ActivitySupportBinding
    private val SKU_REMOVE_ADS = "remocao_anuncios_v1"
    private val SKU_SMALL_COFFEE = "cafe_pequeno_apoio"
    private val SKU_MEDIUM_COFFEE = "cafe_medio_apoio"
    private val SKU_LARGE_COFFEE = "cafe_grande_apoio"

    private lateinit var billingClient: BillingClient
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    private var avaliacaoAtual: AvaliacaoResultado? = null

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val PREF_ADS_REMOVED = "ads_removed"
        private const val PREF_SUPPORT_VIEW_COUNT = "support_view_count"
        private const val SUPPORT_VIEW_FREQUENCY = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        avaliacaoAtual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("avaliacaoAtual", AvaliacaoResultado::class.java)
        } else {
            intent.getSerializableExtra("avaliacaoAtual") as? AvaliacaoResultado
        }

        if (shouldSkipSupportScreen()) {
            navigateToResults()
            return
        }

        enableEdgeToEdge()
        binding = ActivitySupportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        // --- INÍCIO DA CORREÇÃO ---
        // Substituímos a lógica complexa pela abordagem simples da tela de Histórico.
        // Isso aplica o padding na raiz da view, ajustando todo o conteúdo de uma vez.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = insets.top,
                bottom = insets.bottom
            )
            windowInsets
        }
        // --- FIM DA CORREÇÃO ---

        setupBillingClient()
        setupListeners()
    }

    private fun shouldSkipSupportScreen(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val adsRemoved = prefs.getBoolean(PREF_ADS_REMOVED, false)

        if (!adsRemoved) {
            return false
        }

        val currentCount = prefs.getInt(PREF_SUPPORT_VIEW_COUNT, 0)
        val newCount = currentCount + 1
        prefs.edit { putInt(PREF_SUPPORT_VIEW_COUNT, newCount) }

        return (newCount - 1) % SUPPORT_VIEW_FREQUENCY != 0
    }

    private fun navigateToResults() {
        startActivity(Intent(this, ResultadosActivity::class.java).apply {
            putExtra("avaliacaoAtual", avaliacaoAtual)
        })
        finish()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    queryPurchases()
                } else {
                    Log.e("Billing", "Erro na configuração: ${billingResult.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w("Billing", "Serviço de faturamento desconectado.")
            }
        })
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Compra cancelada.", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("Billing", "Erro na compra: ${billingResult.debugMessage}")
            Toast.makeText(this, "Erro durante a compra.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_REMOVE_ADS).setProductType(BillingClient.ProductType.INAPP).build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_SMALL_COFFEE).setProductType(BillingClient.ProductType.INAPP).build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_MEDIUM_COFFEE).setProductType(BillingClient.ProductType.INAPP).build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_LARGE_COFFEE).setProductType(BillingClient.ProductType.INAPP).build()
        )

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                queryResult.productDetailsList?.forEach { pd ->
                    productDetailsMap[pd.productId] = pd
                }
            } else {
                Log.e("Billing", "Falha ao obter detalhes: ${billingResult.debugMessage}")
            }
            updateUiWithProductDetails()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            billingClient.acknowledgePurchase(ackParams) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    dispatchPurchase(purchase)
                }
            }
        } else {
            dispatchPurchase(purchase)
        }
    }

    private fun dispatchPurchase(purchase: Purchase) {
        purchase.products.forEach { productId ->
            when (productId) {
                SKU_REMOVE_ADS -> grantAdRemoval()
                SKU_SMALL_COFFEE, SKU_MEDIUM_COFFEE, SKU_LARGE_COFFEE -> consumeDonation(purchase)
            }
        }
    }

    private fun setupListeners() {
        binding.cardRemoveAds.setOnClickListener {
            productDetailsMap[SKU_REMOVE_ADS]?.let { launchPurchaseFlow(it) }
        }
        binding.cardSupportSmall.setOnClickListener {
            productDetailsMap[SKU_SMALL_COFFEE]?.let { launchPurchaseFlow(it) }
        }
        binding.cardSupportMedium.setOnClickListener {
            productDetailsMap[SKU_MEDIUM_COFFEE]?.let { launchPurchaseFlow(it) }
        }
        binding.cardSupportLarge.setOnClickListener {
            productDetailsMap[SKU_LARGE_COFFEE]?.let { launchPurchaseFlow(it) }
        }
        binding.buttonContinueToResults.setOnClickListener {
            navigateToResults()
        }
    }

    private fun launchPurchaseFlow(productDetails: ProductDetails) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails).build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(this, flowParams)
    }

    private fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    private fun updateUiWithProductDetails() {
        val adsRemoved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREF_ADS_REMOVED, false)

        runOnUiThread {
            if (adsRemoved) {
                binding.adViewSupportBanner.visibility = View.GONE
                binding.cardRemoveAds.visibility = View.GONE
                binding.textViewRemoveAdsSubtitle.text = getString(R.string.ads_removed_message)
                binding.textViewDonationSubtitle.text = getString(R.string.desc_continuar_apoiando)
            } else {
                binding.adViewSupportBanner.visibility = View.VISIBLE
                binding.adViewSupportBanner.loadAd(AdRequest.Builder().build())
                binding.textViewDonationSubtitle.text = getString(R.string.desc_pagar_cafezinho)
            }

            productDetailsMap[SKU_REMOVE_ADS]?.oneTimePurchaseOfferDetails?.let {
                binding.textViewRemoveAdsPrice.text = it.formattedPrice
            }
            productDetailsMap[SKU_SMALL_COFFEE]?.oneTimePurchaseOfferDetails?.let {
                binding.textViewSmallCoffeePrice.text = it.formattedPrice
            }
            productDetailsMap[SKU_MEDIUM_COFFEE]?.oneTimePurchaseOfferDetails?.let {
                binding.textViewMediumCoffeePrice.text = it.formattedPrice
            }
            productDetailsMap[SKU_LARGE_COFFEE]?.oneTimePurchaseOfferDetails?.let {
                binding.textViewLargeCoffeePrice.text = it.formattedPrice
            }
        }
    }

    private fun grantAdRemoval() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean(PREF_ADS_REMOVED, true)
            putInt(PREF_SUPPORT_VIEW_COUNT, 0)
            apply()
        }
        runOnUiThread { updateUiWithProductDetails() }
    }

    private fun consumeDonation(purchase: Purchase) {
        billingClient.consumeAsync(
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                runOnUiThread {
                    Toast.makeText(this, "Muito obrigado pelo seu apoio!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}