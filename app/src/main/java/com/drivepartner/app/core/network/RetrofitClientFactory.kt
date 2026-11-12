package com.drivepartner.app.core.network

import android.util.Log
import com.drivepartner.app.BuildConfig
import com.drivepartner.app.core.config.AppEnvironmentConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Constructs Retrofit + OkHttp clients ONLY when a non-blank HTTPS backend base URL
 * has been explicitly supplied in LIVE configuration.
 * Never generates or falls back to fake production URLs, and rejects cleartext http:// URLs.
 */
object RetrofitClientFactory {

    val networkJson: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun createOkHttpClient(
        tokenProvider: AuthSessionTokenProvider? = null
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            if (BuildConfig.DEBUG) {
                Log.d("DrivePartnerHttp", SafeLogSanitizer.redactSensitiveFields(message))
            }
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader(RequestMetadataInterceptor.HEADER_AUTHORIZATION)
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(RequestMetadataInterceptor(tokenProvider = tokenProvider))
            .addInterceptor(loggingInterceptor)
            .build()
    }

    fun createRetrofitOrNull(
        environmentConfig: AppEnvironmentConfig,
        tokenProvider: AuthSessionTokenProvider? = null,
        okHttpClient: OkHttpClient = createOkHttpClient(tokenProvider)
    ): Retrofit? {
        if (!environmentConfig.isLiveBackendConfigured) {
            return null
        }
        val trimmedUrl = environmentConfig.apiBaseUrl.trim()
        require(trimmedUrl.startsWith("https://", ignoreCase = true)) {
            "LIVE API_BASE_URL must use HTTPS (cleartext HTTP is prohibited)."
        }
        val normalizedBaseUrl = if (trimmedUrl.endsWith("/")) {
            trimmedUrl
        } else {
            "$trimmedUrl/"
        }
        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
