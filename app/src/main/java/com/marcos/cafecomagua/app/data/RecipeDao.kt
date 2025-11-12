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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: SavedRecipe)

    @Query("SELECT * FROM saved_recipes ORDER BY dateSaved DESC")
    fun getAllRecipes(): Flow<List<SavedRecipe>>

    @Delete
    suspend fun delete(recipe: SavedRecipe)

    // --- ADICIONAR ESTES MÉTODOS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<SavedRecipe>)

    @Query("DELETE FROM saved_recipes")
    suspend fun clearAll()
    // --- FIM DA ADIÇÃO ---
}