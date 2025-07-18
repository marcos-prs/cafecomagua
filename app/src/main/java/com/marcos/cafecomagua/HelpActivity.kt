package com.marcos.cafecomagua

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.text.HtmlCompat
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams // NOVO IMPORT
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams


class HelpActivity : AppCompatActivity() {

    private lateinit var billingClient: BillingClient
    private lateinit var buttonRestore: Button
    private val skuRemoveAds = "remocao_anuncios_v1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.help_screen_title)

        // Inicializa as views
        val textViewTopic1Content: TextView = findViewById(R.id.textViewTopic1Content)
        val textViewTopic2Content: TextView = findViewById(R.id.textViewTopic2Content)
        val textViewTopic3Content: TextView = findViewById(R.id.textViewTopic3Content)
        val textViewTopicOcrContent: TextView = findViewById(R.id.textViewTopicOcrContent)
        buttonRestore = findViewById(R.id.buttonRestorePurchase)

        // Define o conteúdo HTML
        textViewTopic1Content.text = HtmlCompat.fromHtml(getString(R.string.help_topic_1_param_qual_content), HtmlCompat.FROM_HTML_MODE_COMPACT)
        textViewTopic2Content.text = HtmlCompat.fromHtml(getString(R.string.help_topic_2_tds_content), HtmlCompat.FROM_HTML_MODE_COMPACT)
        textViewTopic3Content.text = HtmlCompat.fromHtml(getString(R.string.help_topic_3_scoring_system_content), HtmlCompat.FROM_HTML_MODE_COMPACT)
        textViewTopicOcrContent.text = HtmlCompat.fromHtml(getString(R.string.help_topic_ocr_content), HtmlCompat.FROM_HTML_MODE_COMPACT)

        handleIntentExtras()
        setupBillingClient()

        buttonRestore.setOnClickListener {
            if (billingClient.isReady) {
                buttonRestore.isEnabled = false
                Toast.makeText(this, getString(R.string.toast_verificando_compras), Toast.LENGTH_SHORT).show()
                queryPurchases()
            } else {
                Toast.makeText(this, getString(R.string.toast_erro_conexao_loja), Toast.LENGTH_SHORT).show()
                billingClient.startConnection(billingClientStateListener)
            }
        }
    }

    private fun handleIntentExtras() {
        val scrollToSection = intent.getStringExtra("SCROLL_TO_SECTION")
        if (scrollToSection == "OCR_HELP") {
            val scrollView: ScrollView = findViewById(R.id.scrollViewHelp)
            val ocrTitleView: TextView = findViewById(R.id.textViewTopicOcrTitle)
            scrollView.post {
                scrollView.smoothScrollTo(0, ocrTitleView.top)
            }
        }
    }

    // FUNÇÃO CORRIGIDA
    private fun setupBillingClient() {
        // Passo 1: Crie o objeto de parâmetros
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        // Passo 2: Construa o cliente de faturamento, passando os parâmetros
        billingClient = BillingClient.newBuilder(this)
            .setListener { _, _ -> /* Não necessário para restauração */ }
            .enablePendingPurchases(pendingPurchasesParams) // <-- Passe os parâmetros aqui
            .build()

        billingClient.startConnection(billingClientStateListener)
    }

    private val billingClientStateListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(billingResult: BillingResult) {
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                runOnUiThread {
                    Toast.makeText(this@HelpActivity, getString(R.string.toast_erro_conexao_loja), Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onBillingServiceDisconnected() {
            // A lógica para reconectar pode ser adicionada aqui se desejado
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            runOnUiThread {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val adRemovalPurchased = purchases.any { purchase ->
                        purchase.products.contains(skuRemoveAds) && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    }

                    if (adRemovalPurchased) {
                        grantAdRemoval()
                        Toast.makeText(this, getString(R.string.toast_compra_restaurada), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, getString(R.string.toast_nenhuma_compra_encontrada), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.toast_erro_buscar_compras), Toast.LENGTH_SHORT).show()
                }
                buttonRestore.isEnabled = true
            }
        }
    }

    private fun grantAdRemoval() {
        getSharedPreferences("app_settings",MODE_PRIVATE).edit {
            putBoolean("ads_removed", true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}