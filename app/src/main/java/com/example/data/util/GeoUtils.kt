package com.example.data.util

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class GeocodedLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val area: String,
    val state: String,
    val country: String,
    val postalCode: String,
    val formattedAddress: String
)

object GeoUtils {

    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if ((lat1 == 0.0 && lon1 == 0.0) || (lat2 == 0.0 && lon2 == 0.0)) return Double.MAX_VALUE
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun formatDistance(distanceKm: Double?): String {
        if (distanceKm == null || distanceKm == Double.MAX_VALUE || distanceKm.isNaN()) return ""
        return if (distanceKm < 1.0) {
            val meters = (distanceKm * 1000).roundToInt().coerceAtLeast(10)
            "$meters m away"
        } else if (distanceKm < 10.0) {
            String.format(Locale.US, "%.1f km away", distanceKm)
        } else {
            "${distanceKm.roundToInt()} km away"
        }
    }

    fun formatCurrency(amount: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"))
        return "₹${formatter.format(amount)}"
    }

    fun formatShortPrice(amount: Long): String {
        return when {
            amount >= 100000 -> String.format(Locale.US, "₹%.1fL", amount / 100000.0)
            amount >= 1000 -> String.format(Locale.US, "₹%.1fk", amount / 1000.0).replace(".0k", "k")
            else -> "₹$amount"
        }
    }

    fun openGoogleMapsDirections(
        context: Context,
        originLat: Double?,
        originLng: Double?,
        destLat: Double,
        destLng: Double,
        label: String
    ) {
        val uriString = if (originLat != null && originLng != null && (originLat != 0.0 || originLng != 0.0)) {
            "https://www.google.com/maps/dir/?api=1&origin=$originLat,$originLng&destination=$destLat,$destLng&travelmode=driving"
        } else {
            "https://www.google.com/maps/search/?api=1&query=$destLat,$destLng"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Navigate to $label")
        runCatching { context.startActivity(chooser) }
    }

    @Suppress("DEPRECATION")
    suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): GeocodedLocation? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())
            try {
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lng, 1) { list ->
                            if (cont.isActive) cont.resume(list)
                        }
                    }
                } else {
                    geocoder.getFromLocation(lat, lng, 1) ?: emptyList()
                }
                val addr = addresses.firstOrNull() ?: return@withContext null
                GeocodedLocation(
                    latitude = lat,
                    longitude = lng,
                    city = addr.locality ?: addr.subAdminArea ?: "",
                    area = addr.subLocality ?: addr.thoroughfare ?: "",
                    state = addr.adminArea ?: "",
                    country = addr.countryName ?: "India",
                    postalCode = addr.postalCode ?: "",
                    formattedAddress = (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) }
                )
            } catch (_: Exception) {
                null
            }
        }

    @Suppress("DEPRECATION")
    suspend fun forwardGeocode(context: Context, query: String): GeocodedLocation? =
        withContext(Dispatchers.IO) {
            if (query.isBlank() || !Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())
            try {
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocationName(query, 1) { list ->
                            if (cont.isActive) cont.resume(list)
                        }
                    }
                } else {
                    geocoder.getFromLocationName(query, 1) ?: emptyList()
                }
                val addr = addresses.firstOrNull() ?: return@withContext null
                GeocodedLocation(
                    latitude = addr.latitude,
                    longitude = addr.longitude,
                    city = addr.locality ?: addr.subAdminArea ?: "",
                    area = addr.subLocality ?: addr.featureName ?: "",
                    state = addr.adminArea ?: "",
                    country = addr.countryName ?: "India",
                    postalCode = addr.postalCode ?: "",
                    formattedAddress = (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) }
                )
            } catch (_: Exception) {
                null
            }
        }
}
