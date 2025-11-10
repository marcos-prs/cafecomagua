package com.marcos.cafecomagua.app.data

import RecipeDao
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.app.model.SavedRecipe

@Database(
    entities = [
        AvaliacaoResultado::class,
        SavedRecipe::class // 1. ADICIONADA A ENTIDADE SavedRecipe
    ],
    version = 2, // 2. VERSÃO INCREMENTADA (de 1 para 2)
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun avaliacaoDao(): AvaliacaoDao
    abstract fun recipeDao(): RecipeDao

    companion object {
        // Volatile garante que a instância seja sempre visível para outros threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // Usa o operador 'elvis' para criar o DB se ele não existir
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cafecomagua_database"
                )
                    // 4. HABILITADO o fallbackToDestructiveMigration.
                    // Isso permite que o Room recrie o banco se o schema
                    // mudar (version++), sem a necessidade de uma migração complexa.
                    // Perfeito para desenvolvimento.
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}