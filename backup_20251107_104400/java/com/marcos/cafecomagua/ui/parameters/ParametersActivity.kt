package com.marcos.cafecomagua.ui.parameters

import android.R
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.ui.help.HelpActivity
import com.marcos.cafecomagua.app.billing.SubscriptionActivity
import com.marcos.cafecomagua.app.analytics.AnalyticsManager
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.databinding.ActivityParametersBinding
import java.text.DecimalFormat
import java.util.Date
import com.marcos.cafecomagua.app.analytics.Event
import com.marcos.cafecomagua.app.analytics.AnalyticsManager.Event

/**
 * ParametersActivity (ex-TerceiraActivity)
 * Tela de parâmetros adicionais da água
 *
 * MUDANÇAS DA REFATORAÇÃO:
 * ✅ Banner removido (conforme estratégia)
 * ✅ Integrado analytics
 * ✅ Navega para SubscriptionActivity (refatorado)
 */
class ParametersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParametersBinding
    private var calcio: Double = 0.0
    private var magnesio: Double = 0.0
    private var bicarbonato: Double = 0.0
    private var nomeAgua: String = ""
    private var fonteAgua: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityParametersBinding.inflate(layoutInflater)
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

        // ✅ NOVO: Analytics
        analytics().logEvent(
            Category.NAVIGATION,
            Event.SCREEN_VIEWED,
            mapOf("screen_name" to "parameters")
        )

        setupToolbar()
        getIntentData()
        setupListeners()
        // ✅ REMOVIDO: Banner não é mais exibido aqui
        calculateAndEvaluateAll()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.home -> {
                finish()
                true
            }
            com.marcos.cafecomagua.R.id.action_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getIntentData() {
        intent?.let {
            calcio = it.getDoubleExtra("calcio", 0.0)
            magnesio = it.getDoubleExtra("magnesio", 0.0)
            bicarbonato = it.getDoubleExtra("bicarbonato", 0.0)
            nomeAgua = it.getStringExtra("nomeAgua") ?: ""
            fonteAgua = it.getStringExtra("fonteAgua") ?: ""
            if (it.hasExtra("sodio_ocr")) {
                val sodioOcr = it.getStringExtra("sodio_ocr")
                binding.editTextSodio.setText(sodioOcr)
            }
            if (it.hasExtra("ph_ocr")) {
                val phOcr = it.getStringExtra("ph_ocr")
                binding.editTextPH.setText(phOcr)
            }
            if (it.hasExtra("residuo_ocr")) {
                val residuoOcr = it.getStringExtra("residuo_ocr")
                binding.editTextResiduoEvaporacao.setText(residuoOcr)
            }
        }
    }

    private fun setupListeners() {
        binding.editTextSodio.addTextChangedListener(createCustomTextWatcher { calculateAndEvaluateAll() })
        binding.editTextPH.addTextChangedListener(createCustomTextWatcher { calculateAndEvaluateAll() })
        binding.editTextResiduoEvaporacao.addTextChangedListener(createCustomTextWatcher { calculateAndEvaluateAll() })
        binding.buttonResultados.setOnClickListener {
            navigateToResults()
        }
    }

    private fun calculateAndEvaluateAll() {
        val sodio = parseDouble(binding.editTextSodio.text.toString())
        val ph = parseDouble(binding.editTextPH.text.toString())
        val residuoEvaporacao = parseDouble(binding.editTextResiduoEvaporacao.text.toString())

        val durezaCalculada = (calcio * 2.497) + (magnesio * 4.118)
        val alcalinidadeCalculada = bicarbonato * 0.802

        val df = DecimalFormat("#.##")
        binding.textViewDurezaCalculada.text = getString(com.marcos.cafecomagua.R.string.unidade_mg_l, df.format(durezaCalculada))
        binding.textViewAlcalinidadeCalculada.text = getString(com.marcos.cafecomagua.R.string.unidade_mg_l, df.format(alcalinidadeCalculada))

        evaluateParameter(sodio, "Sodio", binding.textViewSodioAvaliacao)
        evaluateParameter(ph, "PH", binding.textViewPHAvaliacao)
        evaluateParameter(residuoEvaporacao, "ResiduoEvaporacao", binding.textViewResiduoEvaporacaoAvaliacao)
        evaluateParameter(durezaCalculada, "Dureza", binding.textViewDurezaAvaliacao)
        evaluateParameter(alcalinidadeCalculada, "Alcalinidade", binding.textViewAlcalinidadeAvaliacao)
    }

    private fun parseDouble(text: String): Double {
        return try {
            text.replace(",", ".").toDouble()
        } catch (_: NumberFormatException) {
            0.0
        }
    }

    private fun evaluateParameter(valor: Double, parametro: String, textViewAvaliacao: TextView) {
        val (avaliacaoTexto, corAvaliacao) = when (parametro) {
            "Sodio" -> when (valor) {
                10.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_ideal) to com.marcos.cafecomagua.R.color.avaliacao_verde
                in 9.7..10.3 -> getString(com.marcos.cafecomagua.R.string.avaliacao_aceitavel) to com.marcos.cafecomagua.R.color.avaliacao_amarelo
                else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_recomendado) to com.marcos.cafecomagua.R.color.avaliacao_vermelho
            }
            "PH" -> when (valor) {
                7.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_ideal) to com.marcos.cafecomagua.R.color.avaliacao_verde
                in 6.5..7.5 -> getString(com.marcos.cafecomagua.R.string.avaliacao_aceitavel) to com.marcos.cafecomagua.R.color.avaliacao_amarelo
                else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_recomendado) to com.marcos.cafecomagua.R.color.avaliacao_vermelho
            }
            "ResiduoEvaporacao" -> when (valor) {
                150.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_ideal) to com.marcos.cafecomagua.R.color.avaliacao_verde
                in 75.0..175.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_aceitavel) to com.marcos.cafecomagua.R.color.avaliacao_amarelo
                else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_recomendado) to com.marcos.cafecomagua.R.color.avaliacao_vermelho
            }
            "Dureza" -> when (valor) {
                in 68.0..85.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_ideal) to com.marcos.cafecomagua.R.color.avaliacao_verde
                in 50.0..74.99 -> getString(com.marcos.cafecomagua.R.string.avaliacao_aceitavel) to com.marcos.cafecomagua.R.color.avaliacao_amarelo
                else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_recomendado) to com.marcos.cafecomagua.R.color.avaliacao_vermelho
            }
            "Alcalinidade" -> when (valor) {
                40.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_ideal) to com.marcos.cafecomagua.R.color.avaliacao_verde
                in 35.0..45.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_aceitavel) to com.marcos.cafecomagua.R.color.avaliacao_amarelo
                else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_recomendado) to com.marcos.cafecomagua.R.color.avaliacao_vermelho
            }
            else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_avaliado) to R.color.darker_gray
        }

        textViewAvaliacao.text = avaliacaoTexto
        textViewAvaliacao.setTextColor(ContextCompat.getColor(this, corAvaliacao))
        textViewAvaliacao.visibility = if (valor > 0) View.VISIBLE else View.INVISIBLE
    }

    private fun createCustomTextWatcher(onTextChanged: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onTextChanged() }
            override fun afterTextChanged(s: Editable?) {}
        }
    }

    private fun calculateTotalScore(): Double {
        val sodio = parseDouble(binding.editTextSodio.text.toString())
        val ph = parseDouble(binding.editTextPH.text.toString())
        val residuoEvaporacao = parseDouble(binding.editTextResiduoEvaporacao.text.toString())
        val durezaCalculada = (calcio * 2.5) + (magnesio * 4.2)
        val alcalinidadeCalculada = bicarbonato * 0.8

        return getScore(sodio, "Sodio") +
                getScore(ph, "PH") +
                getScore(residuoEvaporacao, "ResiduoEvaporacao") +
                getScore(durezaCalculada, "Dureza") +
                getScore(alcalinidadeCalculada, "Alcalinidade")
    }

    private fun getScore(valor: Double, parametro: String): Double {
        return when (parametro) {
            "Sodio" -> when (valor) {
                10.0 -> 5.0
                in 9.7..10.3 -> 3.5
                else -> 1.0
            }
            "PH" -> when (valor) {
                7.0 -> 5.0
                in 6.5..7.5 -> 3.5
                else -> 1.0
            }
            "ResiduoEvaporacao" -> when (valor) {
                150.0 -> 5.0
                in 75.0..175.0 -> 3.5
                else -> 1.0
            }
            "Dureza" -> when (valor) {
                in 68.0..85.0 -> 5.0
                in 50.0..74.99 -> 3.5
                else -> 1.0
            }
            "Alcalinidade" -> when (valor) {
                40.0 -> 5.0
                in 35.0..45.0 -> 3.5
                else -> 1.0
            }
            else -> 0.0
        }
    }

    private fun getOverallQuality(pontuacaoTotal: Double): String {
        return when {
            pontuacaoTotal >= 20.0 -> getString(com.marcos.cafecomagua.R.string.quality_high)
            pontuacaoTotal >= 12.0 -> getString(com.marcos.cafecomagua.R.string.quality_acceptable)
            else -> getString(com.marcos.cafecomagua.R.string.quality_low)
        }
    }

    private fun navigateToResults() {
        val pontuacaoTotal = calculateTotalScore()
        val qualidadeGeral = getOverallQuality(pontuacaoTotal)

        val sodio = parseDouble(binding.editTextSodio.text.toString())
        val ph = parseDouble(binding.editTextPH.text.toString())
        val residuoEvaporacao = parseDouble(binding.editTextResiduoEvaporacao.text.toString())
        val durezaCalculada = (calcio * 2.5) + (magnesio * 4.2)
        val alcalinidadeCalculada = bicarbonato * 0.8

        val resultadoAtual = AvaliacaoResultado(
            dataAvaliacao = Date(),
            nomeAgua = nomeAgua,
            fonteAgua = fonteAgua,
            pontuacaoTotal = pontuacaoTotal,
            qualidadeGeral = qualidadeGeral,
            ph = ph,
            avaliacaoPh = getEvaluationText(ph, "PH"),
            dureza = durezaCalculada,
            avaliacaoDureza = getEvaluationText(durezaCalculada, "Dureza"),
            alcalinidade = alcalinidadeCalculada,
            avaliacaoAlcalinidade = getEvaluationText(alcalinidadeCalculada, "Alcalinidade"),
            sodio = sodio,
            avaliacaoSodio = getEvaluationText(sodio, "Sodio"),
            residuoEvaporacao = residuoEvaporacao,
            avaliacaoResiduoEvaporacao = getEvaluationText(residuoEvaporacao, "ResiduoEvaporacao")
        )

        // ✅ MODIFICADO: Navega para SubscriptionActivity (refatorado)
        val intentToSubscription = Intent(this, SubscriptionActivity::class.java).apply {
            putExtra("avaliacaoAtual", resultadoAtual)
        }
        startActivity(intentToSubscription)
    }

    private fun getEvaluationText(valor: Double, parametro: String): String {
        return when (parametro) {
            "Sodio" -> when (valor) {
                10.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_ideal)
                in 9.7..10.3 -> getString(com.marcos.cafecomagua.R.string.avaliacao_aceitavel)
                else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_recomendado)
            }
            "PH" -> when (valor) {
                7.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_ideal)
                in 6.5..7.5 -> getString(com.marcos.cafecomagua.R.string.avaliacao_aceitavel)
                else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_recomendado)
            }
            "ResiduoEvaporacao" -> when (valor) {
                150.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_ideal)
                in 75.0..175.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_aceitavel)
                else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_recomendado)
            }
            "Dureza" -> when (valor) {
                in 68.0..85.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_ideal)
                in 50.0..74.99 -> getString(com.marcos.cafecomagua.R.string.avaliacao_aceitavel)
                else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_recomendado)
            }
            "Alcalinidade" -> when (valor) {
                40.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_ideal)
                in 35.0..45.0 -> getString(com.marcos.cafecomagua.R.string.avaliacao_aceitavel)
                else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_recomendado)
            }
            else -> getString(com.marcos.cafecomagua.R.string.avaliacao_nao_avaliado)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Banner removido - sem cleanup
    }
}