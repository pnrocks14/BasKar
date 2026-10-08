package app.pratyahara.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pratyahara")

/** Single source of truth, persisted with DataStore as one JSON record. */
class Store(context: Context, scope: CoroutineScope) {

    private val ds = context.applicationContext.dataStore
    private val key = stringPreferencesKey("app_data")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val data: StateFlow<AppData> = ds.data
        .map { prefs -> prefs[key]?.let { runCatching { json.decodeFromString<AppData>(it) }.getOrNull() } ?: AppData() }
        .stateIn(scope, SharingStarted.Eagerly, AppData())

    /** True once the stored record has been read from disk at least once. */
    val loaded: StateFlow<Boolean> = ds.data.map { true }.stateIn(scope, SharingStarted.Eagerly, false)

    val current: AppData get() = data.value

    suspend fun update(transform: (AppData) -> AppData) {
        ds.edit { prefs ->
            val old = prefs[key]?.let { runCatching { json.decodeFromString<AppData>(it) }.getOrNull() } ?: AppData()
            prefs[key] = json.encodeToString(AppData.serializer(), prune(transform(old)))
        }
    }

    /** Keeps 60 days of counters and tasks; older history is not needed and not kept. */
    private fun prune(d: AppData): AppData {
        val cutoff = LocalDate.now().minusDays(60).toString()
        return d.copy(
            days = d.days.filterKeys { it >= cutoff },
            tasks = d.tasks.filter { it.day >= cutoff },
        )
    }
}
