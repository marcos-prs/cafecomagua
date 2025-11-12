package com.marcos.cafecomagua.app.model

/**
 * Contêiner de dados para o backup unificado (JSON).
 * Inclui avaliações (histórico) e receitas (otimizador).
 */
data class AppBackup(
    val backupVersion: Int = 1,
    val backupDate: Long = System.currentTimeMillis(),
    val evaluations: List<AvaliacaoResultado>,
    val recipes: List<SavedRecipe>
)