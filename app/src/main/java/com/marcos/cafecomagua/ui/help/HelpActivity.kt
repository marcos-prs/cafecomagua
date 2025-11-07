package com.marcos.cafecomagua.ui.help

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.databinding.ActivityHelpBinding

class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding
    private lateinit var billingClient: BillingClient
    private val skuRemoveAds = "remocao_anuncios_v1"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✨ 1. Listener de padding para a borda
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
        setupContent()
        handleIntentExtras()
        setupBillingClient()
        setupListeners()
    }

    // ✨ 3. Método padrão para a Toolbar
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupContent() {
        binding.textViewTopic1Content.text = HtmlCompat.fromHtml(getString(R.string.help_topic_1_param_qual_content), HtmlCompat.FROM_HTML_MODE_COMPACT)
        binding.textViewTopic2Content.text = HtmlCompat.fromHtml(getString(R.string.help_topic_2_tds_content), HtmlCompat.FROM_HTML_MODE_COMPACT)
        binding.textViewTopic3Content.text = HtmlCompat.fromHtml(getString(R.string.help_topic_3_scoring_system_content), HtmlCompat.FROM_HTML_MODE_COMPACT)
        binding.textViewTopicOcrContent.text = HtmlCompat.fromHtml(getString(R.string.help_topic_ocr_content), HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    private fun setupListeners() {
        binding.buttonRestorePurchase.setOnClickListener {
            if (billingClient.isReady) {
                binding.buttonRestorePurchase.isEnabled = false
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
            binding.scrollViewHelp.post {
                binding.scrollViewHelp.smoothScrollTo(0, binding.textViewTopicOcrTitle.top)
            }
        }
    }

    private fun setupBillingClient() {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(this)
            .setListener { _, _ -> /* Não necessário para restauração */ }
            .enablePendingPurchases(pendingPurchasesParams)
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
                binding.buttonRestorePurchase.isEnabled = true
            }
        }
    }

    private fun grantAdRemoval() {
        getSharedPreferences("app_settings", MODE_PRIVATE).edit {
            putBoolean("ads_removed", true)
        }
    }

    // ✨ 3. Lógica do botão "voltar"
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
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