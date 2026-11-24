package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BookingRequest
import com.example.data.model.BookingStatus
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ListingReport
import com.example.data.model.RoomListing
import com.example.data.model.RoomStatus
import com.example.data.model.UiState
import com.example.data.repository.AdminRepository
import com.example.data.repository.BookingRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.RoomRepository
import com.example.data.util.unwrapFirestoreError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val roomRepository: RoomRepository,
    private val chatRepository: ChatRepository,
    private val bookingRepository: BookingRepository,
    private val adminRepository: AdminRepository,
    val currentUserId: String
) : ViewModel() {

    val ownerRooms: StateFlow<UiState<List<RoomListing>>> = roomRepository.observeOwnerRooms(currentUserId)
        .map<List<RoomListing>, UiState<List<RoomListing>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            emit(UiState.Error(root.message ?: "Failed to load your listings"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val conversations: StateFlow<UiState<List<Conversation>>> = chatRepository.observeConversations(currentUserId)
        .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            emit(UiState.Error(root.message ?: "Failed to load messages"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val bookings: StateFlow<UiState<List<BookingRequest>>> = bookingRepository.observeMyBookings(currentUserId)
        .map<List<BookingRequest>, UiState<List<BookingRequest>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            emit(UiState.Error(root.message ?: "Failed to load bookings"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _isSavingRoom = MutableStateFlow(false)
    val isSavingRoom: StateFlow<Boolean> = _isSavingRoom.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun saveRoom(existingRoomId: String?, room: RoomListing, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            _isSavingRoom.value = true
            roomRepository.saveRoom(existingRoomId, room)
                .onSuccess { id ->
                    _statusMessage.value = if (existingRoomId == null) "Listing published!" else "Listing updated!"
                    onSaved(id)
                }
                .onFailure {
                    _statusMessage.value = it.localizedMessage ?: "Failed to save listing"
                }
            _isSavingRoom.value = false
        }
    }

    fun updateRoomStatus(roomId: String, status: RoomStatus) {
        viewModelScope.launch {
            roomRepository.updateRoomStatus(roomId, status)
                .onSuccess { _statusMessage.value = "Listing marked ${status.label}" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to update status" }
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            roomRepository.deleteRoom(roomId)
                .onSuccess { _statusMessage.value = "Listing deleted" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to delete listing" }
        }
    }

    fun observeMessagesFor(conversationId: String): Flow<List<ChatMessage>> =
        if (conversationId.isBlank()) flowOf(emptyList())
        else chatRepository.observeMessages(conversationId, currentUserId)
            .catch { error ->
                unwrapFirestoreError(error)
                emit(emptyList())
            }

    fun sendMessage(
        conversation: Conversation,
        senderName: String,
        text: String,
        imageData: String = "",
        onSent: () -> Unit
    ) {
        val otherId = conversation.participantIds.firstOrNull { it != currentUserId } ?: return
        viewModelScope.launch {
            chatRepository.sendMessage(
                conversationId = conversation.id,
                participantIds = conversation.participantIds,
                otherUserId = otherId,
                senderName = senderName,
                text = text,
                imageData = imageData
            ).onSuccess { onSent() }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to send message" }
        }
    }

    fun markConversationRead(conversation: Conversation, messages: List<ChatMessage>) {
        viewModelScope.launch {
            chatRepository.markConversationRead(conversation, messages, currentUserId)
        }
    }

    fun updateBookingStatus(
        booking: BookingRequest,
        newStatus: BookingStatus,
        actorName: String,
        preferredDate: String = booking.preferredDate
    ) {
        viewModelScope.launch {
            bookingRepository.updateBookingStatus(booking, newStatus, actorName, preferredDate)
                .onSuccess { _statusMessage.value = "Request updated to ${newStatus.label}" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to update booking" }
        }
    }

    fun observeAdminReports(): Flow<List<ListingReport>> =
        adminRepository.observeOpenReports()
            .catch { error ->
                unwrapFirestoreError(error)
                emit(emptyList())
            }

    fun resolveReport(report: ListingReport, removeListing: Boolean) {
        viewModelScope.launch {
            adminRepository.resolveReport(report, removeListing)
                .onSuccess {
                    _statusMessage.value = if (removeListing) "Listing removed" else "Report dismissed"
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Action failed" }
        }
    }

    fun verifyOwner(userId: String) {
        viewModelScope.launch {
            adminRepository.verifyOwner(userId)
                .onSuccess { _statusMessage.value = "Owner verified" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Verification failed" }
        }
    }
}
