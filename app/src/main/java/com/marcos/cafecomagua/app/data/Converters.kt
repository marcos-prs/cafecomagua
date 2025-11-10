package com.marcos.cafecomagua.app.data

import androidx.room.TypeConverter
import java.util.Date
import com.marcos.cafecomagua.app.model.EvaluationStatus

/**
 * Converte tipos complexos (como Date) para tipos primitivos
 * que o Room possa entender (como Long).
 *
 * ✅ CORRIGIDO: Alterado de 'object' para 'class' para
 * consertar o bug 'unexpected jvm signature V' do KSP.
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun toEvaluationStatus(value: String?): EvaluationStatus? {
        return value?.let { EvaluationStatus.valueOf(it) }
    }

    @TypeConverter
    fun fromEvaluationStatus(status: EvaluationStatus?): String? {
        return status?.name
    }
}