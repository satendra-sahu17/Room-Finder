package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BathroomType
import com.example.data.model.FurnishingType
import com.example.data.model.GenderPreference
import com.example.data.model.RoomCategory
import com.example.data.model.RoomFacilities
import com.example.data.model.RoomListing
import com.example.data.model.RoomStatus
import com.example.data.model.UserAccount
import com.example.data.model.UserLocationState
import com.example.data.util.GeoUtils
import com.example.data.util.ImageUtils
import com.example.ui.components.RoomImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditRoomScreen(
    existingRoom: RoomListing?,
    currentUser: UserAccount?,
    userLocation: UserLocationState,
    isSaving: Boolean,
    onRequestLocation: () -> Unit,
    onSaveRoom: (RoomListing, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember(existingRoom) { mutableStateOf(existingRoom?.title ?: "") }
    var description by remember(existingRoom) { mutableStateOf(existingRoom?.description ?: "") }
    var category by remember(existingRoom) { mutableStateOf(existingRoom?.categoryEnum() ?: RoomCategory.SINGLE_ROOM) }
    var rentText by remember(existingRoom) { mutableStateOf(existingRoom?.monthlyRent?.takeIf { it > 0 }?.toString() ?: "") }
    var depositText by remember(existingRoom) { mutableStateOf(existingRoom?.securityDeposit?.takeIf { it > 0 }?.toString() ?: "") }
    var sizeText by remember(existingRoom) { mutableStateOf(existingRoom?.roomSizeSqFt?.takeIf { it > 0 }?.toString() ?: "") }
    var occupantsText by remember(existingRoom) { mutableStateOf((existingRoom?.occupants ?: 1L).toString()) }
    var availableFrom by remember(existingRoom) {
        mutableStateOf(
            existingRoom?.availableFrom?.ifBlank { null }
                ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
    }

    var furnishing by remember(existingRoom) { mutableStateOf(existingRoom?.furnishingEnum() ?: FurnishingType.FURNISHED) }
    var bathroom by remember(existingRoom) { mutableStateOf(existingRoom?.bathroomEnum() ?: BathroomType.ATTACHED) }
    var genderPref by remember(existingRoom) { mutableStateOf(existingRoom?.preferenceEnum() ?: GenderPreference.ANYONE) }

    val selectedFacilities = remember(existingRoom) {
        mutableStateListOf<String>().apply {
            addAll(existingRoom?.facilities ?: listOf("Wi-Fi", "24/7 Water"))
        }
    }

    var country by remember(existingRoom) { mutableStateOf(existingRoom?.country?.ifBlank { "India" } ?: "India") }
    var state by remember(existingRoom) { mutableStateOf(existingRoom?.state ?: "") }
    var city by remember(existingRoom) { mutableStateOf(existingRoom?.city ?: userLocation.city) }
    var area by remember(existingRoom) { mutableStateOf(existingRoom?.area ?: userLocation.area) }
    var address by remember(existingRoom) { mutableStateOf(existingRoom?.address ?: "") }
    var postalCode by remember(existingRoom) { mutableStateOf(existingRoom?.postalCode ?: "") }
    var hideExactAddress by remember(existingRoom) { mutableStateOf(existingRoom?.hideExactAddress ?: false) }

    var latText by remember(existingRoom) {
        mutableStateOf(
            existingRoom?.latitude?.takeIf { it != 0.0 }?.toString()
                ?: userLocation.latitude?.toString() ?: ""
        )
    }
    var lngText by remember(existingRoom) {
        mutableStateOf(
            existingRoom?.longitude?.takeIf { it != 0.0 }?.toString()
                ?: userLocation.longitude?.toString() ?: ""
        )
    }

    var videoUrl by remember(existingRoom) { mutableStateOf(existingRoom?.videoUrl ?: "") }
    val photos = remember(existingRoom) {
        mutableStateListOf<String>().apply {
            addAll(existingRoom?.galleryPhotos() ?: emptyList())
        }
    }
    var coverIndex by remember { mutableIntStateOf(0) }
    var isProcessingImages by remember { mutableStateOf(false) }
    var isGeocoding by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessingImages = true
                for (uri in uris) {
                    if (photos.size >= 4) break
                    val compressed = ImageUtils.compressUriToDataUri(context, uri)
                    if (compressed != null) {
                        photos.add(compressed)
                    }
                }
                isProcessingImages = false
            }
        }
    }

    fun submitForm(publishImmediately: Boolean) {
        val cleanTitle = title.trim()
        val cleanDesc = description.trim()
        val rent = rentText.toLongOrNull() ?: 0L
        val lat = latText.toDoubleOrNull()
        val lng = lngText.toDoubleOrNull()

        when {
            cleanTitle.length < 3 -> formError = "Room title must be at least 3 characters."
            cleanDesc.length < 10 -> formError = "Description must be at least 10 characters."
            rent < 500L -> formError = "Monthly rent must be at least ₹500."
            city.isBlank() || area.isBlank() -> formError = "City and Area/Locality are required."
            lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0 ->
                formError = "Valid coordinates required. Tap 'Use GPS' or 'Lookup Coords'."
            else -> {
                formError = null
                val orderedPhotos = photos.toList()
                val cover = orderedPhotos.getOrNull(coverIndex) ?: orderedPhotos.firstOrNull() ?: ""
                val listing = RoomListing(
                    id = existingRoom?.id ?: "",
                    ownerId = currentUser?.userId ?: "",
                    ownerName = currentUser?.fullName?.ifBlank { "Room Owner" } ?: "Room Owner",
                    ownerPhoto = currentUser?.photoData ?: "",
                    ownerVerified = currentUser?.verified ?: false,
                    title = cleanTitle,
                    description = cleanDesc,
                    category = category.name,
                    monthlyRent = rent,
                    securityDeposit = depositText.toLongOrNull() ?: 0L,
                    roomSizeSqFt = sizeText.toLongOrNull() ?: 0L,
                    occupants = (occupantsText.toLongOrNull() ?: 1L).coerceIn(1L, 20L),
                    availableFrom = availableFrom.trim(),
                    furnishing = furnishing.name,
                    bathroomType = bathroom.name,
                    genderPreference = genderPref.name,
                    facilities = selectedFacilities.toList(),
                    country = country.trim().ifBlank { "India" },
                    state = state.trim(),
                    city = city.trim(),
                    area = area.trim(),
                    address = address.trim(),
                    postalCode = postalCode.trim(),
                    hideExactAddress = hideExactAddress,
                    latitude = lat,
                    longitude = lng,
                    coverPhoto = cover,
                    photos = orderedPhotos,
                    videoUrl = videoUrl.trim(),
                    status = if (publishImmediately) RoomStatus.AVAILABLE.name else RoomStatus.PAUSED.name
                )
                onSaveRoom(listing, publishImmediately)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingRoom == null) "Post a Room" else "Edit Room") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("add_room_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                formError?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Photos Picker
                Text("Room Photos (up to 4)", style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            multiPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = photos.size < 4 && !isProcessingImages,
                        modifier = Modifier.testTag("pick_room_photos_button")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isProcessingImages) "Compressing..." else "Add Photos (${photos.size}/4)")
                    }
                    if (isProcessingImages) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }

                if (photos.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(photos) { idx, photo ->
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .border(
                                        width = if (idx == coverIndex) 2.5.dp else 1.dp,
                                        color = if (idx == coverIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .clickable { coverIndex = idx }
                            ) {
                                RoomImage(
                                    imageSource = photo,
                                    contentDescription = "Room photo ${idx + 1}",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = if (idx == coverIndex) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (idx == coverIndex) {
                                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                        }
                                        Text(
                                            text = if (idx == coverIndex) "Cover" else "Set Cover",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        photos.removeAt(idx)
                                        if (coverIndex >= photos.size) coverIndex = 0
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Basic Information
                Text("Basic Information", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Room Title *") },
                    placeholder = { Text("e.g. Sunlit Single Room near Vijay Nagar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("room_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Detailed Description * (min 10 chars)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("room_description_input")
                )

                Text("Room Category", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoomCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = rentText,
                        onValueChange = { rentText = it.filter { c -> c.isDigit() } },
                        label = { Text("Monthly Rent (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("room_rent_input")
                    )
                    OutlinedTextField(
                        value = depositText,
                        onValueChange = { depositText = it.filter { c -> c.isDigit() } },
                        label = { Text("Security Deposit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sizeText,
                        onValueChange = { sizeText = it.filter { c -> c.isDigit() } },
                        label = { Text("Size (sq.ft)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = occupantsText,
                        onValueChange = { occupantsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Occupants") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = availableFrom,
                        onValueChange = { availableFrom = it },
                        label = { Text("Available From") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                // Property Details
                Text("Furnishing & Bathroom", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FurnishingType.entries.forEach { f ->
                        FilterChip(selected = furnishing == f, onClick = { furnishing = f }, label = { Text(f.label) })
                    }
                    BathroomType.entries.forEach { b ->
                        FilterChip(selected = bathroom == b, onClick = { bathroom = b }, label = { Text(b.label) })
                    }
                }

                Text("Preferred Tenants", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GenderPreference.entries.forEach { p ->
                        FilterChip(selected = genderPref == p, onClick = { genderPref = p }, label = { Text(p.label) })
                    }
                }

                Text("Facilities", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoomFacilities.allFacilities.forEach { fac ->
                        val checked = fac in selectedFacilities
                        FilterChip(
                            selected = checked,
                            onClick = {
                                if (checked) selectedFacilities.remove(fac) else selectedFacilities.add(fac)
                            },
                            label = { Text(fac) }
                        )
                    }
                }

                // Location & Coordinates
                Text("Location & GPS Coordinates", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onRequestLocation()
                            if (userLocation.hasCoordinates) {
                                latText = userLocation.latitude.toString()
                                lngText = userLocation.longitude.toString()
                                if (city.isBlank()) city = userLocation.city
                                if (area.isBlank()) area = userLocation.area
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("use_current_gps_button")
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use GPS")
                    }
                    OutlinedButton(
                        onClick = {
                            val q = listOf(address, area, city, state, country).filter { it.isNotBlank() }.joinToString(", ")
                            if (q.isNotBlank()) {
                                scope.launch {
                                    isGeocoding = true
                                    val geo = GeoUtils.forwardGeocode(context, q)
                                    if (geo != null) {
                                        latText = geo.latitude.toString()
                                        lngText = geo.longitude.toString()
                                        if (city.isBlank()) city = geo.city
                                        if (area.isBlank()) area = geo.area
                                        if (state.isBlank()) state = geo.state
                                        if (postalCode.isBlank()) postalCode = geo.postalCode
                                    } else {
                                        formError = "Could not geocode address. Try City + Area or Use GPS."
                                    }
                                    isGeocoding = false
                                }
                            }
                        },
                        enabled = !isGeocoding,
                        modifier = Modifier.weight(1f).testTag("lookup_coords_button")
                    ) {
                        Icon(Icons.Filled.LocationSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isGeocoding) "Finding..." else "Lookup Coords")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("room_city_input")
                    )
                    OutlinedTextField(
                        value = area,
                        onValueChange = { area = it },
                        label = { Text("Area / Locality *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("room_area_input")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        label = { Text("State") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text("Postal Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Complete Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("room_lat_input")
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("Longitude *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("room_lng_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Privacy: Hide Exact Street Address", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Show only neighborhood & approximate map location publicly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = hideExactAddress, onCheckedChange = { hideExactAddress = it })
                }

                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("Room Video Tour URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { submitForm(publishImmediately = false) },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f).testTag("save_draft_room_button")
                    ) {
                        Text("Save Draft")
                    }
                    Button(
                        onClick = { submitForm(publishImmediately = true) },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1.3f).testTag("publish_room_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (existingRoom == null) "Publish Room" else "Save Changes")
                        }
                    }
                }
            }
        }
    }
}
