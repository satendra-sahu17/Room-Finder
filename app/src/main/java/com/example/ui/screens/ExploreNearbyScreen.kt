package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.RoomFilterState
import com.example.data.model.RoomListing
import com.example.data.model.UiState
import com.example.data.model.UserLocationState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FilterBottomSheet
import com.example.ui.components.InteractiveRoomMap
import com.example.ui.components.RoomPostCard
import com.example.ui.components.SkeletonRoomCard
import com.example.ui.viewmodel.DiscoveryViewModel

@Composable
fun ExploreNearbyScreen(
    roomsState: UiState<List<RoomListing>>,
    filterState: RoomFilterState,
    likedRoomIds: Set<String>,
    favouriteRoomIds: Set<String>,
    locationState: UserLocationState,
    actorName: String,
    initialMapView: Boolean = false,
    discoveryViewModel: DiscoveryViewModel,
    onRequestLocation: () -> Unit,
    onManualCitySearch: (String) -> Unit,
    onRoomClick: (String) -> Unit
) {
    var isMapView by remember(initialMapView) { mutableStateOf(initialMapView) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var cityInput by remember { mutableStateOf("") }

    val radiusChips: List<Pair<Double?, String>> = listOf(
        0.5 to "500 m",
        1.0 to "1 km",
        2.0 to "2 km",
        5.0 to "5 km",
        10.0 to "10 km",
        20.0 to "20 km",
        null to "All"
    )

    val allRooms = (roomsState as? UiState.Success)?.data ?: emptyList()
    val filteredRooms = remember(allRooms, filterState, locationState) {
        discoveryViewModel.computeFilteredRooms(allRooms, filterState, locationState)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header + Search + Map/List Toggle + Radius Chips
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMapView) "Map Discovery" else "Explore & Nearby",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = !isMapView,
                            onClick = { isMapView = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("toggle_list_view")
                        ) {
                            Text("List")
                        }
                        SegmentedButton(
                            selected = isMapView,
                            onClick = { isMapView = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("toggle_map_view")
                        ) {
                            Text("Map")
                        }
                    }
                }

                // Search bar + Filter button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = filterState.query,
                        onValueChange = { q -> discoveryViewModel.updateFilter { it.copy(query = q) } },
                        placeholder = { Text("City, area, locality, facility...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filterState.query.isNotEmpty()) {
                                IconButton(onClick = { discoveryViewModel.updateFilter { it.copy(query = "") } }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("explore_search_input")
                    )

                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.testTag("open_filters_button")
                    ) {
                        BadgedBox(
                            badge = {
                                val count = filterState.activeFilterCount()
                                if (count > 0) Badge { Text("$count") }
                            }
                        ) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Filters")
                        }
                    }
                }

                // Distance radius quick chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(radiusChips) { (km, label) ->
                        FilterChip(
                            selected = filterState.maxDistanceKm == km,
                            onClick = {
                                if (km != null && !locationState.hasCoordinates) {
                                    onRequestLocation()
                                }
                                discoveryViewModel.updateFilter { it.copy(maxDistanceKm = km) }
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("radius_chip_$label")
                        )
                    }
                }

                // Location status or manual city fallback
                if (!locationState.hasCoordinates) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = locationState.error ?: "Detect GPS to sort rooms by live distance",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = onRequestLocation,
                                    enabled = !locationState.isFetching,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("detect_gps_button")
                                ) {
                                    if (locationState.isFetching) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Use GPS", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = cityInput,
                                    onValueChange = { cityInput = it },
                                    placeholder = { Text("Or enter city/area (e.g. Vijay Nagar, Jabalpur)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("manual_city_input")
                                )
                                TextButton(
                                    onClick = {
                                        onManualCitySearch(cityInput)
                                        discoveryViewModel.updateFilter { it.copy(query = cityInput) }
                                    },
                                    enabled = cityInput.isNotBlank() && !locationState.isFetching,
                                    modifier = Modifier.testTag("manual_city_go_button")
                                ) {
                                    Text("Set")
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredRooms.size} rooms matching" +
                                if (locationState.city.isNotBlank()) " • Near ${locationState.city}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (filterState.activeFilterCount() > 0 || filterState.query.isNotBlank()) {
                            TextButton(onClick = { discoveryViewModel.clearFilters() }) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        }

        // Body: Map or List
        Box(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            when (roomsState) {
                is UiState.Loading -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(3) { SkeletonRoomCard() }
                    }
                }
                is UiState.Error -> {
                    EmptyStateView(title = "Unable to load rooms", subtitle = roomsState.message)
                }
                is UiState.Success -> {
                    if (isMapView) {
                        InteractiveRoomMap(
                            rooms = filteredRooms,
                            userLocation = locationState,
                            radiusKm = filterState.maxDistanceKm,
                            onRoomSelected = onRoomClick,
                            onRequestCenterLocation = onRequestLocation,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .padding(bottom = 68.dp)
                        )
                    } else if (filteredRooms.isEmpty()) {
                        EmptyStateView(
                            title = "No rooms match your filters",
                            subtitle = "Try expanding the distance radius or clearing active filters.",
                            actionLabel = "Clear All Filters",
                            onAction = { discoveryViewModel.clearFilters() }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredRooms, key = { it.room.id }) { item ->
                                RoomPostCard(
                                    room = item.room,
                                    distanceKm = item.distanceKm,
                                    isLiked = item.room.id in likedRoomIds,
                                    isSaved = item.room.id in favouriteRoomIds,
                                    onCardClick = { onRoomClick(item.room.id) },
                                    onLikeClick = { discoveryViewModel.toggleLike(item.room, actorName) },
                                    onCommentClick = { onRoomClick(item.room.id) },
                                    onSaveClick = { discoveryViewModel.toggleFavourite(item.room) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            currentFilter = filterState,
            matchingCount = filteredRooms.size,
            onApply = { updated -> discoveryViewModel.updateFilter { updated } },
            onClear = { discoveryViewModel.clearFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }
}
