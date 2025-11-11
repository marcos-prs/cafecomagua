package com.marcos.cafecomagua.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.databinding.ActivityOnboardingBinding
import com.marcos.cafecomagua.ui.adapters.OnboardingAdapter
import com.marcos.cafecomagua.ui.home.HomeActivity
import com.marcos.cafecomagua.app.analytics.Category

/**
 * ✅ REFATORADO:
 * - Remove botões de navegação laterais (setas)
 * - Mantém navegação por swipe
 * - Adiciona botão "Pular" que aparece em todos os slides
 * - Muda texto para "CONCLUIR" no último slide
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
     * Configuração do ViewPager com os slides
     */
    private fun setupViewPager() {
        // Lista de slides atualizada
        val slides = listOf(
            OnboardingSlide(
                title = getString(R.string.onboarding_title_1),
                message = getString(R.string.onboarding_message_1),
                imageRes = R.drawable.ic_star
            ),
            OnboardingSlide(
                title = getString(R.string.onboarding_title_2),
                message = getString(R.string.onboarding_message_2),
                imageRes = R.drawable.ic_chemestry
            ),
            OnboardingSlide(
                title = getString(R.string.onboarding_title_3),
                message = getString(R.string.onboarding_message_3),
                imageRes = R.drawable.ic_water_tds
            ),
            OnboardingSlide(
                title = getString(R.string.onboarding_title_4),
                message = getString(R.string.onboarding_message_4),
                imageRes = R.drawable.ic_water_optimizer,
                isPremiumSlide = true
            )
        )

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

        // Configura botão inicial
        updateButtonVisibility(0)
    }

    /**
     * Configuração dos indicadores de página (estrelinhas)
     */
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

    /**
     * Atualiza os indicadores de página
     */
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
     * ✅ REFATORADO: Listener do botão Pular/Concluir
     */
    private fun setupListeners() {
        binding.buttonSkip.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            val isLastPage = currentPosition == onboardingAdapter.itemCount - 1

            if (isLastPage) {
                // Última página - finaliza onboarding
                analytics().logEvent(
                    Category.NAVIGATION,
                    "onboarding_completed"
                )
                finishOnboarding()
            } else {
                // Pula para a última página
                analytics().logEvent(
                    Category.NAVIGATION,
                    "onboarding_skipped",
                    mapOf("from_page" to currentPosition.toString())
                )
                binding.viewPager.currentItem = onboardingAdapter.itemCount - 1
            }
        }
    }

    /**
     * ✅ REFATORADO: Atualiza o texto e visibilidade do botão
     * Opção 1: Botão visível em todas as páginas (PULAR → CONCLUIR)
     * Opção 2: Botão visível apenas na última página
     */
    private fun updateButtonVisibility(position: Int) {
        val isLastPage = position == onboardingAdapter.itemCount - 1

        // OPÇÃO 1: Botão sempre visível (comentar OPÇÃO 2 abaixo)
        if (isLastPage) {
            binding.buttonSkip.text = getString(R.string.onboarding_finish) // "CONCLUIR"
        } else {
            binding.buttonSkip.text = getString(R.string.onboarding_skip) // "PULAR"
        }
        binding.buttonSkip.isVisible = true

        // OPÇÃO 2: Botão visível apenas na última página (descomentar para usar)
        // binding.buttonSkip.text = getString(R.string.onboarding_finish) // "CONCLUIR"
        // binding.buttonSkip.isVisible = isLastPage
    }

    /**
     * Finaliza o onboarding e vai para a Home
     */
    private fun finishOnboarding() {
        markAsCompleted(this)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}

/**
 * Modelo de dados para um slide de onboarding
 */
data class OnboardingSlide(
    val title: String,
    val message: String,
    val imageRes: Int,
    val isPremiumSlide: Boolean = false
)