package com.marcos.cafecomagua.ui.evaluation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.marcos.cafecomagua.databinding.FragmentParametersBinding
import java.text.DecimalFormat
import com.marcos.cafecomagua.app.logic.WaterEvaluator
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.model.EvaluationStatus // ✅ ADICIONADO ESTE IMPORT
import com.marcos.cafecomagua.app.model.WaterProfile

/**
 * Fragmento para a segunda tela do fluxo de avaliação (parâmetros adicionais).
 * ✅ REFATORADO: Botão de navegação removido.
 */
class ParametersFragment : Fragment() {

    private var _binding: FragmentParametersBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: EvaluationViewModel by activityViewModels()

    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout
    private val df = DecimalFormat("#.##")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParametersBinding.inflate(inflater, container, false)
        adContainerView = binding.adContainer
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdBanner()
        setupListeners()
        bindViewModelToViews()
        calculateDisplayValues()
        setupToolbar()
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

    private fun bindViewModelToViews() {
        // --- 1. Atualiza a UI se o ViewModel já tiver dados ---
        sharedViewModel.sodio.value?.takeIf { it > 0 }?.let { binding.editTextSodio.setText(it.toString()) }
        sharedViewModel.ph.value?.takeIf { it > 0 }?.let { binding.editTextPH.setText(it.toString()) }
        sharedViewModel.residuoEvaporacao.value?.takeIf { it > 0 }?.let { binding.editTextResiduoEvaporacao.setText(it.toString()) }

        // --- 2. Atualiza o ViewModel quando a UI muda ---
        binding.editTextSodio.addTextChangedListener {
            sharedViewModel.sodio.value = it.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            calculateDisplayValues() // ✅ Chama o cálculo em tempo real
        }
        binding.editTextPH.addTextChangedListener {
            sharedViewModel.ph.value = it.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            calculateDisplayValues() // ✅ Chama o cálculo em tempo real
        }
        binding.editTextResiduoEvaporacao.addTextChangedListener {
            sharedViewModel.residuoEvaporacao.value = it.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            calculateDisplayValues() // ✅ Chama o cálculo em tempo real
        }

        // --- 3. Observa mudanças nos valores de Dureza/Alcalinidade (que vêm do fragmento anterior) ---
        sharedViewModel.calcio.observe(viewLifecycleOwner) { calculateDisplayValues() }
        sharedViewModel.magnesio.observe(viewLifecycleOwner) { calculateDisplayValues() }
        sharedViewModel.bicarbonato.observe(viewLifecycleOwner) { calculateDisplayValues() }
    }

    /**
     * ✅ ATUALIZADO
     * Liga os botões "fantasmas"
     */
    private fun setupListeners() {
        // A navegação (e o cálculo) agora é feita pela EvaluationHostActivity
        binding.buttonResultados.setOnClickListener {
            (activity as? EvaluationHostActivity)?.navigateToNextPage()
        }
    }

    /**
     * ✅ ATUALIZADO
     * Agora exibe o feedback visual em tempo real
     */
    private fun calculateDisplayValues() {
        val calcio = sharedViewModel.calcio.value ?: 0.0
        val magnesio = sharedViewModel.magnesio.value ?: 0.0
        val bicarbonato = sharedViewModel.bicarbonato.value ?: 0.0
        val sodio = sharedViewModel.sodio.value ?: 0.0
        val ph = sharedViewModel.ph.value ?: 0.0
        val residuo = sharedViewModel.residuoEvaporacao.value ?: 0.0

        // ✅ REFATORADO: Cálculos usam a fonte única (WaterProfile)
        val profile = WaterProfile(
            calcium = calcio,
            magnesium = magnesio,
            bicarbonate = bicarbonato
        )
        val durezaCalculada = profile.calculateHardness()
        val alcalinidadeCalculada = profile.calculateAlkalinity()

        // Exibe valores calculados
        binding.textViewDurezaCalculada.setText(df.format(durezaCalculada))
        binding.textViewAlcalinidadeCalculada.setText(df.format(alcalinidadeCalculada))

        // ✅ APLICAR AVALIAÇÕES EM TEMPO REAL
        // Chama as NOVAS funções do ViewModel que retornam Enums
        val durezaStatus = sharedViewModel.mapPointsToStatus(WaterEvaluator.getHardnessPoints(durezaCalculada))
        val alcalinidadeStatus = sharedViewModel.mapPointsToStatus(WaterEvaluator.getAlkalinityPoints(alcalinidadeCalculada))
        val sodioStatus = sharedViewModel.mapPointsToStatus(WaterEvaluator.getSodiumPoints(sodio))
        val phStatus = sharedViewModel.getPhEvaluationStatus(ph)
        val residuoStatus = sharedViewModel.mapPointsToStatus(WaterEvaluator.getTdsPoints(residuo))

        // Aplica o feedback visual
        updateValidationView(binding.textViewDurezaAvaliacao, durezaStatus)
        updateValidationView(binding.textViewAlcalinidadeAvaliacao, alcalinidadeStatus)
        updateValidationView(binding.textViewSodioAvaliacao, sodioStatus)
        updateValidationView(binding.textViewPHAvaliacao, phStatus)
        updateValidationView(binding.textViewResiduoEvaporacaoAvaliacao, residuoStatus)
    }

    /**
     * ✅ ATUALIZADO
     * Recebe um 'EvaluationStatus' (Enum) e define o texto e a cor
     */
    private fun updateValidationView(textView: TextView, status: EvaluationStatus) {

        // 1. O TextView agora é sempre visível, não vamos mais escondê-lo.
        textView.visibility = View.VISIBLE

        // 2. O 'when' do texto agora lida com todos os 4 casos do Enum
        textView.text = when(status) {
            EvaluationStatus.IDEAL -> getString(R.string.avaliacao_ideal)
            EvaluationStatus.ACEITAVEL -> getString(R.string.avaliacao_aceitavel)
            EvaluationStatus.NAO_RECOMENDADO -> getString(R.string.avaliacao_nao_recomendado)
            // ✅ Caso NA adicionado (presumindo que você tenha esta string):
            EvaluationStatus.NA -> getString(R.string.avaliacao_nao_avaliado)
        }

        // 3. O 'when' da cor agora também lida com o NA (a cor cinza que você mencionou)
        val colorRes = when (status) {
            EvaluationStatus.IDEAL -> R.color.ideal_green
            EvaluationStatus.ACEITAVEL -> R.color.acceptable_yellow
            EvaluationStatus.NAO_RECOMENDADO -> R.color.not_recommended_red
            EvaluationStatus.NA -> R.color.na_text_gray
        }

        textView.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun setupAdBanner() {
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val adsRemoved = sharedPref.getBoolean("ads_removed", false)

        if (adsRemoved) {
            adContainerView.visibility = View.GONE
            return
        }

        adView = AdView(requireContext())
        adView?.apply {
            setAdSize(AdSize.BANNER)
            adUnitId = "ca-app-pub-7526020095328101/2793229383"
        }
        adContainerView.removeAllViews()
        adContainerView.addView(adView)

        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }

    // --- (Ciclo de Vida) ---

    override fun onDestroyView() {
        super.onDestroyView()
        adView?.destroy()
        adView = null
        _binding = null
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }
}