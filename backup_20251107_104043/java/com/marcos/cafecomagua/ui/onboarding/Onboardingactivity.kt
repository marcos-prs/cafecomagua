package com.marcos.cafecomagua.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.adapters.OnboardingAdapter
import com.marcos.cafecomagua.app.analytics.AnalyticsManager
import com.marcos.cafecomagua.app.analytics.analytics
import com.marcos.cafecomagua.databinding.ActivityOnboardingBinding
import com.marcos.cafecomagua.ui.home.HomeActivity
import com.marcos.cafecomagua.app.analytics.Event
import com.marcos.cafecomagua.app.analytics.AnalyticsManager.Event

/**
 * Activity de Onboarding - mostra tutorial de 3 passos na primeira vez
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var onboardingAdapter: OnboardingAdapter

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
    }

    private fun setupViewPager() {
        val slides = listOf(
            OnboardingSlide(
                title = getString(R.string.onboarding_title_1),
                message = getString(R.string.onboarding_message_1),
                imageRes = R.drawable.onboarding_welcome
            ),
            OnboardingSlide(
                title = getString(R.string.onboarding_title_2),
                message = getString(R.string.onboarding_message_2),
                imageRes = R.drawable.onboarding_sca
            ),
            OnboardingSlide(
                title = getString(R.string.onboarding_title_3),
                message = getString(R.string.onboarding_message_3),
                imageRes = R.drawable.onboarding_premium
            )
        )

        onboardingAdapter = OnboardingAdapter(slides)
        binding.viewPager.adapter = onboardingAdapter

        // Conecta TabLayout com ViewPager
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()

        // Listener para mudança de página
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtonVisibility(position)
            }
        })

        // Configura botões iniciais
        updateButtonVisibility(0)
    }

    private fun setupListeners() {
        binding.buttonSkip.setOnClickListener {
            analytics().logEvent(
                Category.NAVIGATION,
                "onboarding_skipped"
            )
            finishOnboarding()
        }

        binding.buttonNext.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition < onboardingAdapter.itemCount - 1) {
                binding.viewPager.currentItem = currentPosition + 1
            }
        }

        binding.buttonGetStarted.setOnClickListener {
            analytics().logEvent(
                Category.NAVIGATION,
                "onboarding_completed"
            )
            finishOnboarding()
        }
    }

    private fun updateButtonVisibility(position: Int) {
        val isLastPage = position == onboardingAdapter.itemCount - 1

        if (isLastPage) {
            binding.buttonSkip.visibility = View.GONE
            binding.buttonNext.visibility = View.GONE
            binding.buttonGetStarted.visibility = View.VISIBLE
        } else {
            binding.buttonSkip.visibility = View.VISIBLE
            binding.buttonNext.visibility = View.VISIBLE
            binding.buttonGetStarted.visibility = View.GONE
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
 */
data class OnboardingSlide(
    val title: String,
    val message: String,
    val imageRes: Int
)