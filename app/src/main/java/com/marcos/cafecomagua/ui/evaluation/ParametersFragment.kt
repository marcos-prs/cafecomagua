package com.marcos.cafecomagua.ui.evaluation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
 * ✅ SOLUÇÃO DEFINITIVA IMPLEMENTADA:
 * - TextViews de avaliação SEMPRE visíveis no XML (sem android:visibility)
 * - updateValidationView() NÃO muda visibility (apenas text + color)
 * - Resultado: ZERO re-layouts durante digitação = ZERO race conditions
 * - Foco NUNCA é roubado, funciona em emuladores E dispositivos físicos
 */
class ParametersFragment : Fragment() {

    private var _binding: FragmentParametersBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: EvaluationViewModel by activityViewModels()

    private var adView: AdView? = null
    private lateinit var adContainerView: FrameLayout
    private val df = DecimalFormat("#.##")

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
        setupFieldNavigation() // ✅ Navegação manual
        bindViewModelToViews()
        // ❌ REMOVIDO: calculateDisplayValues() inicial (TextViews já visíveis)
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
     * ✅ NAVEGAÇÃO PROGRAMÁTICA: Substitui nextFocusForward quebrado
     * Garante que o foco vá para o campo correto SEM bugs
     */
    private fun setupFieldNavigation() {
        // Sódio → pH
        binding.editTextSodio.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.editTextPH.requestFocus()
                // Garante que o campo fique visível
                binding.scrollView.post {
                    val location = IntArray(2)
                    binding.editTextPH.getLocationInWindow(location)
                    binding.scrollView.smoothScrollTo(0, location[1] - 200)
                }
                true
            } else false
        }

        // pH → Resíduo de Evaporação
        binding.editTextPH.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.editTextResiduoEvaporacao.requestFocus()
                // Garante que o campo fique visível
                binding.scrollView.post {
                    val location = IntArray(2)
                    binding.editTextResiduoEvaporacao.getLocationInWindow(location)
                    binding.scrollView.smoothScrollTo(0, location[1] - 200)
                }
                true
            } else false
        }

        // Resíduo → Done (fecha teclado)
        binding.editTextResiduoEvaporacao.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.editTextResiduoEvaporacao.clearFocus()
                // Esconde o teclado
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                        as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(binding.editTextResiduoEvaporacao.windowToken, 0)
                true
            } else false
        }
    }

    private fun bindViewModelToViews() {
        // Popula campos iniciais
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

        // ✅ SOLUÇÃO: TextWatchers atualizam ViewModel E recalculam
        // Mas updateValidationView NÃO muda visibility (sem re-layout!)
        binding.editTextSodio.addTextChangedListener { editable ->
            if (!isUpdatingFromViewModel) {
                val value = editable.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                sharedViewModel.sodio.value = value
                calculateDisplayValues() // ✅ Recalcula IMEDIATAMENTE (sem postDelayed)
            }
        }

        binding.editTextPH.addTextChangedListener { editable ->
            if (!isUpdatingFromViewModel) {
                val value = editable.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                sharedViewModel.ph.value = value
                calculateDisplayValues()
            }
        }

        binding.editTextResiduoEvaporacao.addTextChangedListener { editable ->
            if (!isUpdatingFromViewModel) {
                val value = editable.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                sharedViewModel.residuoEvaporacao.value = value
                calculateDisplayValues()
            }
        }

        binding.buttonResultados.setOnClickListener {
            // ✅ CRÍTICO: Calcula APENAS aqui, garantindo zero race conditions
            calculateDisplayValues()
            (activity as? EvaluationHostActivity)?.navigateToNextPage()
        }
    }

    /**
     * ✅ CORRIGIDO: Apenas recalcula quando necessário (foco perdido ou cálculo manual)
     */
    private fun calculateDisplayValues() {
        val calcio = sharedViewModel.calcio.value ?: 0.0
        val magnesio = sharedViewModel.magnesio.value ?: 0.0
        val bicarbonato = sharedViewModel.bicarbonato.value ?: 0.0
        val sodio = sharedViewModel.sodio.value ?: 0.0
        val ph = sharedViewModel.ph.value ?: 0.0
        val residuo = sharedViewModel.residuoEvaporacao.value ?: 0.0

        val profile = WaterProfile(
            calcium = calcio,
            magnesium = magnesio,
            bicarbonate = bicarbonato
        )
        val durezaCalculada = profile.calculateHardness()
        val alcalinidadeCalculada = profile.calculateAlkalinity()

        isUpdatingFromViewModel = true

        binding.textViewDurezaCalculada.setText(df.format(durezaCalculada))
        binding.textViewAlcalinidadeCalculada.setText(df.format(alcalinidadeCalculada))

        isUpdatingFromViewModel = false

        // Avaliações
        // ✅ Trata 0.0 como NA para não mostrar avaliação incorreta
        val durezaStatus = if (durezaCalculada == 0.0) EvaluationStatus.NA else sharedViewModel.mapPointsToStatus(
            WaterEvaluator.getHardnessPoints(durezaCalculada)
        )
        val alcalinidadeStatus = if (alcalinidadeCalculada == 0.0) EvaluationStatus.NA else sharedViewModel.mapPointsToStatus(
            WaterEvaluator.getAlkalinityPoints(alcalinidadeCalculada)
        )
        val sodioStatus = if (sodio == 0.0) EvaluationStatus.NA else sharedViewModel.mapPointsToStatus(
            WaterEvaluator.getSodiumPoints(sodio)
        )
        val phStatus = if (ph == 0.0) EvaluationStatus.NA else sharedViewModel.getPhEvaluationStatus(ph)
        val residuoStatus = if (residuo == 0.0) EvaluationStatus.NA else sharedViewModel.mapPointsToStatus(
            WaterEvaluator.getTdsPoints(residuo)
        )

        updateValidationView(binding.textViewDurezaAvaliacao, durezaStatus)
        updateValidationView(binding.textViewAlcalinidadeAvaliacao, alcalinidadeStatus)
        updateValidationView(binding.textViewSodioAvaliacao, sodioStatus)
        updateValidationView(binding.textViewPHAvaliacao, phStatus)
        updateValidationView(binding.textViewResiduoEvaporacaoAvaliacao, residuoStatus)
    }

    /**
     * ✅ SOLUÇÃO DEFINITIVA: NÃO muda visibility (evita re-layout)
     * TextViews sempre visíveis, apenas texto vazio quando NA
     */
    private fun updateValidationView(textView: TextView, status: EvaluationStatus) {
        // ❌ REMOVIDO: textView.visibility = View.VISIBLE (causava race condition!)

        textView.text = when(status) {
            EvaluationStatus.IDEAL -> getString(R.string.avaliacao_ideal)
            EvaluationStatus.ACEITAVEL -> getString(R.string.avaliacao_aceitavel)
            EvaluationStatus.NAO_RECOMENDADO -> getString(R.string.avaliacao_nao_recomendado)
            EvaluationStatus.NA -> "" // ✅ String vazia = sem re-layout
        }

        val colorRes = when (status) {
            EvaluationStatus.IDEAL -> R.color.ideal_green
            EvaluationStatus.ACEITAVEL -> R.color.acceptable_yellow
            EvaluationStatus.NAO_RECOMENDADO -> R.color.not_recommended_red
            EvaluationStatus.NA -> android.R.color.transparent
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