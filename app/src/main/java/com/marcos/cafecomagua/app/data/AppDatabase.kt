package com.marcos.cafecomagua.app.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.marcos.cafecomagua.app.model.AvaliacaoResultado
import com.marcos.cafecomagua.app.model.SavedRecipe

@Database(
    entities = [
        AvaliacaoResultado::class,
        SavedRecipe::class
    ],
    version = 3, // Versão incrementada para forçar recriação limpa
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun avaliacaoDao(): AvaliacaoDao
    abstract fun recipeDao(): RecipeDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DATABASE_NAME = "cafecomagua_database"

        // Volatile garante que a instância seja sempre visível para outros threads
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Retorna a instância do banco de dados (Singleton)
         * Com proteções contra corrupção e logs para diagnóstico
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE
                if (instance != null) {
                    instance
                } else {
                    try {
                        val newInstance = Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            DATABASE_NAME
                        )
                            // Recria o banco se o schema mudar (perfeito para desenvolvimento)
                            .fallbackToDestructiveMigration()
                            // Proteção contra corrupção de dados
                            // TRUNCATE é mais seguro em dispositivos com storage limitado
                            .setJournalMode(JournalMode.TRUNCATE)
                            .build()

                        INSTANCE = newInstance
                        Log.d(TAG, "Database instance created successfully")
                        newInstance
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating database instance", e)
                        throw e
                    }
                }
            }
        }

        /**
         * Método seguro para limpar o banco de dados
         * Fecha todas as conexões antes de deletar o arquivo
         */
        fun clearDatabase(context: Context) {
            synchronized(this) {
                try {
                    Log.d(TAG, "Clearing database...")
                    INSTANCE?.close()
                    INSTANCE = null
                    val deleted = context.deleteDatabase(DATABASE_NAME)
                    if (deleted) {
                        Log.d(TAG, "Database cleared successfully")
                    } else {
                        Log.w(TAG, "Database file not found or could not be deleted")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing database", e)
                    throw e
                }
            }
        }

        /**
         * Fecha a instância do banco de dados de forma segura
         */
        fun closeDatabase() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                    INSTANCE = null
                    Log.d(TAG, "Database closed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing database", e)
                }
            }
        }

        /**
         * Verifica se o banco de dados está íntegro
         */
        suspend fun checkDatabaseIntegrity(context: Context): Boolean {
            return try {
                val db = getInstance(context)
                // Tenta operações simples para verificar integridade
                db.recipeDao().getRecipeCount()
                db.avaliacaoDao().getAvaliacaoCount()
                Log.d(TAG, "Database integrity check passed")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Database integrity check failed", e)
                false
            }
        }

        /**
         * Recria o banco de dados em caso de corrupção
         */
        suspend fun recreateDatabase(context: Context) {
            synchronized(this) {
                try {
                    Log.w(TAG, "Recreating database due to corruption...")
                    clearDatabase(context)
                    getInstance(context)
                    Log.d(TAG, "Database recreated successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error recreating database", e)
                    throw e
                }
            }
        }
    }
}