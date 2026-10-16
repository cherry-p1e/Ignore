package com.caffeinetracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.caffeinetracker.pk.Dose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.caffeineDataStore: DataStore<Preferences> by preferencesDataStore(name = "caffeine_doses")

private val DOSES_KEY = stringSetPreferencesKey("doses")

/**
 * Local dose log. Each entry is "timestamp:mg:id" so a restart can rebuild
 * both today's sum and the blood estimate. No network.
 */
class DoseRepository(private val dataStore: DataStore<Preferences>) {
    val doses: Flow<List<Dose>> = dataStore.data.map { prefs ->
        prefs[DOSES_KEY].orEmpty().mapNotNull(Dose::decode).sortedBy { it.timestampMillis }
    }

    suspend fun addDose(amountMg: Int, nowMillis: Long = System.currentTimeMillis()) {
        val encoded = Dose.encode(Dose(id = System.nanoTime().toString(), timestampMillis = nowMillis, amountMg = amountMg))
        dataStore.edit { prefs ->
            prefs[DOSES_KEY] = prefs[DOSES_KEY].orEmpty() + encoded
        }
    }

    companion object {
        fun create(context: Context): DoseRepository = DoseRepository(context.applicationContext.caffeineDataStore)
    }
}
