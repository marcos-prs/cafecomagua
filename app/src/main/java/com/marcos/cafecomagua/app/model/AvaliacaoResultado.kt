package com.marcos.cafecomagua.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.Date

@Entity(tableName = "avaliacoes")
data class AvaliacaoResultado(

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,

    val dataAvaliacao: Date,
    val nomeAgua: String,
    val fonteAgua: String,
    val pontuacaoTotal: Double,
    val qualidadeGeral: EvaluationStatus,
    val ph: Double,
    val avaliacaoPh: EvaluationStatus,
    val dureza: Double,
    val avaliacaoDureza: EvaluationStatus,
    val alcalinidade: Double,
    val avaliacaoAlcalinidade: EvaluationStatus,
    val sodio: Double,
    val avaliacaoSodio: EvaluationStatus,
    val residuoEvaporacao: Double,
    val avaliacaoResiduoEvaporacao: EvaluationStatus,
    val calcio: Double,
    val magnesio: Double,
    val bicarbonato: Double
) : Serializable