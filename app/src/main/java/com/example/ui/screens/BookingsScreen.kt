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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BookingRequest
import com.example.data.model.BookingStatus
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

@Composable
fun BookingsScreen(
    bookingsState: UiState<List<BookingRequest>>,
    currentUserId: String,
    actorName: String,
    onRoomClick: (String) -> Unit,
    onUpdateStatus: (BookingRequest, BookingStatus, String) -> Unit
) {
    var showIncoming by remember { mutableStateOf(false) }
    var scheduleDialogTarget by remember { mutableStateOf<BookingRequest?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Bookings & Visits", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !showIncoming,
                        onClick = { showIncoming = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("bookings_sent_tab")
                    ) { Text("My Requests") }
                    SegmentedButton(
                        selected = showIncoming,
                        onClick = { showIncoming = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("bookings_received_tab")
                    ) { Text("Owner Requests") }
                }
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            when (bookingsState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> EmptyStateView(title = "Unable to load bookings", subtitle = bookingsState.message)
                is UiState.Success -> {
                    val list = remember(bookingsState.data, showIncoming, currentUserId) {
                        if (showIncoming) bookingsState.data.filter { it.ownerId == currentUserId }
                        else bookingsState.data.filter { it.seekerId == currentUserId }
                    }
                    if (list.isEmpty()) {
                        EmptyStateView(
                            title = if (showIncoming) "No incoming requests" else "No booking requests yet",
                            subtitle = if (showIncoming) "Requests from seekers for your rooms appear here."
                            else "Visit or book a room from its detail page to track status here."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(list, key = { it.id }) { booking ->
                                val statusEnum = booking.statusEnum()
                                val (bgColor, fgColor) = when (statusEnum) {
                                    BookingStatus.ACCEPTED, BookingStatus.CONFIRMED -> StatusAvailableBg to StatusAvailable
                                    BookingStatus.REJECTED, BookingStatus.CANCELLED -> StatusRentedBg to StatusRented
                                    BookingStatus.PENDING, BookingStatus.VISIT_SCHEDULED -> StatusPendingBg to StatusPending
                                }

                                Card(
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { onRoomClick(booking.roomId) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RoomImage(
                                                imageSource = booking.roomPhoto,
                                                contentDescription = booking.roomTitle,
                                                modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = booking.roomTitle,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${booking.roomCity} • ${GeoUtils.formatCurrency(booking.roomRent)}/mo",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${booking.typeEnum().label} • Date: ${booking.preferredDate}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Surface(color = bgColor, shape = RoundedCornerShape(50)) {
                                                Text(
                                                    text = statusEnum.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = fgColor,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = if (showIncoming) "From: ${booking.seekerName}" else "Owner: ${booking.ownerName}",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        if (booking.message.isNotBlank()) {
                                            Text(
                                                text = "\"${booking.message}\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Role-aware actions (terminal states locked)
                                        val isTerminal = statusEnum in listOf(
                                            BookingStatus.REJECTED,
                                            BookingStatus.CANCELLED,
                                            BookingStatus.CONFIRMED
                                        )
                                        if (!isTerminal) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (showIncoming) {
                                                    if (statusEnum == BookingStatus.PENDING || statusEnum == BookingStatus.VISIT_SCHEDULED) {
                                                        OutlinedButton(
                                                            onClick = { scheduleDialogTarget = booking },
                                                            modifier = Modifier.weight(1f)
                                                        ) { Text("Visit") }
                                                        Button(
                                                            onClick = { onUpdateStatus(booking, BookingStatus.ACCEPTED, booking.preferredDate) },
                                                            modifier = Modifier.weight(1f)
                                                        ) { Text("Accept") }
                                                        OutlinedButton(
                                                            onClick = { onUpdateStatus(booking, BookingStatus.REJECTED, booking.preferredDate) },
                                                            modifier = Modifier.weight(1f)
                                                        ) { Text("Reject") }
                                                    } else if (statusEnum == BookingStatus.ACCEPTED) {
                                                        Button(
                                                            onClick = { onUpdateStatus(booking, BookingStatus.CONFIRMED, booking.preferredDate) },
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) { Text("Confirm & Mark Room Rented") }
                                                    }
                                                } else {
                                                    if (statusEnum == BookingStatus.PENDING || statusEnum == BookingStatus.VISIT_SCHEDULED) {
                                                        OutlinedButton(
                                                            onClick = { onUpdateStatus(booking, BookingStatus.CANCELLED, booking.preferredDate) },
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) { Text("Cancel Request") }
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
        }
    }

    scheduleDialogTarget?.let { target ->
        var dateInput by remember(target.id) { mutableStateOf(target.preferredDate) }
        AlertDialog(
            onDismissRequest = { scheduleDialogTarget = null },
            title = { Text("Schedule Visit Date") },
            text = {
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text("Confirmed Visit Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateStatus(target, BookingStatus.VISIT_SCHEDULED, dateInput)
                        scheduleDialogTarget = null
                    },
                    enabled = dateInput.isNotBlank()
                ) { Text("Confirm Visit") }
            },
            dismissButton = {
                TextButton(onClick = { scheduleDialogTarget = null }) { Text("Cancel") }
            }
        )
    }
}
