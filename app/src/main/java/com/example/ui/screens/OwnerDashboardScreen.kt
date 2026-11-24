package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BookingRequest
import com.example.data.model.BookingStatus
import com.example.data.model.RoomListing
import com.example.data.model.RoomStatus
import com.example.data.model.UiState
import com.example.data.util.GeoUtils
import com.example.ui.components.EmptyStateView
import com.example.ui.components.RoomImage
import com.example.ui.theme.StatusAvailable
import com.example.ui.theme.StatusAvailableBg
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusRented
import com.example.ui.theme.StatusRentedBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(
    ownerRoomsState: UiState<List<RoomListing>>,
    bookingsState: UiState<List<BookingRequest>>,
    currentUserId: String,
    onAddRoom: () -> Unit,
    onEditRoom: (String) -> Unit,
    onRoomClick: (String) -> Unit,
    onUpdateStatus: (String, RoomStatus) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onBack: () -> Unit
) {
    var deleteConfirmTarget by remember { mutableStateOf<RoomListing?>(null) }

    val rooms = (ownerRoomsState as? UiState.Success)?.data ?: emptyList()
    val ownerBookings = ((bookingsState as? UiState.Success)?.data ?: emptyList())
        .filter { it.ownerId == currentUserId }

    val totalListings = rooms.size
    val activeListings = rooms.count { it.status == RoomStatus.AVAILABLE.name }
    val rentedListings = rooms.count { it.status == RoomStatus.RENTED.name }
    val totalViews = rooms.sumOf { it.viewsCount }
    val totalFavourites = rooms.sumOf { it.favouritesCount }
    val pendingReq = ownerBookings.count { it.status == BookingStatus.PENDING.name || it.status == BookingStatus.VISIT_SCHEDULED.name }
    val acceptedReq = ownerBookings.count { it.status == BookingStatus.ACCEPTED.name || it.status == BookingStatus.CONFIRMED.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Owner Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("owner_dash_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRoom,
                modifier = Modifier.testTag("owner_dash_add_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add new room")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (ownerRoomsState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> EmptyStateView(title = "Unable to load dashboard", subtitle = ownerRoomsState.message)
                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Analytics Grid
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Live Property Analytics", style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatTile("Total", "$totalListings", Modifier.weight(1f))
                                    StatTile("Active", "$activeListings", Modifier.weight(1f))
                                    StatTile("Rented", "$rentedListings", Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatTile("Pending Req", "$pendingReq", Modifier.weight(1f))
                                    StatTile("Accepted", "$acceptedReq", Modifier.weight(1f))
                                    StatTile("Views / Saves", "$totalViews / $totalFavourites", Modifier.weight(1f))
                                }
                            }
                        }

                        item {
                            Text("My Listings (${rooms.size})", style = MaterialTheme.typography.titleMedium)
                        }

                        if (rooms.isEmpty()) {
                            item {
                                EmptyStateView(
                                    title = "No listings created yet",
                                    subtitle = "Tap + to publish your first rental room with photos and GPS coordinates.",
                                    actionLabel = "Add New Room",
                                    onAction = onAddRoom
                                )
                            }
                        } else {
                            items(rooms, key = { it.id }) { room ->
                                val status = room.statusEnum()
                                val (bg, fg) = when (status) {
                                    RoomStatus.AVAILABLE -> StatusAvailableBg to StatusAvailable
                                    RoomStatus.PAUSED -> StatusPendingBg to StatusPending
                                    RoomStatus.RENTED, RoomStatus.REJECTED -> StatusRentedBg to StatusRented
                                }
                                Card(
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { onRoomClick(room.id) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RoomImage(
                                                imageSource = room.galleryPhotos().firstOrNull() ?: "",
                                                contentDescription = room.title,
                                                modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.small)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(room.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("${room.area}, ${room.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    "${GeoUtils.formatCurrency(room.monthlyRent)}/mo • ${room.viewsCount} views • ${room.favouritesCount} saves",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Surface(color = bg, shape = RoundedCornerShape(50)) {
                                                Text(
                                                    text = status.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = fg,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { onEditRoom(room.id) },
                                                modifier = Modifier.weight(1f).testTag("edit_room_${room.id}")
                                            ) {
                                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Edit")
                                            }
                                            if (status == RoomStatus.AVAILABLE) {
                                                OutlinedButton(
                                                    onClick = { onUpdateStatus(room.id, RoomStatus.PAUSED) },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Pause")
                                                }
                                                Button(
                                                    onClick = { onUpdateStatus(room.id, RoomStatus.RENTED) },
                                                    modifier = Modifier.weight(1f)
                                                ) { Text("Rented") }
                                            } else {
                                                Button(
                                                    onClick = { onUpdateStatus(room.id, RoomStatus.AVAILABLE) },
                                                    modifier = Modifier.weight(1.5f)
                                                ) {
                                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Publish")
                                                }
                                            }
                                            IconButton(onClick = { deleteConfirmTarget = room }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete listing", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Delete Listing?") },
            text = { Text("Permanently remove \"${target.title}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRoom(target.id)
                        deleteConfirmTarget = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
