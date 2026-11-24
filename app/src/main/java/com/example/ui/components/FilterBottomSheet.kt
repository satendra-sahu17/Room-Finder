package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BathroomType
import com.example.data.model.FurnishingType
import com.example.data.model.GenderPreference
import com.example.data.model.RoomCategory
import com.example.data.model.RoomFilterState
import com.example.data.model.SortOption

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    currentFilter: RoomFilterState,
    matchingCount: Int,
    onApply: (RoomFilterState) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(currentFilter) { mutableStateOf(currentFilter) }
    var minRentText by remember(currentFilter) { mutableStateOf(currentFilter.minRent?.toString() ?: "") }
    var maxRentText by remember(currentFilter) { mutableStateOf(currentFilter.maxRent?.toString() ?: "") }

    val radiusOptions: List<Pair<Double?, String>> = listOf(
        0.5 to "500 m",
        1.0 to "1 km",
        2.0 to "2 km",
        5.0 to "5 km",
        10.0 to "10 km",
        20.0 to "20 km",
        null to "Any"
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Search Filters", style = MaterialTheme.typography.headlineSmall)
                TextButton(
                    onClick = {
                        draft = RoomFilterState()
                        minRentText = ""
                        maxRentText = ""
                        onClear()
                    },
                    modifier = Modifier.testTag("clear_filters_button")
                ) {
                    Text("Clear All")
                }
            }

            // Sort Order
            Text("Sort By", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOption.entries.forEach { option ->
                    FilterChip(
                        selected = draft.sortOption == option,
                        onClick = { draft = draft.copy(sortOption = option) },
                        label = { Text(option.label) }
                    )
                }
            }

            // Distance
            Text("Distance Radius", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                radiusOptions.forEach { (km, label) ->
                    FilterChip(
                        selected = draft.maxDistanceKm == km,
                        onClick = { draft = draft.copy(maxDistanceKm = km) },
                        label = { Text(label) }
                    )
                }
            }

            // Budget
            Text("Monthly Rent (₹)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minRentText,
                    onValueChange = { s ->
                        minRentText = s.filter { it.isDigit() }
                        draft = draft.copy(minRent = minRentText.toIntOrNull())
                    },
                    label = { Text("Min Rent") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxRentText,
                    onValueChange = { s ->
                        maxRentText = s.filter { it.isDigit() }
                        draft = draft.copy(maxRent = maxRentText.toIntOrNull())
                    },
                    label = { Text("Max Rent") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Room Type
            Text("Room Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoomCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = draft.category == cat,
                        onClick = {
                            draft = draft.copy(category = if (draft.category == cat) null else cat)
                        },
                        label = { Text(cat.label) }
                    )
                }
            }

            // Furnishing
            Text("Furnishing", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FurnishingType.entries.forEach { f ->
                    FilterChip(
                        selected = draft.furnishing == f,
                        onClick = {
                            draft = draft.copy(furnishing = if (draft.furnishing == f) null else f)
                        },
                        label = { Text(f.label) }
                    )
                }
            }

            // Bathroom & Amenities
            Text("Bathroom & Amenities", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BathroomType.entries.forEach { b ->
                    FilterChip(
                        selected = draft.bathroomType == b,
                        onClick = {
                            draft = draft.copy(bathroomType = if (draft.bathroomType == b) null else b)
                        },
                        label = { Text(b.label) }
                    )
                }
                FilterChip(
                    selected = draft.foodOnly,
                    onClick = { draft = draft.copy(foodOnly = !draft.foodOnly) },
                    label = { Text("Food Included") }
                )
                FilterChip(
                    selected = draft.parkingOnly,
                    onClick = { draft = draft.copy(parkingOnly = !draft.parkingOnly) },
                    label = { Text("Parking") }
                )
            }

            // Preferences
            Text("Tenant Preference", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GenderPreference.entries.forEach { pref ->
                    FilterChip(
                        selected = draft.genderPreference == pref,
                        onClick = {
                            draft = draft.copy(genderPreference = if (draft.genderPreference == pref) null else pref)
                        },
                        label = { Text(pref.label) }
                    )
                }
            }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onApply(draft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).testTag("apply_filters_button")
                ) {
                    Text("Apply ($matchingCount)")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
