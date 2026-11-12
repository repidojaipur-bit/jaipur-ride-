package com.drivepartner.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A driver-saved place: home stand, market lane, airport queue — the shortcuts a partner actually
 * uses. Stored on-device only (never synced, never sent to the backend) because these are personal
 * favourites, not operational data.
 */
@Serializable
data class SavedLocation(
    val id: String,
    val label: String,
    val locality: String,
    val cityCode: String,
    val isReturnHome: Boolean = false
)

private val Context.savedLocationsStore by preferencesDataStore(name = "drive_saved_locations")

private val SAVED_LOCATIONS_KEY = stringPreferencesKey("saved_locations_json")

/**
 * On-device persistence for favourite/saved locations surfaced by the top-bar shortcut.
 * CRUD is suspending and the list is exposed as a [StateFlow] so the sheet and any future
 * selector stay consistent.
 */
class SavedLocationsRepository(
    context: Context,
    scope: CoroutineScope
) {
    private val applicationContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    val savedLocations: StateFlow<List<SavedLocation>> =
        applicationContext.savedLocationsStore.data
            .map { prefs -> decode(prefs[SAVED_LOCATIONS_KEY]) }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    suspend fun add(label: String, locality: String, cityCode: String): SavedLocation {
        val entry = SavedLocation(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            locality = locality.trim(),
            cityCode = cityCode.trim().uppercase(),
            isReturnHome = false
        )
        mutate { current -> current + entry }
        return entry
    }

    suspend fun remove(id: String) {
        mutate { current -> current.filterNot { it.id == id } }
    }

    /** Marks exactly one saved location as the return-home destination. */
    suspend fun setReturnHome(id: String) {
        mutate { current -> current.map { it.copy(isReturnHome = it.id == id) } }
    }

    private suspend fun mutate(transform: (List<SavedLocation>) -> List<SavedLocation>) {
        applicationContext.savedLocationsStore.edit { prefs ->
            val current = decode(prefs[SAVED_LOCATIONS_KEY])
            val updated = transform(current).take(MAX_SAVED_LOCATIONS)
            prefs[SAVED_LOCATIONS_KEY] =
                json.encodeToString(ListSerializer(SavedLocation.serializer()), updated)
        }
    }

    private fun decode(raw: String?): List<SavedLocation> =
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(ListSerializer(SavedLocation.serializer()), raw) }
                .getOrDefault(emptyList())
        }

    companion object {
        /** Keeps the shortcut list glanceable on a phone mount. */
        const val MAX_SAVED_LOCATIONS = 8
    }
}
