package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Conversation
import com.example.data.model.UiState
import com.example.data.util.ImageUtils
import com.example.ui.components.EmptyStateView
import com.example.ui.components.RoomImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeDate
import com.example.ui.viewmodel.WorkspaceViewModel
import kotlinx.coroutines.launch

@Composable
fun ConversationsListScreen(
    conversationsState: UiState<List<Conversation>>,
    currentUserId: String,
    onConversationClick: (String) -> Unit
) {
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
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text("Messages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Direct real-time chats with owners & seekers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            when (conversationsState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> EmptyStateView(
                    title = "Couldn't load messages",
                    subtitle = conversationsState.message
                )
                is UiState.Success -> {
                    if (conversationsState.data.isEmpty()) {
                        EmptyStateView(
                            title = "No conversations yet",
                            subtitle = "Tap Chat on any room listing to ask questions or schedule a visit."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(conversationsState.data, key = { it.id }) { conv ->
                                val otherId = conv.participantIds.firstOrNull { it != currentUserId } ?: ""
                                val otherName = conv.participantNames[otherId] ?: "RoomFinder Member"
                                val otherPhoto = conv.participantPhotos[otherId] ?: ""
                                val isUnread = currentUserId in conv.unreadBy

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("conversation_card_${conv.id}")
                                        .clickable { onConversationClick(conv.id) },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UserAvatar(photoData = otherPhoto, name = otherName, size = 48.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = otherName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = formatRelativeDate(conv.updatedAt ?: conv.createdAt),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (conv.roomTitle.isNotBlank()) {
                                                Text(
                                                    text = conv.roomTitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = conv.lastMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (isUnread) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    conversationsState: UiState<List<Conversation>>,
    workspaceViewModel: WorkspaceViewModel,
    currentUserId: String,
    myName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conversation = (conversationsState as? UiState.Success)?.data?.firstOrNull { it.id == conversationId }
    val messages by remember(conversationId) {
        workspaceViewModel.observeMessagesFor(conversationId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val listState = rememberLazyListState()
    var messageDraft by remember { mutableStateOf("") }
    var isCompressing by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && conversation != null) {
            scope.launch {
                isCompressing = true
                val dataUri = ImageUtils.compressUriToDataUri(context, uri, maxDimension = 520, quality = 60)
                if (dataUri != null) {
                    workspaceViewModel.sendMessage(conversation, myName, "", dataUri) {}
                }
                isCompressing = false
            }
        }
    }

    LaunchedEffect(messages.size, conversation?.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
        if (conversation != null) {
            workspaceViewModel.markConversationRead(conversation, messages)
        }
    }

    val otherId = conversation?.participantIds?.firstOrNull { it != currentUserId } ?: ""
    val otherName = conversation?.participantNames?.get(otherId) ?: "Chat"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(otherName, style = MaterialTheme.typography.titleMedium)
                        val roomTitle = conversation?.roomTitle
                        if (!roomTitle.isNullOrBlank()) {
                            Text(
                                roomTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (conversation != null) {
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !isCompressing,
                            modifier = Modifier.testTag("chat_attach_image_button")
                        ) {
                            if (isCompressing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.Image, contentDescription = "Send photo")
                        }
                        OutlinedTextField(
                            value = messageDraft,
                            onValueChange = { messageDraft = it },
                            placeholder = { Text("Type a message...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.weight(1f).testTag("chat_message_input")
                        )
                        IconButton(
                            onClick = {
                                if (messageDraft.isNotBlank()) {
                                    workspaceViewModel.sendMessage(conversation, myName, messageDraft) {
                                        messageDraft = ""
                                    }
                                }
                            },
                            enabled = messageDraft.isNotBlank(),
                            modifier = Modifier.testTag("chat_send_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = MaterialTheme.colorScheme.primary
                            )
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
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMine) 16.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 16.dp
                            ),
                            modifier = Modifier.widthIn(max = 290.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (msg.imageData.isNotBlank()) {
                                    RoomImage(
                                        imageSource = msg.imageData,
                                        contentDescription = "Shared photo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(MaterialTheme.shapes.small)
                                    )
                                }
                                if (msg.text.isNotBlank()) {
                                    Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formatRelativeDate(msg.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isMine && msg.readStatus == "READ") {
                                        Icon(
                                            imageVector = Icons.Filled.DoneAll,
                                            contentDescription = "Read",
                                            modifier = Modifier.size(14.dp)
                                        )
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
