package com.marcos.cafecomagua.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gerenciador centralizado de assinaturas e compras in-app
 * Implementa tanto o modelo legado (IAP único) quanto o novo (assinatura)
 */
class SubscriptionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : PurchasesUpdatedListener {

    companion object {
        // SKUs de produtos
        const val SKU_SUBSCRIPTION_MONTHLY = "premium_monthly_subscription"
        const val SKU_REMOVE_ADS_LEGACY = "remocao_anuncios_v1" // Mantido para usuários legados
        const val SKU_SMALL_COFFEE = "cafe_pequeno_apoio"
        const val SKU_MEDIUM_COFFEE = "cafe_medio_apoio"
        const val SKU_LARGE_COFFEE = "cafe_grande_apoio"

        // SharedPreferences
        private const val PREFS_NAME = "app_settings"
        private const val PREF_ADS_REMOVED = "ads_removed"
        private const val PREF_SUBSCRIPTION_ACTIVE = "subscription_active"
        private const val PREF_IS_LEGACY_USER = "is_legacy_user"

        private const val TAG = "SubscriptionManager"
    }

    private var billingClient: BillingClient? = null
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    var onPurchaseSuccess: ((String) -> Unit)? = null
    var onPurchaseError: ((String) -> Unit)? = null
    var onProductsLoaded: (() -> Unit)? = null

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing setup successful")
                    queryAllProducts()
                    queryExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                // Tentar reconectar
                setupBillingClient()
            }
        })
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                onPurchaseError?.invoke("Compra cancelada pelo usuário")
            }
            else -> {
                onPurchaseError?.invoke("Erro na compra: ${billingResult.debugMessage}")
            }
        }
    }

    private fun queryAllProducts() {
        // Query de produtos one-time (IAP)
        queryInAppProducts()

        // Query de assinaturas
        querySubscriptions()
    }

    private fun queryInAppProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_REMOVE_ADS_LEGACY)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_SMALL_COFFEE)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_MEDIUM_COFFEE)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_LARGE_COFFEE)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // NOVO: usar result.productDetailsList
                result.productDetailsList.forEach { pd ->
                    productDetailsMap[pd.productId] = pd
                    Log.d(TAG, "Product loaded: ${pd.productId}")
                }
                // (Opcional) tratar itens não buscados
                // result.unfetchedProductList.forEach { Log.w(TAG, "Unfetched: ${it.productId} status=${it.statusCode}") }
                onProductsLoaded?.invoke()
            } else {
                Log.e(TAG, "Failed to query products: ${billingResult.debugMessage}")
            }
        }
    }


    private fun querySubscriptions() {
        val subscriptionList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_SUBSCRIPTION_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(subscriptionList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.productDetailsList.forEach { pd ->
                    productDetailsMap[pd.productId] = pd
                    Log.d(TAG, "Subscription loaded: ${pd.productId}")
                }
                onProductsLoaded?.invoke()
            } else {
                Log.e(TAG, "Failed to query subscriptions: ${billingResult.debugMessage}")
            }
        }
    }


    private fun queryExistingPurchases() {
        // Query compras one-time
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }

        // Query assinaturas
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        coroutineScope.launch {
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            } else {
                processPurchase(purchase)
            }
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val ackParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        withContext(Dispatchers.IO) {
            billingClient?.acknowledgePurchase(ackParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    processPurchase(purchase)
                }
            }
        }
    }

    private fun processPurchase(purchase: Purchase) {
        purchase.products.forEach { productId ->
            when (productId) {
                SKU_REMOVE_ADS_LEGACY -> grantLegacyAdRemoval()
                SKU_SUBSCRIPTION_MONTHLY -> grantSubscription()
                SKU_SMALL_COFFEE, SKU_MEDIUM_COFFEE, SKU_LARGE_COFFEE -> {
                    consumeDonation(purchase)
                }
            }
        }
    }

    private fun grantLegacyAdRemoval() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(PREF_ADS_REMOVED, true)
            putBoolean(PREF_IS_LEGACY_USER, true)
            apply()
        }
        onPurchaseSuccess?.invoke("Anúncios removidos permanentemente!")
        Log.d(TAG, "Legacy ad removal granted")
    }

    private fun grantSubscription() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(PREF_ADS_REMOVED, true)
            putBoolean(PREF_SUBSCRIPTION_ACTIVE, true)
            apply()
        }
        onPurchaseSuccess?.invoke("Assinatura Premium ativada!")
        Log.d(TAG, "Subscription granted")
    }

    private fun consumeDonation(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                onPurchaseSuccess?.invoke("Muito obrigado pelo seu apoio! ☕")
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val productDetails = productDetailsMap[productId]

        if (productDetails == null) {
            onPurchaseError?.invoke("Produto não disponível")
            return
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        // Se for assinatura, adicionar offer token
        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            productDetails.subscriptionOfferDetails?.firstOrNull()?.let { offer ->
                productParamsBuilder.setOfferToken(offer.offerToken)
            }
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        billingClient?.launchBillingFlow(activity, flowParams)
    }

    fun getProductPrice(productId: String): String? {
        val productDetails = productDetailsMap[productId] ?: return null

        return when (productDetails.productType) {
            BillingClient.ProductType.INAPP -> {
                productDetails.oneTimePurchaseOfferDetails?.formattedPrice
            }
            BillingClient.ProductType.SUBS -> {
                productDetails.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    ?.formattedPrice
            }
            else -> null
        }
    }

    fun isPremiumActive(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_ADS_REMOVED, false)
    }

    fun isLegacyUser(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_IS_LEGACY_USER, false)
    }

    fun isSubscriptionActive(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_SUBSCRIPTION_ACTIVE, false)
    }

    fun destroy() {
        billingClient?.endConnection()
    }
}