package com.marcos.cafecomagua

import java.io.Serializable
import java.util.Date

data class AvaliacaoResultado(
    val dataAvaliacao: Date,
    val nomeAgua: String,
    val fonteAgua: String,
    val pontuacaoTotal: Double,
    val qualidadeGeral: String,
    val ph: Double,
    val avaliacaoPh: String,
    val dureza: Double,
    val avaliacaoDureza: String,
    val alcalinidade: Double,
    val avaliacaoAlcalinidade: String,
    val sodio: Double,
    val avaliacaoSodio: String,
    val residuoEvaporacao: Double,
    val avaliacaoResiduoEvaporacao: String
) : Serializable