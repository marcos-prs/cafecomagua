package com.marcos.cafecomagua.ui.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.app.model.DropRecommendation
import com.marcos.cafecomagua.databinding.ItemDropRecommendationBinding
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
                val context = root.context

                // Nome da solução mineral
                textSolutionName.text = recommendation.solution.name
                textSolutionFormula.text = recommendation.solution.formula

                // Número de gotas recomendadas
                textDropsNeeded.text = context.getString(
                    R.string.drops_format,
                    recommendation.dropsNeeded
                )

                // PPM que será adicionado
                textPpmAdded.text = context.getString(
                    R.string.ppm_added_format,
                    df.format(recommendation.ppmAdded)
                )

                // Valor final após adicionar
                textFinalValue.text = context.getString(
                    R.string.final_value_format,
                    df.format(recommendation.finalPpm)
                )

                // Badge de status (Ideal/Aceitável/Precisa Ajuste)
                if (recommendation.isOptimal) {
                    // ✅ CORRIGIDO: Usa a string padronizada
                    chipStatus.text = context.getString(R.string.avaliacao_ideal)
                    // ✅ CORRIGIDO: Usa atributo do tema
                    val idealColor = MaterialColors.getColor(context, R.attr.colorStatIdeal, "default_ideal_color")
                    chipStatus.setChipBackgroundColor(ColorStateList.valueOf(idealColor))
                } else {
                    // ✅ CORRIGIDO: Usa a string padronizada
                    chipStatus.text = context.getString(R.string.avaliacao_aceitavel)
                    // ✅ CORRIGIDO: Usa atributo do tema
                    val acceptableColor = MaterialColors.getColor(context, R.attr.colorStatAcceptable, "default_acceptable_color")
                    chipStatus.setChipBackgroundColor(ColorStateList.valueOf(acceptableColor))
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