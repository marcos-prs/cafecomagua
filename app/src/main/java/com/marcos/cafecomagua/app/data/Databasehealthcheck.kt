package com.marcos.cafecomagua.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Classe utilitária para verificar a saúde e integridade do banco de dados
 * e realizar operações de manutenção quando necessário
 */
object DatabaseHealthCheck {
    private const val TAG = "DatabaseHealthCheck"

    /**
     * Verifica a integridade completa do banco de dados
     * Testa operações de leitura em todas as tabelas
     */
    suspend fun checkDatabaseIntegrity(context: Context): DatabaseHealthStatus {
        return withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(context)

                // Tenta contar registros em cada tabela
                val recipeCount = database.recipeDao().getRecipeCount()
                val avaliacaoCount = database.avaliacaoDao().getAvaliacaoCount()

                Log.d(TAG, "Database integrity check passed - Recipes: $recipeCount, Avaliações: $avaliacaoCount")

                DatabaseHealthStatus.Healthy(
                    recipeCount = recipeCount,
                    avaliacaoCount = avaliacaoCount
                )
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Database integrity check failed - IllegalStateException", e)
                DatabaseHealthStatus.Corrupted(e)
            } catch (e: Exception) {
                Log.e(TAG, "Database integrity check failed - Unknown error", e)
                DatabaseHealthStatus.Error(e)
            }
        }
    }

    /**
     * Tenta recuperar o banco de dados em caso de corrupção
     * Recria o banco se necessário
     */
    suspend fun repairDatabase(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "Attempting to repair database...")
                AppDatabase.recreateDatabase(context)

                // Verifica se a reparação foi bem-sucedida
                val status = checkDatabaseIntegrity(context)
                val isHealthy = status is DatabaseHealthStatus.Healthy

                if (isHealthy) {
                    Log.d(TAG, "Database repaired successfully")
                } else {
                    Log.e(TAG, "Database repair failed")
                }

                isHealthy
            } catch (e: Exception) {
                Log.e(TAG, "Error repairing database", e)
                false
            }
        }
    }

    /**
     * Executa uma operação no banco com proteção contra corrupção
     * Se a operação falhar devido à corrupção, tenta reparar o banco automaticamente
     */
    suspend fun <T> executeWithProtection(
        context: Context,
        operation: suspend () -> T,
        onCorruption: suspend () -> T? = { null }
    ): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                Result.success(operation())
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Database operation failed - attempting repair", e)

                // Tenta reparar o banco
                val repaired = repairDatabase(context)

                if (repaired) {
                    try {
                        // Tenta a operação novamente após reparação
                        Result.success(operation())
                    } catch (retryError: Exception) {
                        Log.e(TAG, "Operation failed even after repair", retryError)

                        // Executa callback de fallback se fornecido
                        val fallbackResult = onCorruption()
                        if (fallbackResult != null) {
                            Result.success(fallbackResult)
                        } else {
                            Result.failure(retryError)
                        }
                    }
                } else {
                    Result.failure(DatabaseCorruptionException("Failed to repair database", e))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during database operation", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Limpa dados antigos do banco para otimizar espaço
     */
    suspend fun cleanOldData(
        context: Context,
        daysToKeep: Int = 90
    ): CleanupResult {
        return withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(context)
                val timestamp = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)

                val recipeCountBefore = database.recipeDao().getRecipeCount()
                val avaliacaoCountBefore = database.avaliacaoDao().getAvaliacaoCount()

                database.recipeDao().deleteOlderThan(timestamp)
                database.avaliacaoDao().deleteOlderThan(timestamp)

                val recipeCountAfter = database.recipeDao().getRecipeCount()
                val avaliacaoCountAfter = database.avaliacaoDao().getAvaliacaoCount()

                val recipesDeleted = recipeCountBefore - recipeCountAfter
                val avaliacoesDeleted = avaliacaoCountBefore - avaliacaoCountAfter

                Log.d(TAG, "Cleanup completed - Recipes deleted: $recipesDeleted, Avaliações deleted: $avaliacoesDeleted")

                CleanupResult.Success(
                    recipesDeleted = recipesDeleted,
                    avaliacoesDeleted = avaliacoesDeleted
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
                CleanupResult.Failure(e)
            }
        }
    }

    /**
     * Realiza backup dos dados antes de operações críticas
     */
    suspend fun createBackup(context: Context): BackupResult {
        return withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getInstance(context)

                // Coleta todos os dados
                val recipes = mutableListOf<com.marcos.cafecomagua.app.model.SavedRecipe>()
                val avaliacoes = mutableListOf<com.marcos.cafecomagua.app.model.AvaliacaoResultado>()

                database.recipeDao().getAllRecipes().collect { recipes.addAll(it) }
                database.avaliacaoDao().getAll().collect { avaliacoes.addAll(it) }

                Log.d(TAG, "Backup created - Recipes: ${recipes.size}, Avaliações: ${avaliacoes.size}")

                BackupResult.Success(
                    recipes = recipes,
                    avaliacoes = avaliacoes
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error creating backup", e)
                BackupResult.Failure(e)
            }
        }
    }
}

/**
 * Status de saúde do banco de dados
 */
sealed class DatabaseHealthStatus {
    data class Healthy(
        val recipeCount: Int,
        val avaliacaoCount: Int
    ) : DatabaseHealthStatus()

    data class Corrupted(val error: Exception) : DatabaseHealthStatus()
    data class Error(val error: Exception) : DatabaseHealthStatus()
}

/**
 * Resultado da operação de limpeza
 */
sealed class CleanupResult {
    data class Success(
        val recipesDeleted: Int,
        val avaliacoesDeleted: Int
    ) : CleanupResult()

    data class Failure(val error: Exception) : CleanupResult()
}

/**
 * Resultado do backup
 */
sealed class BackupResult {
    data class Success(
        val recipes: List<com.marcos.cafecomagua.app.model.SavedRecipe>,
        val avaliacoes: List<com.marcos.cafecomagua.app.model.AvaliacaoResultado>
    ) : BackupResult()

    data class Failure(val error: Exception) : BackupResult()
}

/**
 * Exceção customizada para corrupção de banco de dados
 */
class DatabaseCorruptionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)