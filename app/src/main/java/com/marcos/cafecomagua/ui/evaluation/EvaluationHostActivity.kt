package com.marcos.cafecomagua.ui.evaluation

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels // ✅ Import para o ViewModel
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible // ✅ Import para visibilidade
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2 // ✅ Import para o Callback
import com.marcos.cafecomagua.databinding.ActivityEvaluationHostBinding

/**
 * ✅ REFATORADO:
 * - Removeu o 'TabLayout' (pontos)
 * - Adicionou 'buttonNavPrevious' e 'buttonNavNext'
 * - Habilitou o swipe
 * - Calcula o resultado ANTES de navegar para o ResultsFragment
 */
class EvaluationHostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEvaluationHostBinding

    // Obter o ViewModel (ele sobreviverá à Activity)
    private val sharedViewModel: EvaluationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityEvaluationHostBinding.inflate(layoutInflater)
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

        // --- Configuração do ViewPager ---
        val adapter = EvaluationPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // ✅ HABILITA O SWIPE (Ponto 9)
        binding.viewPager.isUserInputEnabled = true

        // ❌ Lógica do 'TabLayoutMediator' removida

        // --- Configuração dos Botões de Navegação ---
        setupNavigation(adapter.itemCount)
    }

    private fun setupNavigation(itemCount: Int) {
        // Listener para os cliques nos botões
        binding.buttonNavNext.setOnClickListener {
            navigateToNextPage()
        }
        binding.buttonNavPrevious.setOnClickListener {
            navigateToPreviousPage()
        }

        // Listener para o swipe (para atualizar os botões)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateArrowVisibility(position, itemCount)

                // ✅ LÓGICA CHAVE:
                // Se estamos prestes a ver a última página (Resultados),
                // mandamos o ViewModel calcular o resultado.
                if (position == itemCount - 1) { // (Se a posição é 2)
                    sharedViewModel.calcularResultado()
                }
            }
        })

        // Define o estado inicial dos botões
        updateArrowVisibility(0, itemCount)
    }

    private fun updateArrowVisibility(position: Int, itemCount: Int) {
        binding.buttonNavPrevious.isVisible = position > 0
        binding.buttonNavNext.isVisible = position < itemCount - 1
    }

    fun navigateToNextPage() {
        val nextItem = binding.viewPager.currentItem + 1
        if (nextItem < (binding.viewPager.adapter?.itemCount ?: 0)) {
            binding.viewPager.currentItem = nextItem
        }
    }

    fun navigateToPreviousPage() {
        val prevItem = binding.viewPager.currentItem - 1
        if (prevItem >= 0) {
            binding.viewPager.currentItem = prevItem
        }
    }

    // (Adapter interno permanece o mesmo)
    private inner class EvaluationPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> WaterInputFragment()
                1 -> ParametersFragment()
                2 -> ResultsFragment()
                else -> throw IllegalStateException("Posição de Fragment inválida: $position")
            }
        }
    }
}