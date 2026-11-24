package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.data.model.AccountType
import com.example.data.model.AppNotification
import com.example.data.model.FollowRelation
import com.example.data.model.PublicProfile
import com.example.data.model.UiState
import com.example.data.model.UserAccount
import com.example.data.model.UserLocationState
import com.example.data.repository.AdminRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.UserRepository
import com.example.data.util.GeoUtils
import com.example.data.util.PreferencesManager
import com.example.data.util.unwrapFirestoreError
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SessionViewModel(
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val chatRepository: ChatRepository,
    private val adminRepository: AdminRepository,
    private val prefs: PreferencesManager,
    val currentUserId: String
) : ViewModel() {

    val userAccount: StateFlow<UiState<UserAccount?>> = userRepository.observeMyAccount(currentUserId)
        .map<UserAccount?, UiState<UserAccount?>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing account", root)
            emit(UiState.Error(root.message ?: "Unable to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val publicProfiles: StateFlow<Map<String, PublicProfile>> = userRepository.observePublicProfiles()
        .map { list -> list.associateBy { it.userId } }
        .catch { error ->
            unwrapFirestoreError(error)
            emit(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    val followingIds: StateFlow<Set<String>> = userRepository.observeFollowing(currentUserId)
        .map { list -> list.map { it.targetUserId }.toSet() }
        .catch { error ->
            unwrapFirestoreError(error)
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val notifications: StateFlow<UiState<List<AppNotification>>> =
        notificationRepository.observeNotifications(currentUserId)
            .map<List<AppNotification>, UiState<List<AppNotification>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                emit(UiState.Error(root.message ?: "Unable to load notifications"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val unreadNotificationCount: StateFlow<Int> = notifications
        .map { state ->
            (state as? UiState.Success)?.data?.count { it.readStatus == "UNREAD" } ?: 0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val unreadMessageCount: StateFlow<Int> = chatRepository.observeConversations(currentUserId)
        .map { list -> list.count { currentUserId in it.unreadBy } }
        .catch { error ->
            unwrapFirestoreError(error)
            emit(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val darkModeOverride: StateFlow<Boolean?> = prefs.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _locationState = MutableStateFlow(UserLocationState())
    val locationState: StateFlow<UserLocationState> = _locationState.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { user ->
                val pendingDraft = prefs.consumePendingRegistration()
                userRepository.ensureUserInitialized(user, pendingDraft)
            }
            _isAdmin.value = adminRepository.checkIsAdmin()
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkMode(enabled) }
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(context: Context, permissionGranted: Boolean) {
        if (!permissionGranted) {
            _locationState.value = _locationState.value.copy(
                permissionGranted = false,
                isFetching = false,
                error = "Location permission denied. Search by city or area instead."
            )
            return
        }
        _locationState.value = _locationState.value.copy(
            permissionGranted = true,
            isFetching = true,
            error = null
        )
        viewModelScope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()
                val loc = runCatching {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                }.getOrNull() ?: runCatching { client.lastLocation.await() }.getOrNull()

                if (loc != null) {
                    val geo = GeoUtils.reverseGeocode(context, loc.latitude, loc.longitude)
                    _locationState.value = UserLocationState(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        city = geo?.city ?: "",
                        area = geo?.area ?: "",
                        permissionGranted = true,
                        isFetching = false,
                        error = null
                    )
                } else {
                    _locationState.value = _locationState.value.copy(
                        permissionGranted = true,
                        isFetching = false,
                        error = "GPS fix unavailable right now. Enter a city or area manually."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _locationState.value = _locationState.value.copy(
                    isFetching = false,
                    error = e.localizedMessage ?: "Unable to obtain location."
                )
            }
        }
    }

    fun setManualCityLocation(context: Context, query: String) {
        if (query.isBlank()) return
        _locationState.value = _locationState.value.copy(isFetching = true, error = null)
        viewModelScope.launch {
            val geo = GeoUtils.forwardGeocode(context, query)
            if (geo != null) {
                prefs.setLastCity(geo.city.ifBlank { query })
                _locationState.value = _locationState.value.copy(
                    latitude = geo.latitude,
                    longitude = geo.longitude,
                    city = geo.city.ifBlank { query },
                    area = geo.area,
                    isFetching = false,
                    error = null
                )
            } else {
                _locationState.value = _locationState.value.copy(
                    isFetching = false,
                    error = "Could not find coordinates for \"$query\"."
                )
            }
        }
    }

    fun updateProfile(
        fullName: String,
        username: String,
        phone: String,
        bio: String,
        city: String,
        accountType: AccountType,
        photoData: String,
        showPhone: Boolean,
        showEmail: Boolean,
        isPublic: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = userRepository.updateProfile(
                fullName, username, phone, bio, city, accountType, photoData, showPhone, showEmail, isPublic
            )
            res.onSuccess {
                _statusBanner.value = "Profile updated"
                onSuccess()
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun toggleFollow(targetUserId: String, targetName: String) {
        val isFollowing = targetUserId in followingIds.value
        viewModelScope.launch {
            userRepository.toggleFollow(targetUserId, targetName, isFollowing)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Unable to update follow status" }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(currentUserId, notificationId)
        }
    }

    fun markAllNotificationsRead() {
        val list = (notifications.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            notificationRepository.markAllAsRead(currentUserId, list)
        }
    }

    fun exportUserData(onReady: (String) -> Unit) {
        viewModelScope.launch {
            userRepository.exportUserDataJson()
                .onSuccess(onReady)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Export failed" }
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            userRepository.deleteMyAccount()
                .onSuccess { onDeleted() }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Account deletion failed" }
        }
    }

    private companion object {
        const val TAG = "SessionVM"
    }
}
