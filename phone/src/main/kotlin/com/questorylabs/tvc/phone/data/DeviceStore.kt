package com.questorylabs.tvc.phone.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.questorylabs.tvc.shared.Protocol
import com.questorylabs.tvc.shared.SavedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "devices")

class DeviceStore(private val context: Context) {
    private val key = stringPreferencesKey("devices")
    private val serializer = ListSerializer(SavedDevice.serializer())

    val devices: Flow<List<SavedDevice>> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { Protocol.json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
    }

    suspend fun upsert(device: SavedDevice) = context.dataStore.edit { prefs ->
        val current = prefs[key]?.let { runCatching { Protocol.json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
        val updated = current.filterNot { it.host == device.host } + device
        prefs[key] = Protocol.json.encodeToString(serializer, updated)
    }

    suspend fun remove(host: String) = context.dataStore.edit { prefs ->
        val current = prefs[key]?.let { runCatching { Protocol.json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
        prefs[key] = Protocol.json.encodeToString(serializer, current.filterNot { it.host == host })
    }
}
