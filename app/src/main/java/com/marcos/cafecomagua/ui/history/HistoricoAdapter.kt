package com.marcos.cafecomagua.ui.history

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
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.R
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.Context

class HistoricoAdapter(
    private val context: Context, // Adicionado como propriedade
    avaliacoes: List<AvaliacaoResultado>,
    private val adUnitId: String, // Adicionado como propriedade
    private val onItemClick: (AvaliacaoResultado) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Constantes para os tipos de ViewHolder
    private val ITEM_VIEW_TYPE_AVALIACAO = 0
    private val ITEM_VIEW_TYPE_AD = 1
    private val AD_INTERVAL = 5 // Inserir anúncio a cada 5 cards

    private val combinedList: MutableList<Any> = mutableListOf()
    private val nativeAds: MutableMap<Int, NativeAd> = mutableMapOf() // Cache de ads
    // REMOVER a declaração fixa de adUnitId aqui, pois agora é um parâmetro
    // private val adUnitId = "ca-app-pub-3940256099942544/2247599000"

    init {
        // Inicializa a lista combinada com dados e placeholders de anúncios
        populateCombinedList(avaliacoes)
        // Inicializa o Mobile Ads uma vez no Adapter, usando o contexto passado
        MobileAds.initialize(context) {}
    }

    private fun populateCombinedList(avaliacoes: List<AvaliacaoResultado>) {
        combinedList.clear()
        for (i in avaliacoes.indices) {
            combinedList.add(avaliacoes[i]) // Adiciona o item de avaliação

            // Adiciona um placeholder de anúncio a cada 5 itens (mas não depois do último)
            if ((i + 1) % AD_INTERVAL == 0 && i < avaliacoes.size - 1) {
                combinedList.add(Any()) // Placeholder para o Ad Nativo
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (combinedList[position]) {
            is AvaliacaoResultado -> ITEM_VIEW_TYPE_AVALIACAO
            else -> ITEM_VIEW_TYPE_AD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ITEM_VIEW_TYPE_AVALIACAO -> {
                val view = inflater.inflate(R.layout.item_history, parent, false)
                AvaliacaoViewHolder(view)
            }
            ITEM_VIEW_TYPE_AD -> {
                // Aqui usamos o layout `ad_native_template` fornecido
                val view = inflater.inflate(R.layout.item_native_ad, parent, false)
                AdViewHolder(view as NativeAdView)
            }
            else -> throw IllegalArgumentException("ViewType desconhecido: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            ITEM_VIEW_TYPE_AVALIACAO -> {
                val avaliacao = combinedList[position] as AvaliacaoResultado
                (holder as AvaliacaoViewHolder).bind(avaliacao)
                holder.itemView.setOnClickListener { onItemClick(avaliacao) }
            }
            ITEM_VIEW_TYPE_AD -> {
                (holder as AdViewHolder).bindAd(position)
            }
        }
    }

    override fun getItemCount(): Int = combinedList.size

    // --- ViewHolders ---

    inner class AvaliacaoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewDataHora: TextView = itemView.findViewById(R.id.textViewDataHora)
        private val textViewAgua: TextView = itemView.findViewById(R.id.textViewAgua)
        private val textViewFonte: TextView = itemView.findViewById(R.id.textViewFonte)
        private val textViewPontuacao: TextView = itemView.findViewById(R.id.textViewPontuacao)
        private val textViewQualidadeGeral: TextView = itemView.findViewById(R.id.textViewQualidadeGeral)

        fun bind(avaliacao: AvaliacaoResultado) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            textViewDataHora.text = dateFormat.format(avaliacao.dataAvaliacao)

            val context = itemView.context
            textViewAgua.text = context.getString(R.string.label_historico_agua, avaliacao.nomeAgua)
            textViewFonte.text = context.getString(R.string.label_historico_fonte, avaliacao.fonteAgua)
            textViewPontuacao.text = context.getString(R.string.label_historico_pontuacao, String.format(Locale.US, "%.1f", avaliacao.pontuacaoTotal))
            textViewQualidadeGeral.text = context.getString(R.string.label_historico_qualidade, avaliacao.qualidadeGeral)
        }
    }

    inner class AdViewHolder(val adView: NativeAdView) : RecyclerView.ViewHolder(adView) {
        private val headlineView: TextView = adView.findViewById(R.id.ad_headline)
        private val bodyView: TextView = adView.findViewById(R.id.ad_body)
        private val iconView: ImageView = adView.findViewById(R.id.ad_icon)
        private val callToActionView: Button = adView.findViewById(R.id.ad_call_to_action)
        private val starRatingView: RatingBar = adView.findViewById(R.id.ad_stars)

        fun bindAd(position: Int) {
            val ad = nativeAds[position]
            if (ad != null) {
                // Se o Ad estiver em cache, exibe
                populateNativeAdView(ad, adView)
            } else {
                // Se não estiver em cache, carrega um novo
                loadNativeAd(position)
            }
        }

        private fun loadNativeAd(position: Int) {
            MobileAds.initialize(adView.context) {}
            val adLoader = AdLoader.Builder(adView.context, adUnitId)
                .forNativeAd { nativeAd ->
                    // Armazena em cache e exibe
                    nativeAds[position] = nativeAd
                    if (absoluteAdapterPosition == position) { // Garante que o holder não foi reciclado
                        populateNativeAdView(nativeAd, adView)
                    }
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        // Se falhar, remove o placeholder para não mostrar um espaço vazio
                        // NOTA: Para listas maiores, é melhor não remover e tentar carregar no scroll
                    }
                })
                .build()

            adLoader.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
        }
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        // Mapeia os views para o NativeAdView
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.iconView = adView.findViewById(R.id.ad_icon)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.starRatingView = adView.findViewById(R.id.ad_stars)

        // Preenche os dados
        (adView.headlineView as TextView).text = nativeAd.headline
        (adView.bodyView as TextView).text = nativeAd.body

        if (nativeAd.icon != null) {
            (adView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        } else {
            adView.iconView?.visibility = View.GONE
        }

        if (nativeAd.callToAction != null) {
            (adView.callToActionView as Button).text = nativeAd.callToAction
            adView.callToActionView?.visibility = View.VISIBLE
        } else {
            adView.callToActionView?.visibility = View.GONE
        }

        if (nativeAd.starRating != null) {
            (adView.starRatingView as RatingBar).rating = nativeAd.starRating!!.toFloat()
            adView.starRatingView?.visibility = View.VISIBLE
        } else {
            adView.starRatingView?.visibility = View.GONE
        }

        // Registra o NativeAd para processar os cliques e impressões
        adView.setNativeAd(nativeAd)
    }

    // Método para liberar a memória dos ads quando o Adapter for destruído
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        nativeAds.values.forEach { it.destroy() }
        nativeAds.clear()
    }
}