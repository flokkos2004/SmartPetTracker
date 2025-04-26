package com.example.smartpettracker

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension for easy access to DataStore
val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsDataStore {
    private val MUTE_ALERTS_KEY = booleanPreferencesKey("mute_alerts")
    private val RADIUS_KEY = doublePreferencesKey("geofence_radius")
    internal val CENTER_LAT_KEY = doublePreferencesKey("center_latitude")
    internal val CENTER_LON_KEY = doublePreferencesKey("center_longitude")
    internal val HOME_LAT_KEY = doublePreferencesKey("home_latitude")
    internal val HOME_LON_KEY = doublePreferencesKey("home_longitude")
    private val LAST_LAT_KEY = doublePreferencesKey("last_lat")
    private val LAST_LON_KEY = doublePreferencesKey("last_lon")

    fun getMuteAlerts(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[MUTE_ALERTS_KEY] == true
        }
    }

    suspend fun setMuteAlerts(context: Context, value: Boolean) {
        context.dataStore.edit { settings ->
            settings[MUTE_ALERTS_KEY] = value
        }
    }

    fun getRadius(context: Context): Flow<Double> {
        return context.dataStore.data.map { preferences ->
            preferences[RADIUS_KEY] ?: 100.0
        }
    }

    suspend fun setRadius(context: Context, value: Double) {
        context.dataStore.edit { settings ->
            settings[RADIUS_KEY] = value
        }
    }

    fun getGeofenceCenter(context: Context): Flow<LatLng> {
        return context.dataStore.data.map { preferences ->
            val lat = preferences[CENTER_LAT_KEY] ?: 35.1856
            val lon = preferences[CENTER_LON_KEY] ?: 33.3823
            LatLng(lat, lon)
        }
    }

    suspend fun setGeofenceCenter(context: Context, latLng: LatLng) {
        context.dataStore.edit { settings ->
            settings[CENTER_LAT_KEY] = latLng.latitude
            settings[CENTER_LON_KEY] = latLng.longitude
        }
    }

    suspend fun saveLastKnownLocation(context: Context, latLng: LatLng) {
        context.dataStore.edit { prefs ->
            prefs[LAST_LAT_KEY] = latLng.latitude
            prefs[LAST_LON_KEY] = latLng.longitude
        }
    }

    fun getLastKnownLocation(context: Context): Flow<LatLng?> {
        return context.dataStore.data.map { prefs ->
            val lat = prefs[LAST_LAT_KEY]
            val lon = prefs[LAST_LON_KEY]
            if (lat != null && lon != null) LatLng(lat, lon) else null
        }
    }

    fun getHomeLocation(context: Context): Flow<LatLng?> {
        return context.dataStore.data.map { prefs ->
            val lat = prefs[HOME_LAT_KEY]
            val lon = prefs[HOME_LON_KEY]
            if (lat != null && lon != null) LatLng(lat, lon) else null
        }
    }

    suspend fun setHomeLocation(context: Context, latLng: LatLng) {
        context.dataStore.edit { settings ->
            settings[HOME_LAT_KEY] = latLng.latitude
            settings[HOME_LON_KEY] = latLng.longitude
        }
    }

    suspend fun clearHomeLocation(context: Context) {
        context.dataStore.edit { settings ->
            settings.remove(HOME_LAT_KEY)
            settings.remove(HOME_LON_KEY)
        }
    }

}


