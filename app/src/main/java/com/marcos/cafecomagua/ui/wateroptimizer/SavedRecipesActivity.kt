package com.marcos.cafecomagua.ui.wateroptimizer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.databinding.ActivitySavedRecipesBinding

/**
 * Activity refatorada para exibir 2 tabs:
 * 1. Tab "Receitas" - Lista de receitas salvas
 * 2. Tab "Blend" - Calculadora de mistura de águas
 */
class SavedRecipesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySavedRecipesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySavedRecipesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge
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
        setupTabs()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupTabs() {
        // Configurar ViewPager2 com o adapter
        val pagerAdapter = RecipesPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        // Conectar TabLayout com ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_recipes)
                1 -> getString(R.string.tab_blend)
                else -> ""
            }
        }.attach()
    }

    /**
     * Adapter do ViewPager2 que gerencia as 2 tabs
     */
    private class RecipesPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> RecipesListFragment() // Tab de receitas
                1 -> BlendFragment()        // Tab de blend
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }
}