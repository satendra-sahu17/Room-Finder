package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AccountType(val label: String) {
    SEEKER("Room Seeker"),
    OWNER("Room Owner"),
    BOTH("Seeker & Owner");

    companion object {
        fun fromRaw(raw: String?): AccountType =
            entries.firstOrNull { it.name == raw } ?: BOTH
    }
}

enum class RoomCategory(val label: String) {
    SINGLE_ROOM("Single Room"),
    DOUBLE_SHARING("Double Sharing"),
    TRIPLE_SHARING("Triple Sharing"),
    SHARED_ROOM("Shared Room"),
    PG("PG"),
    ONE_BHK("1 BHK"),
    TWO_BHK("2 BHK"),
    FLAT("Flat"),
    HOSTEL("Hostel");

    companion object {
        fun fromRaw(raw: String?): RoomCategory =
            entries.firstOrNull { it.name == raw } ?: SINGLE_ROOM
    }
}

enum class FurnishingType(val label: String) {
    FURNISHED("Furnished"),
    SEMI_FURNISHED("Semi-Furnished"),
    UNFURNISHED("Unfurnished");

    companion object {
        fun fromRaw(raw: String?): FurnishingType =
            entries.firstOrNull { it.name == raw } ?: SEMI_FURNISHED
    }
}

enum class BathroomType(val label: String) {
    ATTACHED("Attached Bath"),
    SHARED("Shared Bath");

    companion object {
        fun fromRaw(raw: String?): BathroomType =
            entries.firstOrNull { it.name == raw } ?: ATTACHED
    }
}

enum class GenderPreference(val label: String) {
    ANYONE("Anyone"),
    MALE("Male"),
    FEMALE("Female"),
    STUDENTS("Students"),
    PROFESSIONALS("Working Professionals"),
    FAMILY("Family");

    companion object {
        fun fromRaw(raw: String?): GenderPreference =
            entries.firstOrNull { it.name == raw } ?: ANYONE
    }
}

enum class RoomStatus(val label: String) {
    AVAILABLE("Available"),
    PAUSED("Paused"),
    RENTED("Rented"),
    REJECTED("Rejected");

    companion object {
        fun fromRaw(raw: String?): RoomStatus =
            entries.firstOrNull { it.name == raw } ?: AVAILABLE
    }
}

enum class BookingType(val label: String) {
    VISIT("Schedule Visit"),
    BOOKING("Book Room");

    companion object {
        fun fromRaw(raw: String?): BookingType =
            entries.firstOrNull { it.name == raw } ?: VISIT
    }
}

