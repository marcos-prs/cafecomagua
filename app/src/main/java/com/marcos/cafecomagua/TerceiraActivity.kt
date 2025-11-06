package com.marcos.cafecomagua

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.marcos.cafecomagua.databinding.ActivityTerceiraBinding
import java.text.DecimalFormat
import java.util.Date

class TerceiraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerceiraBinding
    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout
    private var calcio: Double = 0.0
    private var magnesio: Double = 0.0
    private var bicarbonato: Double = 0.0
    private var nomeAgua: String = ""
    private var fonteAgua: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityTerceiraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adContainerView = binding.adContainer

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
        getIntentData()
        setupListeners()
        loadAdaptiveAd()
        calculateAndEvaluateAll()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_help -> {
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

    private fun loadAdaptiveAd() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val adsRemoved = sharedPref.getBoolean("ads_removed", false)

        if (!adsRemoved) {
            MobileAds.initialize(this) {}
            adView = AdView(this)
            adView?.adUnitId = "ca-app-pub-7526020095328101/2958565121" // ID de teste
            adContainerView.removeAllViews()
            adContainerView.addView(adView)
            val displayMetrics = resources.displayMetrics
            val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
            adView?.setAdSize(adSize)
            val adRequest = AdRequest.Builder().build()
            adView?.loadAd(adRequest)
            adContainerView.visibility = View.VISIBLE
        } else {
            adContainerView.visibility = View.GONE
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
        binding.textViewDurezaCalculada.setText(getString(R.string.unidade_mg_l, df.format(durezaCalculada)))
        binding.textViewAlcalinidadeCalculada.setText(getString(R.string.unidade_mg_l, df.format(alcalinidadeCalculada)))

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
                10.0 -> getString(R.string.avaliacao_ideal) to R.color.avaliacao_verde
                in 9.7..10.3 -> getString(R.string.avaliacao_aceitavel) to R.color.avaliacao_amarelo
                else -> getString(R.string.avaliacao_nao_recomendado) to R.color.avaliacao_vermelho
            }
            "PH" -> when (valor) {
                7.0 -> getString(R.string.avaliacao_ideal) to R.color.avaliacao_verde
                in 6.5..7.5 -> getString(R.string.avaliacao_aceitavel) to R.color.avaliacao_amarelo
                else -> getString(R.string.avaliacao_nao_recomendado) to R.color.avaliacao_vermelho
            }
            "ResiduoEvaporacao" -> when (valor) {
                150.0 -> getString(R.string.avaliacao_ideal) to R.color.avaliacao_verde
                in 75.0..175.0 -> getString(R.string.avaliacao_aceitavel) to R.color.avaliacao_amarelo
                else -> getString(R.string.avaliacao_nao_recomendado) to R.color.avaliacao_vermelho
            }
            "Dureza" -> when (valor) {
                in 68.0..85.0 -> getString(R.string.avaliacao_ideal) to R.color.avaliacao_verde
                in 50.0..74.99 -> getString(R.string.avaliacao_aceitavel) to R.color.avaliacao_amarelo
                else -> getString(R.string.avaliacao_nao_recomendado) to R.color.avaliacao_vermelho
            }
            "Alcalinidade" -> when (valor) {
                40.0 -> getString(R.string.avaliacao_ideal) to R.color.avaliacao_verde
                in 35.0..45.0 -> getString(R.string.avaliacao_aceitavel) to R.color.avaliacao_amarelo
                else -> getString(R.string.avaliacao_nao_recomendado) to R.color.avaliacao_vermelho
            }
            else -> getString(R.string.avaliacao_nao_avaliado) to android.R.color.darker_gray
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

    // ✨ CORREÇÃO DO TIPO DE RETORNO
    private fun getOverallQuality(pontuacaoTotal: Double): String {
        return when {
            pontuacaoTotal >= 20.0 -> getString(R.string.quality_high)
            pontuacaoTotal >= 12.0 -> getString(R.string.quality_acceptable)
            else -> getString(R.string.quality_low)
        }
    }

    private fun navigateToResults() {
        val pontuacaoTotal = calculateTotalScore()
        // ✨ CORREÇÃO DA CHAMADA
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

        val intentToSupport = Intent(this, SupportActivity::class.java).apply {
            putExtra("avaliacaoAtual", resultadoAtual)
        }
        startActivity(intentToSupport)
    }

    private fun getEvaluationText(valor: Double, parametro: String): String {
        return when (parametro) {
            "Sodio" -> when (valor) {
                10.0 -> getString(R.string.avaliacao_ideal)
                in 9.7..10.3 -> getString(R.string.avaliacao_aceitavel)
                else -> getString(R.string.avaliacao_nao_recomendado)
            }
            "PH" -> when (valor) {
                7.0 -> getString(R.string.avaliacao_ideal)
                in 6.5..7.5 -> getString(R.string.avaliacao_aceitavel)
                else -> getString(R.string.avaliacao_nao_recomendado)
            }
            "ResiduoEvaporacao" -> when (valor) {
                150.0 -> getString(R.string.avaliacao_ideal)
                in 75.0..175.0 -> getString(R.string.avaliacao_aceitavel)
                else -> getString(R.string.avaliacao_nao_recomendado)
            }
            "Dureza" -> when (valor) {
                in 68.0..85.0 -> getString(R.string.avaliacao_ideal)
                in 50.0..74.99 -> getString(R.string.avaliacao_aceitavel)
                else -> getString(R.string.avaliacao_nao_recomendado)
            }
            "Alcalinidade" -> when (valor) {
                40.0 -> getString(R.string.avaliacao_ideal)
                in 35.0..45.0 -> getString(R.string.avaliacao_aceitavel)
                else -> getString(R.string.avaliacao_nao_recomendado)
            }
            else -> getString(R.string.avaliacao_nao_avaliado)
        }
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }
}