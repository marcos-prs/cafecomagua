package com.marcos.cafecomagua.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.ui.onboarding.OnboardingSlide
/**
 * ✅ REFATORADO (Ponto 10)
 * Este adapter agora suporta múltiplos layouts:
 * - VIEW_TYPE_STANDARD: Para os slides 1-3 (usa R.layout.item_onboarding_slide)
 * - VIEW_TYPE_PREMIUM_SLIDE: Para o slide 4 (usa R.layout.item_onboarding_premium_slide)
 */
class OnboardingAdapter(
    private val slides: List<OnboardingSlide>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() { // Alterado para ViewHolder genérico

    companion object {
        private const val VIEW_TYPE_STANDARD = 0
        private const val VIEW_TYPE_PREMIUM_SLIDE = 1
    }

    /**
     * Define qual layout usar com base na posição.
     * O slide 4 (posição 3) é o único que usa o layout premium.
     */
    override fun getItemViewType(position: Int): Int {
        return if (slides[position].isPremiumSlide) { // ✅ LÓGICA ATUALIZADA
            VIEW_TYPE_PREMIUM_SLIDE
        } else {
            VIEW_TYPE_STANDARD
        }
    }

    /**
     * Infla o layout correto (standard ou premium) com base no viewType.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_PREMIUM_SLIDE) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_premium_slide, parent, false)
            PremiumSlideViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_slide, parent, false)
            StandardSlideViewHolder(view)
        }
    }

    /**
     * Vincula os dados ao ViewHolder correto.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val slide = slides[position]

        if (holder is PremiumSlideViewHolder) {
            holder.bind(slide)
        } else if (holder is StandardSlideViewHolder) {
            holder.bind(slide)
        }
    }

    override fun getItemCount(): Int = slides.size

    /**
     * ViewHolder para os slides padrão (1-3)
     * Layout: R.layout.item_onboarding_slide.xml
     */
    class StandardSlideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageIcon: ImageView = itemView.findViewById(R.id.imageIcon)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textDescription: TextView = itemView.findViewById(R.id.textDescription)

        fun bind(slide: OnboardingSlide) {
            imageIcon.setImageResource(slide.imageRes)
            textTitle.text = slide.title
            textDescription.text = slide.message

            // ❌ Lógica 'isPremiumFeature' removida
        }
    }

    /**
     * ViewHolder para o slide final (4) do Otimizador
     * Layout: R.layout.item_onboarding_premium_slide.xml
     */
    class PremiumSlideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textDescription: TextView = itemView.findViewById(R.id.textDescription)

        fun bind(slide: OnboardingSlide) {
            textTitle.text = slide.title
            textDescription.text = slide.message
        }
    }
}