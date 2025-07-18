package com.marcos.cafecomagua

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.text.HtmlCompat
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams

class HelpActivity : AppCompatActivity() {

    private lateinit var billingClient: BillingClient
    private lateinit var buttonRestore: Button

    // Convenção de nomenclatura do Kotlin para propriedades privadas é lowerCamelCase.
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
        buttonRestore = findViewById(R.id.buttonRestorePurchase)

        // Define o conteúdo HTML
        textViewTopic1Content.text = HtmlCompat.fromHtml(getString(R.string.help_topic_1_param_qual_content), HtmlCompat.FROM_HTML_MODE_COMPACT)
        textViewTopic2Content.text = HtmlCompat.fromHtml(getString(R.string.help_topic_2_tds_content), HtmlCompat.FROM_HTML_MODE_COMPACT)
        textViewTopic3Content.text = HtmlCompat.fromHtml(getString(R.string.help_topic_3_scoring_system_content), HtmlCompat.FROM_HTML_MODE_COMPACT)

        // Configura o BillingClient uma vez
        setupBillingClient()

        buttonRestore.setOnClickListener {
            // Apenas inicia a verificação se o cliente estiver pronto
            if (billingClient.isReady) {
                buttonRestore.isEnabled = false
                Toast.makeText(this, getString(R.string.toast_verificando_compras), Toast.LENGTH_SHORT).show()
                queryPurchases()
            } else {
                // Tenta reconectar se não estiver pronto
                Toast.makeText(this, getString(R.string.toast_erro_conexao_loja), Toast.LENGTH_SHORT).show()
                billingClient.startConnection(billingClientStateListener)
            }
        }
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            // A chamada enablePendingPurchases() foi removida pois se tornou obsoleta
            // a partir da Billing Library v4. A funcionalidade agora é padrão.
            .setListener { _, _ -> /* Não necessário para restauração */ }
            .build()

        billingClient.startConnection(billingClientStateListener)
    }

    // Listener de estado da conexão separado para reutilização
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
                    // Verifica se a lista de compras não é nula e contém o SKU correto
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
                // Reabilita o botão após a operação terminar
                buttonRestore.isEnabled = true
            }
        }
    }

    private fun grantAdRemoval() {
        // Usa a função de extensão KTX para um código Kotlin mais limpo e seguro.
        // O .apply() é chamado automaticamente.
        getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit {
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
        // Encerra a conexão ao destruir a atividade para liberar recursos
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}