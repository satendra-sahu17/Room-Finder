package com.example.data.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "roomfinder_prefs")

data class PendingRegistrationDraft(
    val fullName: String = "",
    val phone: String = "",
    val city: String = "",
    val accountType: AccountType = AccountType.BOTH
)

class PreferencesManager(private val context: Context) {

    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val lastCityKey = stringPreferencesKey("last_city")
    private val pendingRegNameKey = stringPreferencesKey("pending_reg_name")
    private val pendingRegPhoneKey = stringPreferencesKey("pending_reg_phone")
    private val pendingRegCityKey = stringPreferencesKey("pending_reg_city")
    private val pendingRegTypeKey = stringPreferencesKey("pending_reg_type")
    private val hasPendingRegKey = booleanPreferencesKey("has_pending_reg")

    val darkModeFlow: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[darkModeKey]
    }

    val lastCityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[lastCityKey] ?: ""
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[darkModeKey] = enabled
        }
    }

    suspend fun setLastCity(city: String) {
        context.dataStore.edit { prefs ->
            prefs[lastCityKey] = city
        }
    }

    suspend fun savePendingRegistration(draft: PendingRegistrationDraft) {
        context.dataStore.edit { prefs ->
            prefs[hasPendingRegKey] = true
            prefs[pendingRegNameKey] = draft.fullName.trim().take(100)
            prefs[pendingRegPhoneKey] = draft.phone.trim().take(30)
            prefs[pendingRegCityKey] = draft.city.trim().take(80)
            prefs[pendingRegTypeKey] = draft.accountType.name
        }
    }

    suspend fun consumePendingRegistration(): PendingRegistrationDraft? {
        val prefs = context.dataStore.data.first()
        if (prefs[hasPendingRegKey] != true) return null
        val draft = PendingRegistrationDraft(
            fullName = prefs[pendingRegNameKey] ?: "",
            phone = prefs[pendingRegPhoneKey] ?: "",
            city = prefs[pendingRegCityKey] ?: "",
            accountType = AccountType.fromRaw(prefs[pendingRegTypeKey])
        )
        context.dataStore.edit { mutable ->
            mutable.remove(hasPendingRegKey)
            mutable.remove(pendingRegNameKey)
            mutable.remove(pendingRegPhoneKey)
            mutable.remove(pendingRegCityKey)
            mutable.remove(pendingRegTypeKey)
        }
        return draft
    }
}
