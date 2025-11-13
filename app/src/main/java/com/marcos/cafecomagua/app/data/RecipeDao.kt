package com.marcos.cafecomagua.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marcos.cafecomagua.app.model.SavedRecipe
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    /**
     * Insere uma receita única
     * Em caso de conflito, substitui a receita existente
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: SavedRecipe)

    /**
     * Insere múltiplas receitas de uma vez
     * Útil para operações de backup/restore
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<SavedRecipe>)

    /**
     * Retorna todas as receitas ordenadas por data de salvamento (mais recente primeiro)
     * Usa Flow para observar mudanças em tempo real
     */
    @Query("SELECT * FROM saved_recipes ORDER BY dateSaved DESC")
    fun getAllRecipes(): Flow<List<SavedRecipe>>

    /**
     * Retorna receitas limitadas (útil para paginação)
     */
    @Query("SELECT * FROM saved_recipes ORDER BY dateSaved DESC LIMIT :limit")
    fun getRecentRecipes(limit: Int = 10): Flow<List<SavedRecipe>>

    /**
     * Busca uma receita específica por ID
     */
    @Query("SELECT * FROM saved_recipes WHERE id = :recipeId LIMIT 1")
    suspend fun getRecipeById(recipeId: String): SavedRecipe?

    /**
     * Verifica se uma receita existe
     */
    @Query("SELECT EXISTS(SELECT 1 FROM saved_recipes WHERE id = :recipeId LIMIT 1)")
    suspend fun recipeExists(recipeId: String): Boolean

    /**
     * Deleta uma receita específica
     */
    @Delete
    suspend fun delete(recipe: SavedRecipe)

    /**
     * Deleta uma receita por ID
     */
    @Query("DELETE FROM saved_recipes WHERE id = :recipeId")
    suspend fun deleteById(recipeId: String)

    /**
     * Remove todas as receitas
     * Útil para limpar dados ou fazer reset
     */
    @Query("DELETE FROM saved_recipes")
    suspend fun clearAll()

    /**
     * Conta o total de receitas salvas
     * Útil para verificar integridade do banco e exibir estatísticas
     */
    @Query("SELECT COUNT(*) FROM saved_recipes")
    suspend fun getRecipeCount(): Int

    /**
     * Busca receitas por nome (pesquisa parcial)
     */
    /**
     * Busca receitas por nome (pesquisa parcial)
     */
    @Query("SELECT * FROM saved_recipes WHERE recipeName LIKE '%' || :searchQuery || '%' ORDER BY dateSaved DESC")
    fun searchRecipes(searchQuery: String): Flow<List<SavedRecipe>>

    /**
     * Deleta receitas antigas (mais de X dias)
     * Útil para limpeza automática
     */
    @Query("DELETE FROM saved_recipes WHERE dateSaved < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}