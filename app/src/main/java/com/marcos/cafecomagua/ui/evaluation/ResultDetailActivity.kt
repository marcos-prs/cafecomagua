package com.marcos.cafecomagua.ui.evaluation

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.model.AvaliacaoResultado

/**
 * Hospeda o ResultsFragment para exibir um item salvo do histórico.
 */
class ResultDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result_detail)

        if (savedInstanceState == null) {
            // 1. Obter a Avaliação do Intent
            val avaliacao: AvaliacaoResultado? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("avaliacao", AvaliacaoResultado::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("avaliacao") as? AvaliacaoResultado
            }

            if (avaliacao != null) {
                // 2. Pré-popular o ViewModel com os dados
                val viewModel: EvaluationViewModel by viewModels()
                viewModel.popularComDados(avaliacao) // (Precisamos adicionar esta função)

                // 3. Adicionar o ResultsFragment
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ResultsFragment())
                    .commitNow()
            }
        }
    }
}