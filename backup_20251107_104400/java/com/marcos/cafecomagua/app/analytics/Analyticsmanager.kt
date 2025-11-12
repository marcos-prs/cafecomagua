package com.marcos.cafecomagua.app.analytics

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sistema simples de analytics para rastrear eventos importantes
 * Armazena localmente e pode ser expandido para enviar para servidor
 */
class AnalyticsManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: AnalyticsManager? = null
        private const val PREFS_NAME = "app_analytics"
        private const val TAG = "Analytics"

        fun getInstance(context: Context): AnalyticsManager {
            return instance ?: synchronized(this) {
                instance ?: AnalyticsManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        // Categorias de eventos
        object Category {
            const val USER_ACTION = "user_action"
            const val NAVIGATION = "navigation"
            const val EVALUATION = "evaluation"
            const val MONETIZATION = "monetization"
            const val PREMIUM = "premium"
        }

        // Eventos específicos
        object Event {
            // Navegação
            const val APP_OPENED = "app_opened"
            const val SCREEN_VIEWED = "screen_viewed"

            // Avaliações
            const val EVALUATION_STARTED = "evaluation_started"
            const val EVALUATION_COMPLETED = "evaluation_completed"
            const val EVALUATION_SAVED = "evaluation_saved"
            const val HISTORY_VIEWED = "history_viewed"

            // OCR
            const val OCR_ATTEMPTED = "ocr_attempted"
            const val OCR_SUCCESS = "ocr_success"
            const val OCR_FAILED = "ocr_failed"

            // Premium
            const val PREMIUM_FEATURE_ATTEMPTED = "premium_feature_attempted"
            const val WATER_OPTIMIZER_OPENED = "water_optimizer_opened"
            const val RECIPE_SAVED = "recipe_saved"
            const val RECIPE_SHARED = "recipe_shared"

            // Monetização
            const val SUBSCRIPTION_SCREEN_VIEWED = "subscription_screen_viewed"
            const val SUBSCRIPTION_BUTTON_CLICKED = "subscription_button_clicked"
            const val DONATION_BUTTON_CLICKED = "donation_button_clicked"
            const val PURCHASE_COMPLETED = "purchase_completed"
            const val PURCHASE_FAILED = "purchase_failed"

            // Anúncios
            const val AD_LOADED = "ad_loaded"
            const val AD_SHOWN = "ad_shown"
            const val AD_CLICKED = "ad_clicked"
            const val AD_FAILED = "ad_failed"
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Registra um evento
     */
    fun logEvent(
        category: String,
        event: String,
        properties: Map<String, Any> = emptyMap()
    ) {
        try {
            val eventData = JSONObject().apply {
                put("category", category)
                put("event", event)
                put("timestamp", dateFormat.format(Date()))
                put("properties", JSONObject(properties))
            }

            Log.d(TAG, "Event logged: $eventData")

            // Incrementa contadores
            incrementEventCounter(event)

            // Salva últimos eventos (mantém últimos 100)
            saveRecentEvent(eventData.toString())

            // Aqui você pode expandir para enviar para Firebase, Amplitude, etc.

        } catch (e: Exception) {
            Log.e(TAG, "Error logging event", e)
        }
    }

    /**
     * Incrementa contador de um evento específico
     */
    private fun incrementEventCounter(event: String) {
        val currentCount = prefs.getInt("count_$event", 0)
        prefs.edit().putInt("count_$event", currentCount + 1).apply()
    }

    /**
     * Obtém contagem de um evento
     */
    fun getEventCount(event: String): Int {
        return prefs.getInt("count_$event", 0)
    }

    /**
     * Salva evento recente para análise posterior
     */
    private fun saveRecentEvent(eventJson: String) {
        val recentEvents = getRecentEvents().toMutableList()
        recentEvents.add(0, eventJson)

        // Mantém apenas os últimos 100 eventos
        if (recentEvents.size > 100) {
            recentEvents.removeAt(recentEvents.lastIndex)
        }

        val eventsString = recentEvents.joinToString(separator = "|||")
        prefs.edit().putString("recent_events", eventsString).apply()
    }

    /**
     * Recupera eventos recentes
     */
    fun getRecentEvents(): List<String> {
        val eventsString = prefs.getString("recent_events", "") ?: ""
        return if (eventsString.isNotEmpty()) {
            eventsString.split("|||")
        } else {
            emptyList()
        }
    }

    /**
     * Registra propriedade do usuário
     */
    fun setUserProperty(key: String, value: String) {
        prefs.edit().putString("user_$key", value).apply()
        Log.d(TAG, "User property set: $key = $value")
    }

    /**
     * Obtém propriedade do usuário
     */
    fun getUserProperty(key: String): String? {
        return prefs.getString("user_$key", null)
    }

    /**
     * Registra sessão do app
     */
    fun logSession() {
        val sessionCount = prefs.getInt("session_count", 0)
        prefs.edit().apply {
            putInt("session_count", sessionCount + 1)
            putLong("last_session_time", System.currentTimeMillis())
            apply()
        }

        logEvent(
            Category.USER_ACTION,
            Event.APP_OPENED,
            mapOf("session_number" to sessionCount + 1)
        )
    }

    /**
     * Obtém estatísticas gerais
     */
    fun getGeneralStats(): Map<String, Any> {
        return mapOf(
            "total_sessions" to prefs.getInt("session_count", 0),
            "evaluations_completed" to getEventCount(Event.EVALUATION_COMPLETED),
            "evaluations_saved" to getEventCount(Event.EVALUATION_SAVED),
            "ocr_attempts" to getEventCount(Event.OCR_ATTEMPTED),
            "ocr_success_rate" to calculateOCRSuccessRate(),
            "premium_feature_attempts" to getEventCount(Event.PREMIUM_FEATURE_ATTEMPTED),
            "subscription_views" to getEventCount(Event.SUBSCRIPTION_SCREEN_VIEWED)
        )
    }

    /**
     * Calcula taxa de sucesso do OCR
     */
    private fun calculateOCRSuccessRate(): Double {
        val attempts = getEventCount(Event.OCR_ATTEMPTED)
        if (attempts == 0) return 0.0
        val successes = getEventCount(Event.OCR_SUCCESS)
        return (successes.toDouble() / attempts.toDouble()) * 100.0
    }

    /**
     * Limpa dados de analytics (útil para debugging)
     */
    fun clearAllData() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All analytics data cleared")
    }

    /**
     * Exporta dados para debug
     */
    fun exportDataForDebug(): String {
        val stats = getGeneralStats()
        val recentEvents = getRecentEvents().take(10) // Últimos 10 eventos

        return buildString {
            appendLine("=== ANALYTICS REPORT ===")
            appendLine("\nGeneral Stats:")
            stats.forEach { (key, value) ->
                appendLine("  $key: $value")
            }
            appendLine("\nRecent Events (last 10):")
            recentEvents.forEachIndexed { index, event ->
                appendLine("  ${index + 1}. $event")
            }
        }
    }
}

/**
 * Extension functions para facilitar o uso
 */
fun Context.analytics(): AnalyticsManager {
    return AnalyticsManager.getInstance(this)
}