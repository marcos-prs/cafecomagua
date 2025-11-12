package com.marcos.cafecomagua.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * ✨ REFATORADO: Agora inclui os parâmetros originais da água analisada,
 * campo para notas do usuário, VOLUME DE ÁGUA e ÁGUA OTIMIZADA com scores.
 */
@Entity(tableName = "saved_recipes")
data class SavedRecipe(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Identificação da receita
    val recipeName: String,
    val dateSaved: Date,

    // Gotas calculadas para adicionar
    val calciumDrops: Int,
    val magnesiumDrops: Int,
    val sodiumDrops: Int,
    val potassiumDrops: Int,

    // Volume de água para o qual a receita foi calculada
    val waterVolumeMl: Int = 1000, // Volume padrão: 1L (mais fácil para conversões)

    // Parâmetros originais da água analisada
    val originalCalcium: Double,
    val originalMagnesium: Double,
    val originalSodium: Double,
    val originalBicarbonate: Double,
    val originalHardness: Double,
    val originalAlkalinity: Double,
    val originalTds: Double,

    // ✨ NOVO: Parâmetros da água otimizada (após adicionar gotas)
    val optimizedCalcium: Double = originalCalcium,
    val optimizedMagnesium: Double = originalMagnesium,
    val optimizedSodium: Double = originalSodium,
    val optimizedBicarbonate: Double = originalBicarbonate,
    val optimizedHardness: Double = originalHardness,
    val optimizedAlkalinity: Double = originalAlkalinity,
    val optimizedTds: Double = originalTds,

    // ✨ NOVO: Scores (0-100)
    val originalScore: Double = 0.0,
    val optimizedScore: Double = 0.0,
    val improvementPercent: Double = 0.0,

    // Notas/descrição opcional do usuário
    val notes: String? = null
) {
    /**
     * Calcula as gotas ajustadas para um volume diferente
     */
    fun adjustForVolume(targetVolumeMl: Int): SavedRecipe {
        if (targetVolumeMl == waterVolumeMl) return this

        val ratio = targetVolumeMl.toDouble() / waterVolumeMl
        return copy(
            calciumDrops = (calciumDrops * ratio).toInt(),
            magnesiumDrops = (magnesiumDrops * ratio).toInt(),
            sodiumDrops = (sodiumDrops * ratio).toInt(),
            potassiumDrops = (potassiumDrops * ratio).toInt(),
            waterVolumeMl = targetVolumeMl
        )
    }

    /**
     * Formata o volume para exibição
     */
    fun getVolumeFormatted(): String {
        return when {
            waterVolumeMl >= 1000 -> "${waterVolumeMl / 1000}L"
            else -> "${waterVolumeMl}ml"
        }
    }

    /**
     * ✨ NOVO: Retorna lista de parâmetros que foram afetados pela otimização
     */
    fun getAffectedParameters(): List<AffectedParameter> {
        val affected = mutableListOf<AffectedParameter>()

        // Alcalinidade (afetada por Potássio/Bicarbonato)
        if (optimizedAlkalinity != originalAlkalinity) {
            affected.add(AffectedParameter(
                name = "Alcalinidade",
                originalValue = originalAlkalinity,
                optimizedValue = optimizedAlkalinity,
                weight = 5
            ))
        }

        // Dureza (afetada por Cálcio/Magnésio)
        if (optimizedHardness != originalHardness) {
            affected.add(AffectedParameter(
                name = "Dureza",
                originalValue = originalHardness,
                optimizedValue = optimizedHardness,
                weight = 3
            ))
        }

        // Sódio
        if (optimizedSodium != originalSodium) {
            affected.add(AffectedParameter(
                name = "Sódio",
                originalValue = originalSodium,
                optimizedValue = optimizedSodium,
                weight = 1
            ))
        }

        // TDS
        if (optimizedTds != originalTds) {
            affected.add(AffectedParameter(
                name = "TDS",
                originalValue = originalTds,
                optimizedValue = optimizedTds,
                weight = 1
            ))
        }

        return affected.sortedByDescending { it.weight }
    }

    /**
     * ✨ NOVO: Classe para representar um parâmetro afetado
     */
    data class AffectedParameter(
        val name: String,
        val originalValue: Double,
        val optimizedValue: Double,
        val weight: Int
    )
}