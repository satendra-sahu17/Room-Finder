package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BookingType
import com.example.data.model.PublicProfile
import com.example.data.model.RoomComment
import com.example.data.model.RoomListing
import com.example.data.model.UiState
import com.example.data.model.UserLocationState
import com.example.data.util.GeoUtils
import com.example.ui.components.EmptyStateView
import com.example.ui.components.RoomImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeDate
import com.example.ui.theme.AmberAccent
import com.example.ui.viewmodel.RoomDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoomDetailScreen(
    viewModel: RoomDetailViewModel,
    currentUserId: String,
    myName: String,
    myPhoto: String,
    isSaved: Boolean,
    isFollowingOwner: Boolean,
    ownerPublicProfile: PublicProfile?,
    userLocation: UserLocationState,
    onToggleSave: (RoomListing) -> Unit,
    onToggleFollow: (String, String) -> Unit,
    onOpenChat: (String) -> Unit,
    onEditRoom: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val roomState by viewModel.roomState.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val eligibleBooking by viewModel.eligibleBookingForReview.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    var showBookingDialog by remember { mutableStateOf<BookingType?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<RoomComment?>(null) }
    var commentDraft by remember { mutableStateOf("") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Room Details") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val room = (roomState as? UiState.Success)?.data
                    if (room != null) {
                        IconButton(
                            onClick = { onToggleSave(room) },
                            modifier = Modifier.testTag("detail_save_button")
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Save room",
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "RoomFinder: ${room.title} in ${room.area}, ${room.city} — ${GeoUtils.formatCurrency(room.monthlyRent)}/mo"
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Room"))
                            },
                            modifier = Modifier.testTag("detail_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share room")
                        }
                        if (room.ownerId == currentUserId) {
                            IconButton(
                                onClick = { onEditRoom(room.id) },
                                modifier = Modifier.testTag("detail_edit_button")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit room")
                            }
                        } else {
                            IconButton(
                                onClick = { showReportDialog = true },
                                modifier = Modifier.testTag("detail_report_button")
                            ) {
                                Icon(Icons.Filled.Flag, contentDescription = "Report listing")
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            val room = (roomState as? UiState.Success)?.data
            if (room != null && room.ownerId != currentUserId) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.startChatWithOwner(myName, myPhoto, room, onOpenChat)
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("detail_chat_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chat")
                        }
                        OutlinedButton(
                            onClick = { showBookingDialog = BookingType.VISIT },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("detail_visit_button")
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Visit")
                        }
                        Button(
                            onClick = { showBookingDialog = BookingType.BOOKING },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("detail_book_button")
                        ) {
                            Text("Book")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val state = roomState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    EmptyStateView(title = "Unable to load room", subtitle = state.message)
                }
                is UiState.Success -> {
                    val room = state.data
                    if (room == null) {
                        EmptyStateView(title = "Room not found", subtitle = "This listing may have been removed or paused.")
                    } else {
                        val photos = remember(room) { room.galleryPhotos() }
                        val pagerState = rememberPagerState(pageCount = { maxOf(photos.size, 1) })
                        val distanceKm = remember(room, userLocation) {
                            if (userLocation.hasCoordinates && (room.latitude != 0.0 || room.longitude != 0.0)) {
                                GeoUtils.distanceKm(userLocation.latitude!!, userLocation.longitude!!, room.latitude, room.longitude)
                            } else null
                        }

                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Swipeable Photo Gallery
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 10f)
                                        .clip(MaterialTheme.shapes.large)
                                ) {
                                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                                        RoomImage(
                                            imageSource = photos.getOrNull(page) ?: "",
                                            contentDescription = "${room.title} photo ${page + 1}",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    if (photos.size > 1) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(10.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            repeat(photos.size) { idx ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (pagerState.currentPage == idx) 8.dp else 6.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (pagerState.currentPage == idx) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.outline
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Title, Price, Specs
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = room.title,
                                            style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "${GeoUtils.formatCurrency(room.monthlyRent)}/mo",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val distLabel = GeoUtils.formatDistance(distanceKm)
                                        Text(
                                            text = room.displayAddress() + if (distLabel.isNotBlank()) " • $distLabel" else "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SpecPill(room.categoryEnum().label)
                                        SpecPill(room.furnishingEnum().label)
                                        SpecPill(room.bathroomEnum().label)
                                        SpecPill("For ${room.preferenceEnum().label}")
                                        if (room.securityDeposit > 0) SpecPill("Deposit ${GeoUtils.formatCurrency(room.securityDeposit)}")
                                        if (room.roomSizeSqFt > 0) SpecPill("${room.roomSizeSqFt} sq.ft")
                                        SpecPill("Max ${room.occupants} occupant(s)")
                                        if (room.availableFrom.isNotBlank()) SpecPill("From ${room.availableFrom}")
                                    }
                                }
                            }

                            // Description
                            item {
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("About this Room", style = MaterialTheme.typography.titleMedium)
                                        Text(room.description, style = MaterialTheme.typography.bodyMedium)
                                        if (room.facilities.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Facilities", style = MaterialTheme.typography.titleSmall)
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                room.facilities.forEach { fac -> SpecPill(fac) }
                                            }
                                        }
                                        if (room.videoUrl.isNotBlank()) {
                                            Text(
                                                "Video Tour: ${room.videoUrl}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Location & Google Maps Directions Card
                            item {
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Location & Directions", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = if (room.hideExactAddress) "Approximate area: ${room.area}, ${room.city}" else room.displayAddress(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                GeoUtils.openGoogleMapsDirections(
                                                    context = context,
                                                    originLat = userLocation.latitude,
                                                    originLng = userLocation.longitude,
                                                    destLat = room.latitude,
                                                    destLng = room.longitude,
                                                    label = room.title
                                                )
                                            },
                                            modifier = Modifier.testTag("detail_directions_button")
                                        ) {
                                            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Directions")
                                        }
                                    }
                                }
                            }

                            // Owner Profile Card
                            item {
                                val verified = ownerPublicProfile?.verified ?: room.ownerVerified
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UserAvatar(
                                            photoData = ownerPublicProfile?.photoData?.ifBlank { room.ownerPhoto } ?: room.ownerPhoto,
                                            name = room.ownerName,
                                            size = 48.dp,
                                            verified = verified
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(room.ownerName, style = MaterialTheme.typography.titleMedium)
                                            val followers = ownerPublicProfile?.followersCount ?: 0L
                                            Text(
                                                text = buildString {
                                                    append(if (verified) "Verified Owner" else "Property Owner")
                                                    if (followers > 0) append(" • $followers followers")
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (room.ownerId != currentUserId) {
                                            OutlinedButton(
                                                onClick = { onToggleFollow(room.ownerId, room.ownerName) },
                                                modifier = Modifier.testTag("follow_owner_button")
                                            ) {
                                                Text(if (isFollowingOwner) "Following" else "Follow")
                                            }
                                        }
                                    }
                                }
                            }

                            // Verified Reviews Section
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val avgRating = if (reviews.isNotEmpty()) {
                                            String.format(Locale.US, "%.1f", reviews.map { it.rating }.average())
                                        } else null
                                        Text(
                                            text = if (avgRating != null) "Reviews ($avgRating ★ • ${reviews.size})" else "Reviews (${reviews.size})",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (eligibleBooking != null && reviews.none { it.reviewerId == currentUserId }) {
                                            TextButton(
                                                onClick = { showReviewDialog = true },
                                                modifier = Modifier.testTag("write_review_button")
                                            ) {
                                                Text("Write Review")
                                            }
                                        }
                                    }
                                    if (reviews.isEmpty()) {
                                        Text(
                                            "No verified reviews yet. Users who schedule a visit or confirm a booking can leave a review.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            items(reviews, key = { it.id }) { rev ->
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            UserAvatar(photoData = rev.reviewerPhoto, name = rev.reviewerName, size = 32.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(rev.reviewerName, style = MaterialTheme.typography.titleSmall)
                                                Row {
                                                    repeat(rev.rating.toInt().coerceIn(1, 5)) {
                                                        Icon(Icons.Filled.Star, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                            Text(formatRelativeDate(rev.createdAt), style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text(rev.comment, style = MaterialTheme.typography.bodyMedium)
                                        if (rev.ownerResponse.isNotBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(
                                                    text = "Owner reply: ${rev.ownerResponse}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Comments Section
                            item {
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Questions & Comments (${comments.size})", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = commentDraft,
                                        onValueChange = { commentDraft = it },
                                        placeholder = { Text("Ask a question about this room...") },
                                        modifier = Modifier.weight(1f).testTag("comment_input")
                                    )
                                    IconButton(
                                        onClick = {
                                            if (commentDraft.isNotBlank()) {
                                                viewModel.addComment(room, myName, myPhoto, commentDraft) {
                                                    commentDraft = ""
                                                }
                                            }
                                        },
                                        enabled = commentDraft.isNotBlank() && !isBusy,
                                        modifier = Modifier.testTag("post_comment_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post comment", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            items(comments, key = { it.id }) { comment ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    UserAvatar(photoData = comment.authorPhoto, name = comment.authorName, size = 34.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(comment.authorName, style = MaterialTheme.typography.titleSmall)
                                                Text(formatRelativeDate(comment.createdAt), style = MaterialTheme.typography.labelSmall)
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                                            if (comment.authorId == currentUserId) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    TextButton(onClick = { editingComment = comment }) {
                                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    TextButton(onClick = { viewModel.deleteComment(comment.id) }) {
                                                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Delete", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Dialogs
                        showBookingDialog?.let { type ->
                            val defaultDate = remember {
                                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() + 86_400_000L))
                            }
                            var dateText by remember { mutableStateOf(defaultDate) }
                            var msgText by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { showBookingDialog = null },
                                title = { Text(type.label) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = dateText,
                                            onValueChange = { dateText = it },
                                            label = { Text("Preferred Date (YYYY-MM-DD)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("booking_date_input")
                                        )
                                        OutlinedTextField(
                                            value = msgText,
                                            onValueChange = { msgText = it },
                                            label = { Text("Message to Owner (optional)") },
                                            minLines = 2,
                                            modifier = Modifier.fillMaxWidth().testTag("booking_message_input")
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.submitBooking(room, myName, type, dateText, msgText) {
                                                showBookingDialog = null
                                            }
                                        },
                                        enabled = dateText.isNotBlank() && !isBusy,
                                        modifier = Modifier.testTag("confirm_booking_request_button")
                                    ) { Text("Send Request") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showBookingDialog = null }) { Text("Cancel") }
                                }
                            )
                        }

                        if (showReviewDialog && eligibleBooking != null) {
                            var stars by remember { mutableIntStateOf(5) }
                            var revText by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { showReviewDialog = false },
                                title = { Text("Rate Your Experience") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            (1..5).forEach { s ->
                                                IconButton(onClick = { stars = s }) {
                                                    Icon(
                                                        imageVector = if (s <= stars) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                                        contentDescription = "$s stars",
                                                        tint = AmberAccent
                                                    )
                                                }
                                            }
                                        }
                                        OutlinedTextField(
                                            value = revText,
                                            onValueChange = { revText = it },
                                            label = { Text("Your review (min 3 chars)") },
                                            minLines = 3,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.submitReview(room, eligibleBooking!!.id, myName, myPhoto, stars, revText) {
                                                showReviewDialog = false
                                            }
                                        },
                                        enabled = revText.trim().length >= 3 && !isBusy
                                    ) { Text("Submit") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showReviewDialog = false }) { Text("Cancel") }
                                }
                            )
                        }

                        if (showReportDialog) {
                            var reason by remember { mutableStateOf("Inaccurate info") }
                            var details by remember { mutableStateOf("") }
                            val reasons = listOf("Inaccurate info", "Already rented", "Suspicious / Spam", "Wrong location")
                            AlertDialog(
                                onDismissRequest = { showReportDialog = false },
                                title = { Text("Report Listing") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            reasons.forEach { r ->
                                                FilterChip(
                                                    selected = reason == r,
                                                    onClick = { reason = r },
                                                    label = { Text(r) }
                                                )
                                            }
                                        }
                                        OutlinedTextField(
                                            value = details,
                                            onValueChange = { details = it },
                                            label = { Text("Additional details (optional)") },
                                            minLines = 2,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.submitReport(reason, details) { showReportDialog = false }
                                        }
                                    ) { Text("Report") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
                                }
                            )
                        }

                        editingComment?.let { target ->
                            var updatedText by remember(target.id) { mutableStateOf(target.text) }
                            AlertDialog(
                                onDismissRequest = { editingComment = null },
                                title = { Text("Edit Comment") },
                                text = {
                                    OutlinedTextField(
                                        value = updatedText,
                                        onValueChange = { updatedText = it },
                                        minLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.editComment(target.id, updatedText) { editingComment = null }
                                        },
                                        enabled = updatedText.isNotBlank()
                                    ) { Text("Save") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { editingComment = null }) { Text("Cancel") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
