package com.marcos.cafecomagua.ui.help

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.databinding.ActivityHelpBinding

/**
 * ✅ REFATORADO
 * - Removida TODA a lógica de BillingClient (restauração de compra),
 * pois ela agora é gerenciada pelo SubscriptionManager em outras telas.
 * - Esta Activity agora é 100% informativa.
 * - Adicionada a lógica para o novo tópico da Calculadora (Ponto 11).
 */
class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding
    // ❌ REMOVIDO: billingClient e skuRemoveAds

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
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
        setupContent()
        handleIntentExtras()

        // ❌ REMOVIDO: setupBillingClient()
        // ❌ REMOVIDO: setupListeners() (o botão de restaurar não existe mais)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    /**
     * ✅ ATUALIZADO (Ponto 11)
     * Carrega os textos da nova filosofia E o novo texto da calculadora.
     */
    private fun setupContent() {
        binding.textViewTopic1Content.text = HtmlCompat.fromHtml(
            getString(R.string.help_topic_1_param_qual_content),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        binding.textViewTopic2Content.text = HtmlCompat.fromHtml(
            getString(R.string.help_topic_2_tds_content),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        binding.textViewTopic3Content.text = HtmlCompat.fromHtml(
            getString(R.string.help_topic_3_scoring_system_content),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        binding.textViewTopicOcrContent.text = HtmlCompat.fromHtml(
            getString(R.string.help_topic_ocr_content),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )

        // ✅ ADICIONADO (Ponto 11): Carrega o novo texto da calculadora
        // (Você precisará adicionar 'textViewTopicCalculatorContent' ao seu XML)
        binding.textViewTopicCalculatorContent.text = HtmlCompat.fromHtml(
            getString(R.string.help_topic_calculator_content),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
    }

    private fun handleIntentExtras() {
        val scrollToSection = intent.getStringExtra("SCROLL_TO_SECTION")
        if (scrollToSection == "OCR_HELP") {
            binding.scrollViewHelp.post {
                binding.scrollViewHelp.smoothScrollTo(0, binding.textViewTopicOcrTitle.top)
            }
        }
        // (Você pode adicionar um novo marcador para a seção da Calculadora aqui)
        if (scrollToSection == "WATER_OPTIMIZATION_GUIDE") {
            binding.scrollViewHelp.post {
                binding.scrollViewHelp.smoothScrollTo(0, binding.textViewTopicCalculatorTitle.top)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}