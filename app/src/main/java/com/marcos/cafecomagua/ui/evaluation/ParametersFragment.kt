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
import com.marcos.cafecomagua.app.model.EvaluationStatus
import com.marcos.cafecomagua.app.model.WaterProfile

/**
 * Fragmento para a segunda tela do fluxo de avaliação (parâmetros adicionais).
 * ✅ REFATORADO: Botão de navegação removido.
 * ✅ CORRIGIDO: Perda de foco nos campos resolvida
 */
class ParametersFragment : Fragment() {

    private var _binding: FragmentParametersBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: EvaluationViewModel by activityViewModels()

    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout
    private val df = DecimalFormat("#.##")

    // ✅ NOVO: Flag para evitar loops infinitos de atualização
    private var isUpdatingFromViewModel = false

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
        setupToolbar()
        bindViewModelToViews()
        observeViewModelChanges() // ✅ NOVO: Observadores separados
        calculateDisplayValues() // Cálculo inicial
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        binding.toolbar.setNavigationOnClickListener {
            activity?.finish()
        }
    }

    /**
     * ✅ CORRIGIDO: Separado a inicialização dos listeners
     * Agora não chama calculateDisplayValues() dentro do listener
     */
    private fun bindViewModelToViews() {
        // --- 1. Atualiza a UI se o ViewModel já tiver dados ---
        isUpdatingFromViewModel = true
        sharedViewModel.sodio.value?.takeIf { it > 0 }?.let {
            binding.editTextSodio.setText(it.toString())
        }
        sharedViewModel.ph.value?.takeIf { it > 0 }?.let {
            binding.editTextPH.setText(it.toString())
        }
        sharedViewModel.residuoEvaporacao.value?.takeIf { it > 0 }?.let {
            binding.editTextResiduoEvaporacao.setText(it.toString())
        }
        isUpdatingFromViewModel = false

        // --- 2. Atualiza o ViewModel quando a UI muda ---
        // ✅ CORRIGIDO: Não chama calculateDisplayValues() imediatamente
        // Apenas atualiza o ViewModel
        binding.editTextSodio.addTextChangedListener { editable ->
            if (!isUpdatingFromViewModel) {
                val value = editable.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                sharedViewModel.sodio.value = value
                // calculateDisplayValues() será chamado pelo observer
            }
        }

        binding.editTextPH.addTextChangedListener { editable ->
            if (!isUpdatingFromViewModel) {
                val value = editable.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                sharedViewModel.ph.value = value
                // calculateDisplayValues() será chamado pelo observer
            }
        }

        binding.editTextResiduoEvaporacao.addTextChangedListener { editable ->
            if (!isUpdatingFromViewModel) {
                val value = editable.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                sharedViewModel.residuoEvaporacao.value = value
                // calculateDisplayValues() será chamado pelo observer
            }
        }

        // ✅ NOVO: Botão de resultados
        binding.buttonResultados.setOnClickListener {
            (activity as? EvaluationHostActivity)?.navigateToNextPage()
        }
    }

    /**
     * ✅ NOVO: Observadores separados que chamam calculateDisplayValues()
     * Isso evita chamadas múltiplas e perda de foco
     */
    private fun observeViewModelChanges() {
        // Observa mudanças nos valores e recalcula
        sharedViewModel.calcio.observe(viewLifecycleOwner) {
            calculateDisplayValues()
        }
        sharedViewModel.magnesio.observe(viewLifecycleOwner) {
            calculateDisplayValues()
        }
        sharedViewModel.bicarbonato.observe(viewLifecycleOwner) {
            calculateDisplayValues()
        }
        sharedViewModel.sodio.observe(viewLifecycleOwner) {
            calculateDisplayValues()
        }
        sharedViewModel.ph.observe(viewLifecycleOwner) {
            calculateDisplayValues()
        }
        sharedViewModel.residuoEvaporacao.observe(viewLifecycleOwner) {
            calculateDisplayValues()
        }
    }

    /**
     * ✅ OTIMIZADO
     * Agora é chamado apenas pelos observers, não pelos text listeners
     */
    private fun calculateDisplayValues() {
        val calcio = sharedViewModel.calcio.value ?: 0.0
        val magnesio = sharedViewModel.magnesio.value ?: 0.0
        val bicarbonato = sharedViewModel.bicarbonato.value ?: 0.0
        val sodio = sharedViewModel.sodio.value ?: 0.0
        val ph = sharedViewModel.ph.value ?: 0.0
        val residuo = sharedViewModel.residuoEvaporacao.value ?: 0.0

        // Cálculos usando a fonte única (WaterProfile)
        val profile = WaterProfile(
            calcium = calcio,
            magnesium = magnesio,
            bicarbonate = bicarbonato
        )
        val durezaCalculada = profile.calculateHardness()
        val alcalinidadeCalculada = profile.calculateAlkalinity()

        // ✅ CORRIGIDO: Usar a flag para evitar loop
        isUpdatingFromViewModel = true

        // Exibe valores calculados (usando setText() porque são EditText)
        binding.textViewDurezaCalculada.setText(df.format(durezaCalculada))
        binding.textViewAlcalinidadeCalculada.setText(df.format(alcalinidadeCalculada))

        isUpdatingFromViewModel = false

        // Aplicar avaliações em tempo real
        val durezaStatus = sharedViewModel.mapPointsToStatus(
            WaterEvaluator.getHardnessPoints(durezaCalculada)
        )
        val alcalinidadeStatus = sharedViewModel.mapPointsToStatus(
            WaterEvaluator.getAlkalinityPoints(alcalinidadeCalculada)
        )
        val sodioStatus = sharedViewModel.mapPointsToStatus(
            WaterEvaluator.getSodiumPoints(sodio)
        )
        val phStatus = sharedViewModel.getPhEvaluationStatus(ph)
        val residuoStatus = sharedViewModel.mapPointsToStatus(
            WaterEvaluator.getTdsPoints(residuo)
        )

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
        textView.visibility = View.VISIBLE

        textView.text = when(status) {
            EvaluationStatus.IDEAL -> getString(R.string.avaliacao_ideal)
            EvaluationStatus.ACEITAVEL -> getString(R.string.avaliacao_aceitavel)
            EvaluationStatus.NAO_RECOMENDADO -> getString(R.string.avaliacao_nao_recomendado)
            EvaluationStatus.NA -> getString(R.string.avaliacao_nao_avaliado)
        }

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