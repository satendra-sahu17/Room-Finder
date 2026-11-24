package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BookingRequest
import com.example.data.model.BookingStatus
import com.example.data.model.BookingType
import com.example.data.model.RoomComment
import com.example.data.model.RoomListing
import com.example.data.model.RoomReview
import com.example.data.model.UiState
import com.example.data.repository.BookingRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.RoomRepository
import com.example.data.util.unwrapFirestoreError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoomDetailViewModel(
    private val roomRepository: RoomRepository,
    private val bookingRepository: BookingRepository,
    private val chatRepository: ChatRepository,
    val roomId: String,
    val currentUserId: String
) : ViewModel() {

    val roomState: StateFlow<UiState<RoomListing?>> = roomRepository.observeRoom(roomId)
        .map<RoomListing?, UiState<RoomListing?>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            emit(UiState.Error(root.message ?: "Unable to load room"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val comments: StateFlow<List<RoomComment>> = roomRepository.observeComments(roomId)
        .catch { error ->
            unwrapFirestoreError(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val reviews: StateFlow<List<RoomReview>> = roomRepository.observeReviews(roomId)
        .catch { error ->
            unwrapFirestoreError(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val eligibleBookingForReview: StateFlow<BookingRequest?> = bookingRepository.observeMyBookings(currentUserId)
        .map { list ->
            list.firstOrNull {
                it.roomId == roomId && it.seekerId == currentUserId &&
                    it.status in listOf(
                        BookingStatus.VISIT_SCHEDULED.name,
                        BookingStatus.ACCEPTED.name,
                        BookingStatus.CONFIRMED.name
                    )
            }
        }
        .catch { error ->
            unwrapFirestoreError(error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            roomRepository.incrementRoomView(roomId)
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun addComment(room: RoomListing, authorName: String, authorPhoto: String, text: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            roomRepository.addComment(room, authorName, authorPhoto, text)
                .onSuccess { onDone() }
                .onFailure { _feedback.value = it.localizedMessage ?: "Failed to post comment" }
            _isBusy.value = false
        }
    }

    fun editComment(commentId: String, newText: String, onDone: () -> Unit) {
        viewModelScope.launch {
            roomRepository.editComment(roomId, commentId, newText)
                .onSuccess { onDone() }
                .onFailure { _feedback.value = it.localizedMessage ?: "Failed to update comment" }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            roomRepository.deleteComment(roomId, commentId)
                .onFailure { _feedback.value = it.localizedMessage ?: "Failed to delete comment" }
        }
    }

    fun submitBooking(
        room: RoomListing,
        seekerName: String,
        type: BookingType,
        preferredDate: String,
        message: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            bookingRepository.createBookingRequest(room, seekerName, type, preferredDate, message)
                .onSuccess {
                    _feedback.value = if (type == BookingType.VISIT) "Visit request sent!" else "Booking request sent!"
                    onSuccess()
                }
                .onFailure { _feedback.value = it.localizedMessage ?: "Could not submit request" }
            _isBusy.value = false
        }
    }

    fun submitReview(
        room: RoomListing,
        bookingId: String,
        reviewerName: String,
        reviewerPhoto: String,
        rating: Int,
        comment: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            roomRepository.submitReview(room, bookingId, reviewerName, reviewerPhoto, rating, comment)
                .onSuccess {
                    _feedback.value = "Review published"
                    onSuccess()
                }
                .onFailure { _feedback.value = it.localizedMessage ?: "Failed to submit review" }
            _isBusy.value = false
        }
    }

    fun replyToReview(reviewId: String, response: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            roomRepository.replyToReview(roomId, reviewId, response)
                .onSuccess { onSuccess() }
                .onFailure { _feedback.value = it.localizedMessage ?: "Failed to post reply" }
        }
    }

    fun submitReport(reason: String, details: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            roomRepository.submitReport("ROOM", roomId, reason, details)
                .onSuccess {
                    _feedback.value = "Report submitted for review"
                    onSuccess()
                }
                .onFailure { _feedback.value = it.localizedMessage ?: "Failed to submit report" }
        }
    }

    fun startChatWithOwner(
        myName: String,
        myPhoto: String,
        room: RoomListing,
        onConversationReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            chatRepository.getOrCreateConversation(
                myName = myName,
                myPhoto = myPhoto,
                otherUserId = room.ownerId,
                otherName = room.ownerName,
                otherPhoto = room.ownerPhoto,
                roomId = room.id,
                roomTitle = room.title
            ).onSuccess { convId ->
                onConversationReady(convId)
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Unable to start chat"
            }
            _isBusy.value = false
        }
    }
}
