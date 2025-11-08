package com.marcos.cafecomagua.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.ui.onboarding.OnboardingSlide

class OnboardingAdapter(
    private val slides: List<OnboardingSlide>
) : RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_slide, parent, false)
        return SlideViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount(): Int = slides.size

    /**
     * ViewHolder para slides de onboarding
     */
    class SlideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageIcon: ImageView = itemView.findViewById(R.id.imageIcon)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textDescription: TextView = itemView.findViewById(R.id.textDescription)

        fun bind(slide: OnboardingSlide) {
            imageIcon.setImageResource(slide.imageRes)
            textTitle.text = slide.title
            textDescription.text = slide.message

            // Se for slide premium, adiciona indicador visual
            if (slide.isPremiumFeature) {
                // Adiciona uma estrela dourada ao título para indicar premium
                textTitle.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_premium,
                    0,
                    0,
                    0
                )
                textTitle.compoundDrawablePadding = 8
            } else {
                textTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
    }
}