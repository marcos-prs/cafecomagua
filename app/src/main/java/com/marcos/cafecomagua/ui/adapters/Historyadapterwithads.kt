package com.marcos.cafecomagua.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.R
import com.marcos.cafecomagua.databinding.ItemHistoryBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale
// ⚠️ IMPORT NECESSÁRIO (Presumindo que EvaluationStatus exista no pacote 'model')
import com.marcos.cafecomagua.app.model.EvaluationStatus


/**
 * Adapter para histórico de avaliações com Native Ads inseridos estrategicamente
 */
class HistoryAdapterWithAds(
    private val context: Context,
    private var avaliacoes: List<AvaliacaoResultado>,
    private val onItemClick: (AvaliacaoResultado) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_EVALUATION = 0
        private const val VIEW_TYPE_AD = 1
        private const val AD_FREQUENCY = 5
        private const val NATIVE_AD_UNIT_ID = "ca-app-pub-7526020095328101/XXXXXX"
    }

    private val items = mutableListOf<Any>()
    private val nativeAds = mutableListOf<NativeAd>()

    private val adsRemoved: Boolean by lazy {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("ads_removed", false)
    }

    init {
        setupItems()
        if (!adsRemoved) {
            loadNativeAds()
        }
    }

    fun updateData(newAvaliacoes: List<AvaliacaoResultado>) {
        nativeAds.forEach { it.destroy() }
        nativeAds.clear()

        avaliacoes = newAvaliacoes
        setupItems()

        if (!adsRemoved) {
            loadNativeAds()
        }

        notifyDataSetChanged()
    }

    private fun setupItems() {
        items.clear()

        if (adsRemoved) {
            items.addAll(avaliacoes)
        } else {
            avaliacoes.forEachIndexed { index, avaliacao ->
                items.add(avaliacao)

                if ((index + 1) % AD_FREQUENCY == 0 && index < avaliacoes.size - 1) {
                    items.add(NativeAdPlaceholder)
                }
            }
        }
    }

    private fun loadNativeAds() {
        val numberOfAds = (avaliacoes.size / AD_FREQUENCY).coerceAtLeast(1)

        val adLoader = AdLoader.Builder(context, NATIVE_AD_UNIT_ID)
            .forNativeAd { nativeAd ->
                nativeAds.add(nativeAd)

                if (nativeAds.size <= numberOfAds) {
                    notifyDataSetChanged()
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    // Falha silenciosa
                }
            })
            .build()

        adLoader.loadAds(AdRequest.Builder().build(), numberOfAds)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AvaliacaoResultado -> VIEW_TYPE_EVALUATION
            is NativeAdPlaceholder -> VIEW_TYPE_AD
            else -> VIEW_TYPE_EVALUATION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_EVALUATION -> {
                val binding = ItemHistoryBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                EvaluationViewHolder(binding)
            }
            VIEW_TYPE_AD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_native_ad, parent, false)
                NativeAdViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EvaluationViewHolder -> {
                val avaliacao = items[position] as AvaliacaoResultado
                holder.bind(avaliacao, onItemClick)
            }
            is NativeAdViewHolder -> {
                val adIndex = (position / (AD_FREQUENCY + 1)).coerceAtMost(nativeAds.size - 1)
                if (adIndex >= 0 && adIndex < nativeAds.size) {
                    holder.bind(nativeAds[adIndex])
                }
            }
        }
    }

    override fun getItemCount() = items.size

    // --- EvaluationViewHolder Corrigido ---

    /**
     * ViewHolder para itens de avaliação
     */
    inner class EvaluationViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // ❌ Declaração duplicada do Enum removida daqui (Linha 165 anterior)

        fun bind(avaliacao: AvaliacaoResultado, onClick: (AvaliacaoResultado) -> Unit) {
            val df = DecimalFormat("#.##")

            // 1. Configuração da Data e Hora
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                binding.textViewDataHora.text = sdf.format(avaliacao.dataAvaliacao)
            } catch (e: Exception) {
                binding.textViewDataHora.text = "Data indisponível"
            }

            // 2. Configuração de Nome, Fonte e Pontuação
            binding.textViewAgua.text = avaliacao.nomeAgua
            binding.textViewFonte.text = context.getString(
                R.string.label_historico_fonte,
                avaliacao.fonteAgua
            )
            binding.textViewPontuacao.text = context.getString(
                R.string.label_historico_pontuacao,
                df.format(avaliacao.pontuacaoTotal)
            )

            // ✅ 3. Lógica de Qualidade Simplificada e Corrigida
            // 'avaliacao.qualidadeGeral' já é do tipo EvaluationStatus.
            val qualidadeStatus = avaliacao.qualidadeGeral

            val qualidadeText = when (qualidadeStatus) {
                EvaluationStatus.IDEAL -> context.getString(R.string.avaliacao_ideal)
                EvaluationStatus.ACEITAVEL -> context.getString(R.string.avaliacao_aceitavel)
                EvaluationStatus.NAO_RECOMENDADO -> context.getString(R.string.avaliacao_nao_recomendado)
                else -> {}
            }

            binding.textViewQualidadeGeral.text = context.getString(
                R.string.label_historico_qualidade,
                qualidadeText
            )
            // ----------------------------------------------------

            binding.root.setOnClickListener {
                onClick(avaliacao)
            }
        }
    }

    /**
     * ViewHolder para Native Ads
     */
    inner class NativeAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ... (lógica de bind do AdViewHolder) ...
        fun bind(nativeAd: NativeAd) {
            val adView = itemView.findViewById<NativeAdView>(R.id.native_ad_view)

            adView.findViewById<TextView>(R.id.ad_headline)?.let {
                it.text = nativeAd.headline
                adView.headlineView = it
            }

            adView.findViewById<TextView>(R.id.ad_body)?.let {
                it.text = nativeAd.body
                adView.bodyView = it
            }

            adView.findViewById<Button>(R.id.ad_call_to_action)?.let {
                it.text = nativeAd.callToAction
                adView.callToActionView = it
            }

            adView.findViewById<ImageView>(R.id.ad_icon)?.let {
                nativeAd.icon?.let { icon ->
                    it.setImageDrawable(icon.drawable)
                }
                adView.iconView = it
            }

            adView.findViewById<RatingBar>(R.id.ad_stars)?.let {
                nativeAd.starRating?.let { rating ->
                    it.rating = rating.toFloat()
                    it.visibility = View.VISIBLE
                } ?: run {
                    it.visibility = View.GONE
                }
                adView.starRatingView = it
            }

            adView.setNativeAd(nativeAd)
        }
    }

    /**
     * Libera recursos dos Native Ads
     */
    fun destroy() {
        nativeAds.forEach { it.destroy() }
        nativeAds.clear()
    }

    /**
     * Objeto placeholder para Native Ads
     */
    private object NativeAdPlaceholder
}