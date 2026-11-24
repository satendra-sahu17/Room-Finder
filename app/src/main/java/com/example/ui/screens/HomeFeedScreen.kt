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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.data.model.FeedTab
import com.example.data.model.RoomListing
import com.example.data.model.UiState
import com.example.data.model.UserLocationState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.RoomPostCard
import com.example.ui.components.SkeletonRoomCard
import com.example.ui.viewmodel.DiscoveryViewModel

@Composable
fun HomeFeedScreen(
    roomsState: UiState<List<RoomListing>>,
    selectedTab: FeedTab,
    likedRoomIds: Set<String>,
    favouriteRoomIds: Set<String>,
    locationState: UserLocationState,
    unreadNotifications: Int,
    actorName: String,
    discoveryViewModel: DiscoveryViewModel,
    onRequestLocation: () -> Unit,
    onRoomClick: (String) -> Unit,
    onAddRoomClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSavedRoomsClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Branded Top Bar + Quick Search
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "RoomFinder",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        val locLabel = when {
                            locationState.city.isNotBlank() && locationState.area.isNotBlank() ->
                                "${locationState.area}, ${locationState.city}"
                            locationState.city.isNotBlank() -> locationState.city
                            locationState.hasCoordinates -> "GPS active"
                            else -> "Tap to detect location"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("home_location_chip")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MyLocation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = locLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!locationState.hasCoordinates) {
                                TextButton(onClick = onRequestLocation) {
                                    Text("Enable", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onSavedRoomsClick,
                            modifier = Modifier.testTag("home_saved_button")
                        ) {
                            Icon(Icons.Filled.Bookmark, contentDescription = "Saved rooms")
                        }
                        IconButton(
                            onClick = onNotificationsClick,
                            modifier = Modifier.testTag("home_notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotifications > 0) {
                                        Badge { Text("$unreadNotifications") }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search city, area, title, Wi-Fi...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FeedTab.entries) { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { discoveryViewModel.setFeedTab(tab) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("feed_tab_${tab.name}")
                        )
                    }
                }
            }
        }

        // Feed Content
        Box(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            when (roomsState) {
                is UiState.Loading -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(3) { SkeletonRoomCard() }
                    }
                }
                is UiState.Error -> {
                    EmptyStateView(
                        title = "Couldn't load feed",
                        subtitle = roomsState.message
                    )
                }
                is UiState.Success -> {
                    val itemsList = remember(roomsState.data, selectedTab, locationState, searchQuery) {
                        discoveryViewModel.computeFeedRooms(
                            rooms = roomsState.data,
                            tab = selectedTab,
                            location = locationState,
                            query = searchQuery
                        )
                    }

                    if (itemsList.isEmpty()) {
                        EmptyStateView(
                            title = if (searchQuery.isNotBlank()) "No matching rooms" else "No rooms listed yet",
                            subtitle = if (searchQuery.isNotBlank()) {
                                "Try a different city, area, or keyword."
                            } else {
                                "Be the first to post an available room in your neighborhood."
                            },
                            actionLabel = "Post a Room",
                            onAction = onAddRoomClick
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Card(
                                    onClick = onAddRoomClick,
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("home_post_prompt_card")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Have a room to rent out?",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                "Publish photos, rent & live GPS coordinates in seconds.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "Post room",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            items(itemsList, key = { it.room.id }) { item ->
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
}
