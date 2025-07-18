# ProGuard Rules for Café com Água - Versão Final de Depuração
#
# O principal problema que estamos resolvendo é um crash na inicialização
# causado pela biblioteca Gson ao usar `new TypeToken<...>() {}`.
# O R8 (ProGuard) remove informações de tipo genéricas para economizar espaço,
# o que quebra o TypeToken. As regras abaixo são agressivas para garantir
# que essas informações e as classes relacionadas sejam 100% preservadas.

# --- REGRAS GERAIS E DE DEPURACAO ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# REGRA CRUCIAL 1: Mantém as "assinaturas genéricas" que o TypeToken precisa.
-keepattributes Signature

# --- REGRAS PARA COMPONENTES DO ANDROID ---
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgent
-keep public class * extends androidx.core.app.CoreComponentFactory
-keep class * extends androidx.viewbinding.ViewBinding

# --- REGRAS ESPECIFICAS DO SEU APP ---
# REGRA CRUCIAL 2: Mantém TODAS as classes, métodos e campos no seu pacote.
# Isso impede que R8 renomeie ou remova `AppDataSource`, `AvaliacaoResultado`, etc.
-keep class com.marcos.cafecomagua.** { *; }

# --- REGRAS PARA BIBLIOTECAS (LIBS) ---

# Google Play Billing
-keepclassmembers class com.android.billingclient.api.** { *; }

# Google Mobile Ads (AdMob)
-keep public class com.google.android.gms.ads.** { public *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.** { *; }
-keepclassmembers class ** { volatile <fields>; }

# Gson
# REGRA CRUCIAL 3: Mantém explicitamente o TypeToken e qualquer classe anônima
# que herde dele. Esta é uma regra de força bruta para o nosso problema.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Regras padrão do Gson para garantir que a serialização funcione.
-keep class com.google.gson.stream.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
