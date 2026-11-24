package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FavouriteItem
import com.example.data.model.FeedTab
import com.example.data.model.RoomFilterState
import com.example.data.model.RoomListing
import com.example.data.model.SortOption
import com.example.data.model.UiState
import com.example.data.model.UserLocationState
import com.example.data.repository.RoomRepository
import com.example.data.util.GeoUtils
import com.example.data.util.unwrapFirestoreError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoomWithDistance(
    val room: RoomListing,
    val distanceKm: Double?
)

class DiscoveryViewModel(
    private val roomRepository: RoomRepository,
    private val currentUserId: String
) : ViewModel() {

    val availableRooms: StateFlow<UiState<List<RoomListing>>> = roomRepository.observeAvailableRooms()
        .map<List<RoomListing>, UiState<List<RoomListing>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing rooms", root)
            emit(UiState.Error(root.message ?: "Failed to load rooms"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val likedRoomIds: StateFlow<Set<String>> = roomRepository.observeLikedRoomIds(currentUserId)
        .catch { error ->
            unwrapFirestoreError(error)
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val favourites: StateFlow<List<FavouriteItem>> = roomRepository.observeFavourites(currentUserId)
        .catch { error ->
            unwrapFirestoreError(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val favouriteRoomIds: StateFlow<Set<String>> = favourites
        .map { list -> list.map { it.roomId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    private val _selectedFeedTab = MutableStateFlow(FeedTab.RECOMMENDED)
    val selectedFeedTab: StateFlow<FeedTab> = _selectedFeedTab.asStateFlow()

    private val _filterState = MutableStateFlow(RoomFilterState())
    val filterState: StateFlow<RoomFilterState> = _filterState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun setFeedTab(tab: FeedTab) {
        _selectedFeedTab.value = tab
    }

    fun updateFilter(transform: (RoomFilterState) -> RoomFilterState) {
        _filterState.value = transform(_filterState.value)
    }

    fun clearFilters() {
        _filterState.value = RoomFilterState()
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun toggleLike(room: RoomListing, actorName: String) {
        val liked = room.id in likedRoomIds.value
        viewModelScope.launch {
            roomRepository.toggleLike(room, actorName, liked)
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to update like" }
        }
    }

    fun toggleFavourite(room: RoomListing) {
        val saved = room.id in favouriteRoomIds.value
        viewModelScope.launch {
            roomRepository.toggleFavourite(room, saved)
                .onSuccess {
                    _actionMessage.value = if (saved) "Removed from Favourites" else "Saved to Favourites"
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to update favourites" }
        }
    }

    fun computeFeedRooms(
        rooms: List<RoomListing>,
        tab: FeedTab,
        location: UserLocationState,
        query: String
    ): List<RoomWithDistance> {
        val q = query.trim().lowercase()
        val withDist = rooms
            .filter { room ->
                q.isEmpty() ||
                    room.title.lowercase().contains(q) ||
                    room.city.lowercase().contains(q) ||
                    room.area.lowercase().contains(q) ||
                    room.facilities.any { it.lowercase().contains(q) }
            }
            .map { room ->
                val dist = if (location.hasCoordinates && (room.latitude != 0.0 || room.longitude != 0.0)) {
                    GeoUtils.distanceKm(location.latitude!!, location.longitude!!, room.latitude, room.longitude)
                } else null
                RoomWithDistance(room, dist)
            }

        return when (tab) {
            FeedTab.RECOMMENDED -> withDist.sortedByDescending { item ->
                val popularity = item.room.likesCount * 3 + item.room.favouritesCount * 4 + item.room.viewsCount
                val proximityBonus = if (item.distanceKm != null && item.distanceKm < 25.0) (25.0 - item.distanceKm) * 2 else 0.0
                popularity + proximityBonus
            }
            FeedTab.NEARBY -> {
                if (location.hasCoordinates) {
                    withDist.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
                } else {
                    withDist
                }
            }
            FeedTab.LATEST -> withDist.sortedByDescending { it.room.createdAt?.seconds ?: 0L }
            FeedTab.POPULAR -> withDist.sortedByDescending { it.room.likesCount + it.room.favouritesCount * 2 + it.room.viewsCount }
        }
    }

    fun computeFilteredRooms(
        rooms: List<RoomListing>,
        filter: RoomFilterState,
        location: UserLocationState
    ): List<RoomWithDistance> {
        val q = filter.query.trim().lowercase()
        val mapped = rooms.map { room ->
            val dist = if (location.hasCoordinates && (room.latitude != 0.0 || room.longitude != 0.0)) {
                GeoUtils.distanceKm(location.latitude!!, location.longitude!!, room.latitude, room.longitude)
            } else null
            RoomWithDistance(room, dist)
        }.filter { (room, dist) ->
            val matchesQuery = q.isEmpty() ||
                room.title.lowercase().contains(q) ||
                room.city.lowercase().contains(q) ||
                room.area.lowercase().contains(q) ||
                room.address.lowercase().contains(q) ||
                room.facilities.any { it.lowercase().contains(q) }

            val matchesMin = filter.minRent == null || room.monthlyRent >= filter.minRent
            val matchesMax = filter.maxRent == null || room.monthlyRent <= filter.maxRent
            val matchesDist = filter.maxDistanceKm == null || (dist != null && dist <= filter.maxDistanceKm)
            val matchesCategory = filter.category == null || room.category == filter.category.name
            val matchesFurnishing = filter.furnishing == null || room.furnishing == filter.furnishing.name
            val matchesBathroom = filter.bathroomType == null || room.bathroomType == filter.bathroomType.name
            val matchesGender = filter.genderPreference == null || room.genderPreference == filter.genderPreference.name
            val matchesFood = !filter.foodOnly || room.facilities.any { it.contains("Food", ignoreCase = true) }
            val matchesParking = !filter.parkingOnly || room.facilities.any { it.contains("Parking", ignoreCase = true) }

            matchesQuery && matchesMin && matchesMax && matchesDist &&
                matchesCategory && matchesFurnishing && matchesBathroom &&
                matchesGender && matchesFood && matchesParking
        }

        return when (filter.sortOption) {
            SortOption.NEAREST -> mapped.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            SortOption.LOWEST_PRICE -> mapped.sortedBy { it.room.monthlyRent }
            SortOption.HIGHEST_PRICE -> mapped.sortedByDescending { it.room.monthlyRent }
            SortOption.RECENT -> mapped.sortedByDescending { it.room.createdAt?.seconds ?: 0L }
            SortOption.POPULAR -> mapped.sortedByDescending { it.room.likesCount + it.room.favouritesCount + it.room.viewsCount }
        }
    }

    private companion object {
        const val TAG = "DiscoveryVM"
    }
}
