package com.marcos.cafecomagua

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.marcos.cafecomagua.databinding.ActivityResultadosBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DecimalFormat

class ResultadosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultadosBinding
    private var avaliacaoAtual: AvaliacaoResultado? = null
    private lateinit var adView: AdView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityResultadosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // --- INÍCIO DA CORREÇÃO DE LAYOUT ---
        // A lógica de insets foi simplificada, seguindo o padrão da HistoricoActivity.
        // O método setupWindowInsets() foi removido e a lógica colocada diretamente aqui.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Aplica padding na view raiz. Isso corrige o topo e a base (anúncio) de uma só vez.
            view.updatePadding(
                top = insets.top,
                bottom = insets.bottom
            )
            windowInsets
        }
        // --- FIM DA CORREÇÃO DE LAYOUT ---

        getEvaluationData()
        setupListeners()
        loadAdaptiveAd()
        requestReviewIfAppropriate()
    }

    // O método setupWindowInsets() foi removido pois sua lógica foi movida para o onCreate.

    private fun getEvaluationData() {
        @Suppress("DEPRECATION")
        avaliacaoAtual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("avaliacaoAtual", AvaliacaoResultado::class.java)
        } else {
            intent.getSerializableExtra("avaliacaoAtual") as? AvaliacaoResultado
        }

        avaliacaoAtual?.let { displayResults(it) }
            ?: Toast.makeText(this, getString(R.string.toast_erro_carregar_resultados), Toast.LENGTH_LONG).show()
    }

    private fun displayResults(resultado: AvaliacaoResultado) {
        val df = DecimalFormat("#.##")
        binding.textViewPontuacao.text = getString(R.string.pontuacao_total_format, resultado.pontuacaoTotal)

        val qualidadeText = when (resultado.qualidadeGeral) {
            "Alta" -> getString(R.string.qualidade_alta)
            "Aceitável" -> getString(R.string.qualidade_aceitavel)
            "Baixa" -> getString(R.string.qualidade_baixa)
            else -> resultado.qualidadeGeral
        }

        binding.textViewQualidade.text = getString(R.string.label_qualidade_geral, qualidadeText)
        setStyleByRating(binding.textViewQualidade, qualidadeText)

        setupParametroView(binding.layoutPh.root, getString(R.string.param_ph_valor, df.format(resultado.ph)), resultado.avaliacaoPh)
        setupParametroView(binding.layoutDureza.root, getString(R.string.param_dureza_valor, df.format(resultado.dureza)), resultado.avaliacaoDureza)
        setupParametroView(binding.layoutAlcalinidade.root, getString(R.string.param_alcalinidade_valor, df.format(resultado.alcalinidade)), resultado.avaliacaoAlcalinidade)
        setupParametroView(binding.layoutSodio.root, getString(R.string.param_sodio_valor, df.format(resultado.sodio)), resultado.avaliacaoSodio)
        setupParametroView(binding.layoutResiduo.root, getString(R.string.param_residuo_valor, df.format(resultado.residuoEvaporacao)), resultado.avaliacaoResiduoEvaporacao)
    }

    private fun setStyleByRating(textView: TextView, rating: String) {
        val backgroundRes = when (rating) {
            getString(R.string.avaliacao_ideal), getString(R.string.qualidade_alta) -> R.drawable.background_box_ideal
            getString(R.string.avaliacao_aceitavel), getString(R.string.qualidade_aceitavel) -> R.drawable.background_box_acceptable
            else -> R.drawable.background_box_not_recommended
        }
        textView.setBackgroundResource(backgroundRes)
    }

    private fun requestReviewIfAppropriate() {
        val prefs = getSharedPreferences("app_ratings", MODE_PRIVATE)
        val openCount = prefs.getInt("open_count", 0)
        val alreadyRequested = prefs.getBoolean("already_requested", false)

        if (openCount >= 30 && !alreadyRequested) {
            val manager = ReviewManagerFactory.create(this)

            lifecycleScope.launch {
                try {
                    val reviewInfo: ReviewInfo = manager.requestReviewFlow().await()
                    manager.launchReviewFlow(this@ResultadosActivity, reviewInfo).await()

                    prefs.edit {
                        putBoolean("already_requested", true)
                    }
                } catch (e: Exception) {
                    Log.e("InAppReview", "Error in review flow", e)
                }
            }
        }
    }

    private fun loadAdaptiveAd() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val adsRemoved = sharedPref.getBoolean("ads_removed", false)

        if (!adsRemoved) {
            MobileAds.initialize(this) {}
            adView = AdView(this)
            binding.adContainer.addView(adView)

            // --- INÍCIO DA CORREÇÃO DO ANÚNCIO ---
            // Usando displayMetrics para um cálculo mais confiável da largura da tela.
            val displayMetrics = resources.displayMetrics
            val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
            // --- FIM DA CORREÇÃO DO ANÚNCIO ---

            adView.setAdSize(adSize)
            adView.adUnitId = "ca-app-pub-3940256099942544/6300978111" // ID de teste
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
        } else {
            binding.adContainer.isVisible = false
        }
    }

    private fun setupListeners() {
        binding.buttonSalvarAvaliacao.setOnClickListener {
            avaliacaoAtual?.let { avaliacao ->
                AppDataSource.addAvaliacao(avaliacao)
                Toast.makeText(this, getString(R.string.toast_avaliacao_salva), Toast.LENGTH_SHORT).show()
                it.isEnabled = false
            }
        }

        binding.buttonNovaAvaliacao.setOnClickListener {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(this)
            }
            finish()
        }

        binding.buttonVerHistorico.setOnClickListener {
            startActivity(Intent(this, HistoricoAvaliacoesActivity::class.java))
        }

        binding.buttonVoltar.setOnClickListener {
            finish()
        }
    }

    private fun setupParametroView(view: View, nome: String, avaliacao: String) {
        val nomeTextView = view.findViewById<TextView>(R.id.textViewParametroNome)
        val avaliacaoTextView = view.findViewById<TextView>(R.id.textViewParametroAvaliacao)

        val textoAbreviado = if (avaliacao == getString(R.string.avaliacao_nao_recomendado)) {
            getString(R.string.avaliacao_nao_recomendado_abrev)
        } else {
            avaliacao
        }

        nomeTextView.text = nome
        avaliacaoTextView.text = textoAbreviado
        setStyleByRating(avaliacaoTextView, avaliacao)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.global_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        if (::adView.isInitialized) {
            adView.destroy()
        }
        super.onDestroy()
    }
}