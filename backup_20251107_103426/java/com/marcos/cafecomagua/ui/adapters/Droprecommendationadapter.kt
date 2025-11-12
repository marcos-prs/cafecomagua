package com.marcos.cafecomagua.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.databinding.ItemDropRecommendationBinding
import com.marcos.cafecomagua.app.model.DropRecommendation
import java.text.DecimalFormat

/**
 * Adapter para exibir recomendações de gotas na calculadora de água
 */
class DropRecommendationAdapter(
    private val recommendations: List<DropRecommendation>,
    private val onInfoClick: (DropRecommendation) -> Unit
) : RecyclerView.Adapter<DropRecommendationAdapter.ViewHolder>() {

    private val df = DecimalFormat("#.##")

    inner class ViewHolder(
        private val binding: ItemDropRecommendationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recommendation: DropRecommendation) {
            binding.apply {
                // Nome da solução mineral
                textSolutionName.text = recommendation.solution.name
                textSolutionFormula.text = recommendation.solution.formula

                // Número de gotas recomendadas
                textDropsNeeded.text = root.context.getString(
                    R.string.drops_format,
                    recommendation.dropsNeeded
                )

                // PPM que será adicionado
                textPpmAdded.text = root.context.getString(
                    R.string.ppm_added_format,
                    df.format(recommendation.ppmAdded)
                )

                // Valor final após adicionar
                textFinalValue.text = root.context.getString(
                    R.string.final_value_format,
                    df.format(recommendation.finalPpm)
                )

                // Badge de status (Ideal/Aceitável/Precisa Ajuste)
                if (recommendation.isOptimal) {
                    chipStatus.text = root.context.getString(R.string.status_optimal)
                    chipStatus.setChipBackgroundColorResource(R.color.ideal_green)
                } else {
                    chipStatus.text = root.context.getString(R.string.status_needs_adjustment)
                    chipStatus.setChipBackgroundColorResource(R.color.acceptable_yellow)
                }

                // Botão de informação
                buttonInfo.setOnClickListener {
                    onInfoClick(recommendation)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDropRecommendationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(recommendations[position])
    }

    override fun getItemCount() = recommendations.size
}