enum class BookingStatus(val label: String) {
    PENDING("Pending"),
    VISIT_SCHEDULED("Visit Scheduled"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    CONFIRMED("Confirmed"),
    CANCELLED("Cancelled");

    companion object {
        fun fromRaw(raw: String?): BookingStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class FeedTab(val label: String) {
    RECOMMENDED("Recommended"),
    NEARBY("Nearby"),
    LATEST("Latest"),
    POPULAR("Popular")
}

enum class SortOption(val label: String) {
    NEAREST("Nearest First"),
    LOWEST_PRICE("Lowest Price"),
    HIGHEST_PRICE("Highest Price"),
    RECENT("Recently Added"),
    POPULAR("Most Popular")
}

object RoomFacilities {
    val allFacilities = listOf(
        "Wi-Fi",
        "Air Conditioning",
        "Kitchen",
        "Balcony",
        "Parking",
        "Laundry",
        "Food Included",
        "24/7 Water",
        "Power Backup",
        "CCTV Security",
        "Geyser",
        "Study Desk"
    )
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val bio: String = "",
    val city: String = "",
    val accountType: String = AccountType.BOTH.name,
    val photoData: String = "",
    val verified: Boolean = false,
    val showPhone: Boolean = false,
    val showEmail: Boolean = false,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val username: String = "",
    val bio: String = "",
    val city: String = "",
    val accountType: String = AccountType.BOTH.name,
    val photoData: String = "",
    val verified: Boolean = false,
    val visibility: String = "PUBLIC",
    val ratingAvg: Double = 0.0,
    val ratingCount: Long = 0L,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RoomListing(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val ownerPhoto: String = "",
    val ownerVerified: Boolean = false,
    val title: String = "",
    val description: String = "",
    val category: String = RoomCategory.SINGLE_ROOM.name,
    val monthlyRent: Long = 0L,
    val securityDeposit: Long = 0L,
    val roomSizeSqFt: Long = 0L,
    val occupants: Long = 1L,
    val availableFrom: String = "",
    val furnishing: String = FurnishingType.SEMI_FURNISHED.name,
    val bathroomType: String = BathroomType.ATTACHED.name,
    val genderPreference: String = GenderPreference.ANYONE.name,
    val facilities: List<String> = emptyList(),
    val country: String = "India",
    val state: String = "",
    val city: String = "",
    val area: String = "",
    val address: String = "",
    val postalCode: String = "",
    val hideExactAddress: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val coverPhoto: String = "",
    val photos: List<String> = emptyList(),
    val videoUrl: String = "",
    val status: String = RoomStatus.AVAILABLE.name,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val viewsCount: Long = 0L,
    val favouritesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): RoomCategory = RoomCategory.fromRaw(category)
    fun furnishingEnum(): FurnishingType = FurnishingType.fromRaw(furnishing)
    fun bathroomEnum(): BathroomType = BathroomType.fromRaw(bathroomType)
    fun preferenceEnum(): GenderPreference = GenderPreference.fromRaw(genderPreference)
    fun statusEnum(): RoomStatus = RoomStatus.fromRaw(status)

    fun galleryPhotos(): List<String> {
        val combined = buildList {
            if (coverPhoto.isNotBlank()) add(coverPhoto)
            photos.forEach { if (it.isNotBlank() && it != coverPhoto) add(it) }
        }
        return combined
    }

    fun displayAddress(): String {
        return if (hideExactAddress) {
            listOf(area, city, state).filter { it.isNotBlank() }.joinToString(", ")
        } else {
            listOf(address, area, city, state, postalCode).filter { it.isNotBlank() }.joinToString(", ")
        }
    }
}

@Keep
@IgnoreExtraProperties
data class RoomComment(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val roomOwnerId: String = "",
    val roomStatus: String = RoomStatus.AVAILABLE.name,
    val authorId: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FavouriteItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val roomTitle: String = "",
    val roomCity: String = "",
    val roomRent: Long = 0L,
    val roomPhoto: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FollowRelation(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val targetUserId: String = "",
    val targetName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val roomId: String = "",
    val roomTitle: String = "",
    val lastMessage: String = "",
    val lastSenderId: String = "",
    val unreadBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val text: String = "",
    val imageData: String = "",
    val readStatus: String = "SENT",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BookingRequest(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val roomTitle: String = "",
    val roomCity: String = "",
    val roomRent: Long = 0L,
    val roomPhoto: String = "",
    val seekerId: String = "",
    val seekerName: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val participantIds: List<String> = emptyList(),
    val bookingType: String = BookingType.VISIT.name,
    val preferredDate: String = "",
    val message: String = "",
    val status: String = BookingStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): BookingStatus = BookingStatus.fromRaw(status)
    fun typeEnum(): BookingType = BookingType.fromRaw(bookingType)
}

@Keep
@IgnoreExtraProperties
data class RoomReview(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val roomOwnerId: String = "",
    val roomStatus: String = RoomStatus.AVAILABLE.name,
    val reviewerId: String = "",
    val reviewerName: String = "",
    val reviewerPhoto: String = "",
    val bookingId: String = "",
    val rating: Long = 5L,
    val comment: String = "",
    val ownerResponse: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: String = "BOOKING_REQUEST",
    val title: String = "",
    val body: String = "",
    val targetId: String = "",
    val readStatus: String = "UNREAD",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ListingReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "ROOM",
    val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "OPEN",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class RoomFilterState(
    val query: String = "",
    val minRent: Int? = null,
    val maxRent: Int? = null,
    val maxDistanceKm: Double? = null, // null = Any distance
    val category: RoomCategory? = null,
    val furnishing: FurnishingType? = null,
    val bathroomType: BathroomType? = null,
    val genderPreference: GenderPreference? = null,
    val foodOnly: Boolean = false,
    val parkingOnly: Boolean = false,
    val sortOption: SortOption = SortOption.NEAREST
) {
    fun activeFilterCount(): Int {
        var count = 0
        if (minRent != null || maxRent != null) count++
        if (maxDistanceKm != null) count++
        if (category != null) count++
        if (furnishing != null) count++
        if (bathroomType != null) count++
        if (genderPreference != null) count++
        if (foodOnly) count++
        if (parkingOnly) count++
        return count
    }
}

data class UserLocationState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String = "",
    val area: String = "",
    val permissionGranted: Boolean = false,
    val isFetching: Boolean = false,
    val error: String? = null
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0)
}
