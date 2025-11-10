package com.marcos.cafecomagua.ui.evaluation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.MyApplication
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.databinding.FragmentResultsBinding
import com.marcos.cafecomagua.ui.history.HistoryActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DecimalFormat
import com.marcos.cafecomagua.billing.SubscriptionManager
import com.marcos.cafecomagua.app.billing.SubscriptionActivity
import com.marcos.cafecomagua.ads.InterstitialAdManager
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.app.analytics.AnalyticsManager
import com.marcos.cafecomagua.app.model.EvaluationStatus // Importação necessária

/**
 * Fragmento para a tela final de resultados.
 * Substitui a ResultsActivity.
 */
class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: EvaluationViewModel by activityViewModels()
    private var avaliacaoAtual: AvaliacaoResultado? = null

    private val subscriptionManager: SubscriptionManager by lazy {
        SubscriptionManager(requireContext(), lifecycleScope)
    }

    private val interstitialManager: InterstitialAdManager by lazy {
        InterstitialAdManager(
            context = requireContext(),
            adUnitId = "ca-app-pub-7526020095328101/9326848140" // (ID da Home, use o seu ID de Resultados)
        ).apply {
            // ✅ ATUALIZADO:
            // Diz ao app para fechar a tela APENAS DEPOIS que o anúncio for fechado.
            onAdDismissed = {
                activity?.finish() // Fecha a Activity
            }
            // ✅ ATUALIZADO:
            // Se o anúncio falhar, feche a tela mesmo assim.
            onAdFailedToShow = {
                activity?.finish() // Fecha a Activity
            }
            onAdShown = {
                requireContext().analytics().logEvent(
                    AnalyticsManager.Category.USER_ACTION,
                    AnalyticsManager.Event.AD_SHOWN,
                    mapOf("ad_type" to "interstitial", "location" to "results_exit")
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        interstitialManager
        setupListeners()
        observeViewModel()
        requestReviewIfAppropriate()

    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false) // O título já está no XML
        }
        binding.toolbar.setNavigationOnClickListener {
            activity?.finish() // Fecha a EvaluationHostActivity
        }
    }


    private fun observeViewModel() {
        sharedViewModel.resultadoFinal.observe(viewLifecycleOwner) { resultado ->
            if (resultado == null) {
                Log.e("ResultsFragment", "Resultado é nulo, calculando novamente.")
                sharedViewModel.calcularResultado()
                return@observe
            }
            this.avaliacaoAtual = resultado
            displayResults(resultado)
        }
    }

    private fun displayResults(resultado: AvaliacaoResultado) {
        val df = DecimalFormat("#.##")
        binding.textViewPontuacao.text = getString(R.string.pontuacao_total_format, resultado.pontuacaoTotal)

        // ✅ LÓGICA SIMPLIFICADA: Lê o Enum (EvaluationStatus) diretamente
        val qualidadeStatus = resultado.qualidadeGeral

        // ✅ USA O ENUM PARA DEFINIR O TEXTO
        binding.textViewQualidade.text = getString(
            R.string.label_qualidade_geral,
            when (qualidadeStatus) {
                EvaluationStatus.IDEAL -> getString(R.string.avaliacao_ideal)
                EvaluationStatus.ACEITAVEL -> getString(R.string.avaliacao_aceitavel)
                // Usamos NAO_RECOMENDADO como fallback, pois NA não é aplicável à qualidade geral
                EvaluationStatus.NAO_RECOMENDADO -> getString(R.string.avaliacao_nao_recomendado)
                EvaluationStatus.NA -> getString(R.string.avaliacao_nao_recomendado)
            }
        )
        // ✅ USA O ENUM PARA DEFINIR O ESTILO
        setStyleByStatus(binding.textViewQualidade, qualidadeStatus)

        // ✅ PASSA O ENUM DIRETAMENTE para setupParametroView
        setupParametroView(binding.layoutPh.root, getString(R.string.param_ph_valor, df.format(resultado.ph)), resultado.avaliacaoPh)
        setupParametroView(binding.layoutDureza.root, getString(R.string.param_dureza_valor, df.format(resultado.dureza)), resultado.avaliacaoDureza)
        setupParametroView(binding.layoutAlcalinidade.root, getString(R.string.param_alcalinidade_valor, df.format(resultado.alcalinidade)), resultado.avaliacaoAlcalinidade)
        setupParametroView(binding.layoutSodio.root, getString(R.string.param_sodio_valor, df.format(resultado.sodio)), resultado.avaliacaoSodio)
        setupParametroView(binding.layoutResiduo.root, getString(R.string.param_residuo_valor, df.format(resultado.residuoEvaporacao)), resultado.avaliacaoResiduoEvaporacao)

        // Lógica do CTA Premium
        if (!subscriptionManager.isPremiumActive()) {
            // Usamos o Enum para verificar se o CTA deve aparecer
            if (qualidadeStatus == EvaluationStatus.ACEITAVEL || qualidadeStatus == EvaluationStatus.NAO_RECOMENDADO) {

                val qualidadeText = when(qualidadeStatus) {
                    EvaluationStatus.ACEITAVEL -> getString(R.string.avaliacao_aceitavel)
                    EvaluationStatus.NAO_RECOMENDADO -> getString(R.string.avaliacao_nao_recomendado)
                    else -> ""
                }
                binding.textCtaMessage.text = getString(R.string.results_cta_message, qualidadeText)
                binding.cardCtaPremium.visibility = View.VISIBLE
            }
        } else {
            binding.cardCtaPremium.visibility = View.GONE
        }
    }


    private fun setupListeners() {
        binding.buttonSalvarAvaliacao.setOnClickListener {
            avaliacaoAtual?.let { avaliacao ->
                lifecycleScope.launch {
                    val db = (activity?.application as? MyApplication)?.database
                    db?.avaliacaoDao()?.insert(avaliacao)
                }
                Toast.makeText(requireContext(), getString(R.string.toast_avaliacao_salva), Toast.LENGTH_SHORT).show()
                it.isEnabled = false
            }
        }

        binding.buttonCtaSubscribe.setOnClickListener {
            startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
        }

        // ✅ LÓGICA DO INTERSTICIAL CORRIGIDA
        binding.buttonNovaAvaliacao.setOnClickListener {

            // 1. Tenta mostrar o anúncio TODA VEZ (frequência = 1)
            val adWasTriggered = interstitialManager.showIfAvailable(
                activity = requireActivity(),
                counterKey = "results_exit",
                frequency = 1 // <-- Definido como 1 para mostrar sempre
            )
            // 2. AÇÃO 'activity?.finish()' REMOVIDA DAQUI
            // O fechamento agora é controlado pelos callbacks (onAdDismissed/onAdFailedToShow)

            // 3. Fallback: Se o manager falhar em iniciar (ex: sem internet),
            // e não acionar os callbacks, feche a tela manualmente.
            if (!adWasTriggered) {
                activity?.finish()
            }
        }

        binding.buttonVerHistorico.setOnClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
    }

    private fun requestReviewIfAppropriate() {
        val prefs = requireContext().getSharedPreferences("app_ratings", Context.MODE_PRIVATE)
        val currentCount = prefs.getInt("results_view_count", 0)
        val newCount = currentCount + 1
        prefs.edit { putInt("results_view_count", newCount) }

        if (newCount % 13 == 0) {
            val manager = ReviewManagerFactory.create(requireContext())
            lifecycleScope.launch {
                try {
                    val reviewInfo: ReviewInfo = manager.requestReviewFlow().await()
                    manager.launchReviewFlow(requireActivity(), reviewInfo).await()
                } catch (e: Exception) {
                    Log.e("InAppReview", "Error in review flow", e)
                }
            }
        }
    }

    /**
     * ✅ REFATORADO: Renomeado de setStyleByRating para setStyleByStatus e recebe EvaluationStatus
     */
    private fun setStyleByStatus(textView: TextView, status: EvaluationStatus) {
        val backgroundRes = when (status) {
            EvaluationStatus.IDEAL -> R.drawable.background_box_ideal
            EvaluationStatus.ACEITAVEL -> R.drawable.background_box_acceptable
            EvaluationStatus.NA -> R.drawable.background_box_na // Usar o estilo N/A se existir
            EvaluationStatus.NAO_RECOMENDADO -> R.drawable.background_box_not_recommended
        }
        textView.setBackgroundResource(backgroundRes)
    }

    /**
     * ✅ REFATORADO: Agora recebe o Enum (EvaluationStatus) diretamente
     */
    private fun setupParametroView(view: View, nome: String, status: EvaluationStatus) {
        val nomeTextView = view.findViewById<TextView>(R.id.textViewParametroNome)
        val avaliacaoTextView = view.findViewById<TextView>(R.id.textViewParametroAvaliacao)

        // ✅ CONVERTE ENUM PARA TEXTO (usando R.string)
        val textoAvaliacao = when (status) {
            EvaluationStatus.IDEAL -> getString(R.string.avaliacao_ideal)
            EvaluationStatus.ACEITAVEL -> getString(R.string.avaliacao_aceitavel)
            EvaluationStatus.NAO_RECOMENDADO -> "Não Rec." // Abreviado para caber no layout
            EvaluationStatus.NA -> getString(R.string.avaliacao_nao_avaliado)
        }

        nomeTextView.text = nome
        avaliacaoTextView.text = textoAvaliacao
        setStyleByStatus(avaliacaoTextView, status) // Usa a função baseada em Enum
    }


    override fun onDestroyView() {
        super.onDestroyView()
        subscriptionManager.destroy()
        interstitialManager.destroy()
        _binding = null
    }
}