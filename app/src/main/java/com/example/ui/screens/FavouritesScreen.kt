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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.FavouriteItem
import com.example.data.model.RoomListing
import com.example.data.util.GeoUtils
import com.example.ui.components.EmptyStateView
import com.example.ui.components.RoomImage
import com.example.ui.theme.StatusAvailable
import com.example.ui.theme.StatusAvailableBg
import com.example.ui.theme.StatusRented
import com.example.ui.theme.StatusRentedBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(
    favourites: List<FavouriteItem>,
    availableRooms: List<RoomListing>,
    onRoomClick: (String) -> Unit,
    onRemoveFavourite: (RoomListing) -> Unit,
    onBack: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var availableOnly by remember { mutableStateOf(false) }

    val availableMap = remember(availableRooms) { availableRooms.associateBy { it.id } }

    val filtered = remember(favourites, availableMap, search, availableOnly) {
        val q = search.trim().lowercase()
        favourites.filter { fav ->
            val isAvail = fav.roomId in availableMap
            val matchesAvail = !availableOnly || isAvail
            val matchesQ = q.isEmpty() || fav.roomTitle.lowercase().contains(q) || fav.roomCity.lowercase().contains(q)
            matchesAvail && matchesQ
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Rooms (${favourites.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("favourites_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filter saved rooms...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = availableOnly,
                    onClick = { availableOnly = !availableOnly },
                    label = { Text("Available Now Only") }
                )
            }

            Box(modifier = Modifier.widthIn(max = 680.dp).fillMaxSize()) {
                if (filtered.isEmpty()) {
                    EmptyStateView(
                        title = "No saved rooms",
                        subtitle = "Tap Save on any room listing to bookmark it across devices."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { fav ->
                            val liveRoom = availableMap[fav.roomId]
                            val isAvail = liveRoom != null
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRoomClick(fav.roomId) },
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RoomImage(
                                        imageSource = liveRoom?.coverPhoto?.ifBlank { fav.roomPhoto } ?: fav.roomPhoto,
                                        contentDescription = fav.roomTitle,
                                        modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.small)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(fav.roomTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${fav.roomCity} • ${GeoUtils.formatCurrency(liveRoom?.monthlyRent ?: fav.roomRent)}/mo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Surface(
                                            color = if (isAvail) StatusAvailableBg else StatusRentedBg,
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isAvail) "Available" else "Unavailable / Rented",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isAvail) StatusAvailable else StatusRented,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            onRemoveFavourite(liveRoom ?: RoomListing(id = fav.roomId, title = fav.roomTitle))
                                        }
                                    ) {
                                        Icon(Icons.Filled.BookmarkRemove, contentDescription = "Remove from saved")
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
