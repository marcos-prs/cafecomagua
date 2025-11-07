package com.marcos.cafecomagua.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.R
import java.text.SimpleDateFormat
import java.util.Locale

class HistoricoAdapter(
    private val avaliacoes: List<AvaliacaoResultado>,
    private val onItemClick: (AvaliacaoResultado) -> Unit
) : RecyclerView.Adapter<HistoricoAdapter.AvaliacaoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvaliacaoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return AvaliacaoViewHolder(view)
    }

    override fun onBindViewHolder(holder: AvaliacaoViewHolder, position: Int) {
        val avaliacao = avaliacoes[position]
        holder.bind(avaliacao)
        holder.itemView.setOnClickListener { onItemClick(avaliacao) }
    }

    override fun getItemCount(): Int = avaliacoes.size

    inner class AvaliacaoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewDataHora: TextView = itemView.findViewById(R.id.textViewDataHora)
        private val textViewAgua: TextView = itemView.findViewById(R.id.textViewAgua)
        private val textViewFonte: TextView = itemView.findViewById(R.id.textViewFonte)
        private val textViewPontuacao: TextView = itemView.findViewById(R.id.textViewPontuacao)
        private val textViewQualidadeGeral: TextView = itemView.findViewById(R.id.textViewQualidadeGeral)

        fun bind(avaliacao: AvaliacaoResultado) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            textViewDataHora.text = dateFormat.format(avaliacao.dataAvaliacao)

            // AÇÃO: Textos com parâmetros extraídos para strings.xml
            val context = itemView.context
            textViewAgua.text = context.getString(R.string.label_historico_agua, avaliacao.nomeAgua)
            textViewFonte.text = context.getString(R.string.label_historico_fonte, avaliacao.fonteAgua)
            textViewPontuacao.text = context.getString(R.string.label_historico_pontuacao, String.format(Locale.US, "%.1f", avaliacao.pontuacaoTotal))
            textViewQualidadeGeral.text = context.getString(R.string.label_historico_qualidade, avaliacao.qualidadeGeral)
        }
    }
}