package com.marcos.cafecomagua.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible // ✅ Importado para os botões de seta
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.analytics.AnalyticsManager
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.databinding.ActivityOnboardingBinding
import com.marcos.cafecomagua.ui.adapters.OnboardingAdapter
import com.marcos.cafecomagua.ui.home.HomeActivity

/**
 * ✅ REFATORADO: (Ponto de Design)
 * Usa botões de navegação laterais (setas) e remove botões inferiores.
 * ✅ CORRIGIDO: Erros de sintaxe resolvidos (código movido para setupViewPager).
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var onboardingAdapter: OnboardingAdapter
    private val indicators = mutableListOf<ImageView>()

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"

        /**
         * Verifica se o onboarding já foi concluído
         */
        fun isCompleted(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_ONBOARDING_COMPLETED, false)
        }

        /**
         * Marca onboarding como concluído
         */
        fun markAsCompleted(context: Context) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_ONBOARDING_COMPLETED, true)
                .apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
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

        setupViewPager()
        setupListeners()
        setupPageIndicators()
    }

    /**
     * ✅ FUNÇÃO CORRIGIDA
     * O conteúdo abaixo (val slides, adapter, etc.) foi movido para DENTRO
     * desta função, corrigindo todos os erros de "Unresolved reference".
     */
    private fun setupViewPager() {

        // (Ponto 10) Lista de slides atualizada com os novos textos e ícones
        val slides = listOf(
            OnboardingSlide(
                title = getString(R.string.onboarding_title_1),
                message = getString(R.string.onboarding_message_1),
                imageRes = R.drawable.ic_star // Ícone Slide 1
            ),
            OnboardingSlide(
                title = getString(R.string.onboarding_title_2),
                message = getString(R.string.onboarding_message_2),
                imageRes = R.drawable.ic_chemestry // Ícone Slide 2 (Alcalinidade/Dureza)
            ),
            OnboardingSlide(
                title = getString(R.string.onboarding_title_3),
                message = getString(R.string.onboarding_message_3),
                imageRes = R.drawable.ic_water_tds // (Sugestão: ícone para TDS/Sódio)
            ),
            OnboardingSlide(
                title = getString(R.string.onboarding_title_4),
                message = getString(R.string.onboarding_message_4),
                imageRes = R.drawable.ic_water_optimizer, // Ícone Slide 4 (Calculadora)
                isPremiumSlide = true // ✅ ADICIONADO: Nova flag para o slide premium
            )
        )

        // (Este é o código que estava colado fora da função)
        onboardingAdapter = OnboardingAdapter(slides)
        binding.viewPager.adapter = onboardingAdapter

        // Listener para mudança de página
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtonVisibility(position)
                updatePageIndicators(position)
            }
        })

        // Configura botões iniciais
        updateButtonVisibility(0)
    } // ✅ ESTA CHAVE (fechando a função) CORRIGE OS ERROS

    private fun setupPageIndicators() {
        binding.indicatorLayout.removeAllViews()
        indicators.clear()

        val indicatorSize = (8 * resources.displayMetrics.density).toInt()
        val indicatorMargin = (8 * resources.displayMetrics.density).toInt()

        // Cria 4 indicadores
        for (i in 0 until 4) {
            val indicator = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(indicatorSize, indicatorSize).apply {
                    marginStart = if (i > 0) indicatorMargin else 0
                }
                setImageResource(R.drawable.ic_star)
                imageTintList = ContextCompat.getColorStateList(
                    this@OnboardingActivity,
                    if (i == 0) R.color.dourado_elegante else android.R.color.darker_gray
                )
                alpha = if (i == 0) 1f else 0.3f
            }
            binding.indicatorLayout.addView(indicator)
            indicators.add(indicator)
        }
    }

    private fun updatePageIndicators(position: Int) {
        indicators.forEachIndexed { index, indicator ->
            indicator.imageTintList = ContextCompat.getColorStateList(
                this,
                if (index == position) R.color.dourado_elegante else android.R.color.darker_gray
            )
            indicator.alpha = if (index == position) 1f else 0.3f
        }
    }

    /**
     * ✅ REFATORADO: Listeners atualizados para os novos botões de seta.
     */
    private fun setupListeners() {
        // ❌ REMOVIDO: binding.buttonSkip.setOnClickListener

        // Botão Seta Direita (Próximo / Concluir)
        binding.buttonNavNext.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition < onboardingAdapter.itemCount - 1) {
                // Avança para a próxima página
                binding.viewPager.currentItem = currentPosition + 1
                analytics().logEvent(
                    AnalyticsManager.Category.NAVIGATION,
                    "onboarding_next_page",
                    mapOf("page" to (currentPosition + 1).toString())
                )
            } else {
                // Última página - finaliza onboarding
                analytics().logEvent(
                    AnalyticsManager.Category.NAVIGATION,
                    "onboarding_completed"
                )
                finishOnboarding()
            }
        }

        // Botão Seta Esquerda (Voltar)
        binding.buttonNavPrevious.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition > 0) {
                binding.viewPager.currentItem = currentPosition - 1
            }
        }
    }

    /**
     * ✅ REFATORADO: Atualiza a visibilidade das setas.
     */
    private fun updateButtonVisibility(position: Int) {
        val isLastPage = position == onboardingAdapter.itemCount - 1
        val isFirstPage = position == 0

        // Esconde a seta "Voltar" na primeira página
        binding.buttonNavPrevious.isVisible = !isFirstPage

        // (Opcional) Mudar o ícone da seta "Próximo" para "Concluir" na última página
        if (isLastPage) {
            binding.buttonNavNext.setImageResource(R.drawable.ic_check) // (Sugestão: ícone de "check")
        } else {
            binding.buttonNavNext.setImageResource(R.drawable.ic_arrow_forward)
        }
    }

    private fun finishOnboarding() {
        markAsCompleted(this)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}

/**
 * Modelo de dados para um slide de onboarding
 * ✅ ATUALIZADO: Inclui a flag isPremiumSlide
 */
data class OnboardingSlide(
    val title: String,
    val message: String,
    val imageRes: Int,
    val isPremiumSlide: Boolean = false // ✅ NOVA PROPRIEDADE
)