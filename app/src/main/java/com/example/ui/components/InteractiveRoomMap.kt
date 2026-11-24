package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.model.UserLocationState
import com.example.data.util.GeoUtils
import com.example.ui.viewmodel.RoomWithDistance
import kotlin.math.cos
import kotlin.math.roundToInt

@Composable
fun InteractiveRoomMap(
    rooms: List<RoomWithDistance>,
    userLocation: UserLocationState,
    radiusKm: Double?,
    onRoomSelected: (String) -> Unit,
    onRequestCenterLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val validRooms = remember(rooms) {
        rooms.filter { it.room.latitude != 0.0 || it.room.longitude != 0.0 }
    }

    val centerLat = remember(userLocation, validRooms) {
        when {
            userLocation.hasCoordinates -> userLocation.latitude!!
            validRooms.isNotEmpty() -> validRooms.map { it.room.latitude }.average()
            else -> 23.1815 // Default viewport reference
        }
    }
    val centerLng = remember(userLocation, validRooms) {
        when {
            userLocation.hasCoordinates -> userLocation.longitude!!
            validRooms.isNotEmpty() -> validRooms.map { it.room.longitude }.average()
            else -> 79.9864
        }
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedItem by remember(rooms) { mutableStateOf(validRooms.firstOrNull()) }

    // 1 degree lat ~= 111 km; base span ~ 16 km across screen at zoom 1f
    val kmPerHalfScreen = (8.0 / zoomScale).coerceIn(0.4, 80.0)

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val roadColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val userDotColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(bgColor)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    panOffset += dragAmount
                }
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val minHalfPx = minOf(widthPx, heightPx) / 2f

        fun latLngToScreen(lat: Double, lng: Double): Offset {
            val dLatKm = (lat - centerLat) * 111.0
            val dLngKm = (lng - centerLng) * 111.0 * cos(Math.toRadians(centerLat))
            val pxPerKm = minHalfPx / kmPerHalfScreen.toFloat()
            val x = widthPx / 2f + panOffset.x + (dLngKm.toFloat() * pxPerKm)
            val y = heightPx / 2f + panOffset.y - (dLatKm.toFloat() * pxPerKm)
            return Offset(x, y)
        }

        // Architectural street grid & distance rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 56.dp.toPx()
            var x = (panOffset.x % step + step) % step
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = (panOffset.y % step + step) % step
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }

            // Diagonal avenues for cartographic depth
            drawLine(
                color = roadColor,
                start = Offset(0f, size.height * 0.25f + panOffset.y * 0.4f),
                end = Offset(size.width, size.height * 0.72f + panOffset.y * 0.4f),
                strokeWidth = 6f
            )
            drawLine(
                color = roadColor,
                start = Offset(size.width * 0.2f + panOffset.x * 0.4f, 0f),
                end = Offset(size.width * 0.75f + panOffset.x * 0.4f, size.height),
                strokeWidth = 5f
            )

            // Radius ring around user/center
            val userPt = latLngToScreen(centerLat, centerLng)
            val pxPerKm = minHalfPx / kmPerHalfScreen.toFloat()
            val ringKm = radiusKm ?: 5.0
            val ringRadiusPx = (ringKm.toFloat() * pxPerKm).coerceAtLeast(24f)
            drawCircle(
                color = userDotColor.copy(alpha = 0.07f),
                radius = ringRadiusPx,
                center = userPt
            )
            drawCircle(
                color = ringColor,
                radius = ringRadiusPx,
                center = userPt,
                style = Stroke(
                    width = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
            )

            if (userLocation.hasCoordinates) {
                drawCircle(
                    color = userDotColor.copy(alpha = 0.22f),
                    radius = 24f,
                    center = userPt
                )
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White,
                    radius = 12f,
                    center = userPt
                )
                drawCircle(
                    color = userDotColor,
                    radius = 8f,
                    center = userPt
                )
            }
        }

        // Price markers for rooms
        validRooms.forEach { item ->
            val pos = latLngToScreen(item.room.latitude, item.room.longitude)
            if (pos.x in -80f..(widthPx + 80f) && pos.y in -80f..(heightPx + 80f)) {
                val isSelected = selectedItem?.room?.id == item.room.id
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(50),
                    shadowElevation = if (isSelected) 6.dp else 3.dp,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pos.x - 42f).roundToInt(),
                                (pos.y - 22f).roundToInt()
                            )
                        }
                        .clickable { selectedItem = item }
                        .testTag("map_marker_${item.room.id}")
                ) {
                    Text(
                        text = GeoUtils.formatShortPrice(item.room.monthlyRent),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Top-left scale indicator
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Text(
                text = buildString {
                    append("${validRooms.size} pinned")
                    if (userLocation.city.isNotBlank()) append(" • ${userLocation.city}")
                    radiusKm?.let { append(" • ≤${if (it < 1) "${(it * 1000).toInt()}m" else "${it.toInt()}km"}") }
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }

        // Right-side map controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = { zoomScale = (zoomScale * 1.6f).coerceAtMost(12f) },
                modifier = Modifier.testTag("map_zoom_in_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom in")
            }
            FilledTonalIconButton(
                onClick = { zoomScale = (zoomScale / 1.6f).coerceAtLeast(0.2f) },
                modifier = Modifier.testTag("map_zoom_out_button")
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
            }
            FilledTonalIconButton(
                onClick = {
                    panOffset = Offset.Zero
                    zoomScale = 1f
                    onRequestCenterLocation()
                },
                modifier = Modifier.testTag("map_recenter_button")
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Center on my location")
            }
        }

        // Bottom Selected Room Preview Card
        selectedItem?.let { current ->
            val r = current.room
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clickable { onRoomSelected(r.id) },
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoomImage(
                            imageSource = r.galleryPhotos().firstOrNull() ?: "",
                            contentDescription = r.title,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = r.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${r.area}, ${r.city}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${GeoUtils.formatCurrency(r.monthlyRent)}/mo",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                val d = GeoUtils.formatDistance(current.distanceKm)
                                if (d.isNotBlank()) {
                                    Text(
                                        text = "• $d",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                GeoUtils.openGoogleMapsDirections(
                                    context = context,
                                    originLat = userLocation.latitude,
                                    originLng = userLocation.longitude,
                                    destLat = r.latitude,
                                    destLng = r.longitude,
                                    label = r.title
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("map_directions_button")
                        ) {
                            Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Directions")
                        }
                        Button(
                            onClick = { onRoomSelected(r.id) },
                            modifier = Modifier.weight(1f).testTag("map_view_room_button")
                        ) {
                            Text("View Room")
                        }
                    }
                }
            }
        }
    }
}
