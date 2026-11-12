package com.drivepartner.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivepartner.app.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

enum class AppLanguage(val bcp47Tag: String) {
    ENGLISH("en"),
    HINDI("hi");

    fun toggle(): AppLanguage = if (this == ENGLISH) HINDI else ENGLISH

    companion object {
        fun fromTag(tag: String?): AppLanguage = when {
            tag.isNullOrBlank() -> ENGLISH
            tag.startsWith("hi", ignoreCase = true) -> HINDI
            else -> ENGLISH
        }
    }
}

interface LanguagePreferencesRepository {
    val preferredLanguage: Flow<AppLanguage>
    val themePreference: Flow<ThemePreference>
        get() = flowOf(ThemePreference.LIGHT)

    suspend fun setPreferredLanguage(language: AppLanguage)
    suspend fun setThemePreference(theme: ThemePreference) {}
}

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drive_partner_ui_prefs"
)

/**
 * Local DataStore repository storing UI preferences only (EN/HI locale & Light/Dark/System theme).
 * Financial ledger, orders, and compliance state are never stored here.
 */
class DataStoreLanguagePreferencesRepository(
    private val context: Context
) : LanguagePreferencesRepository {

    private val preferredLanguageKey = stringPreferencesKey("preferred_language_tag")
    private val themePreferenceKey = stringPreferencesKey("ui_theme_preference")

    override val preferredLanguage: Flow<AppLanguage> =
        context.userPreferencesDataStore.data.map { prefs ->
            AppLanguage.fromTag(prefs[preferredLanguageKey])
        }

    override val themePreference: Flow<ThemePreference> =
        context.userPreferencesDataStore.data.map { prefs ->
            ThemePreference.fromStorage(prefs[themePreferenceKey])
        }

    override suspend fun setPreferredLanguage(language: AppLanguage) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[preferredLanguageKey] = language.bcp47Tag
        }
    }

    override suspend fun setThemePreference(theme: ThemePreference) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[themePreferenceKey] = theme.storageValue
        }
    }
}
