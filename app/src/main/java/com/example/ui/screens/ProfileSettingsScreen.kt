package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.AccountType
import com.example.data.model.PublicProfile
import com.example.data.model.UiState
import com.example.data.model.UserAccount
import com.example.data.util.ImageUtils
import com.example.ui.components.UserAvatar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingsScreen(
    accountState: UiState<UserAccount?>,
    publicProfile: PublicProfile?,
    followingCount: Int,
    savedCount: Int,
    darkModeEnabled: Boolean,
    isAdmin: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onUpdateProfile: (
        fullName: String,
        username: String,
        phone: String,
        bio: String,
        city: String,
        accountType: AccountType,
        photoData: String,
        showPhone: Boolean,
        showEmail: Boolean,
        isPublic: Boolean,
        onDone: () -> Unit
    ) -> Unit,
    onExportData: (onReady: (String) -> Unit) -> Unit,
    onDeleteAccount: (onDeleted: () -> Unit) -> Unit,
    onOpenOwnerDashboard: () -> Unit,
    onOpenFavourites: () -> Unit,
    onOpenBookings: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showEditDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var exportedJson by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (accountState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Text(accountState.message, modifier = Modifier.padding(24.dp))
            is UiState.Success -> {
                val account = accountState.data
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Profile Card
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(
                                    photoData = account?.photoData ?: "",
                                    name = account?.fullName ?: "User",
                                    size = 64.dp,
                                    verified = account?.verified ?: false
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account?.fullName ?: "RoomFinder Member",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "@${account?.username ?: "member"} • ${AccountType.fromRaw(account?.accountType).label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val profileCity = account?.city
                                    if (!profileCity.isNullOrBlank()) {
                                        Text(
                                            text = profileCity,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showEditDialog = true },
                                    modifier = Modifier.testTag("edit_profile_button")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit")
                                }
                            }

                            val profileBio = account?.bio
                            if (!profileBio.isNullOrBlank()) {
                                Text(profileBio, style = MaterialTheme.typography.bodyMedium)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ProfileStat("Followers", "${publicProfile?.followersCount ?: 0}")
                                ProfileStat("Following", "$followingCount")
                                ProfileStat("Saved", "$savedCount")
                                val joined = account?.createdAt?.toDate()?.let {
                                    SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(it)
                                } ?: "Today"
                                ProfileStat("Joined", joined)
                            }
                        }
                    }

                    // Workspace Shortcuts
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            SettingsRow(Icons.Filled.HomeWork, "Owner Dashboard & My Listings", "Manage rooms, analytics & availability", onOpenOwnerDashboard, "nav_owner_dashboard")
                            HorizontalDivider()
                            SettingsRow(Icons.Filled.Bookmark, "Saved Rooms", "$savedCount bookmarked listings", onOpenFavourites, "nav_saved_rooms")
                            HorizontalDivider()
                            SettingsRow(Icons.Filled.CalendarMonth, "Bookings & Visits", "Track visit dates & approvals", onOpenBookings, "nav_bookings_shortcut")
                            if (isAdmin) {
                                HorizontalDivider()
                                SettingsRow(Icons.Filled.AdminPanelSettings, "Admin Moderation Panel", "Review reports & verify owners", onOpenAdminPanel, "nav_admin_panel")
                            }
                        }
                    }

                    // Privacy & Preferences
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            ListItem(
                                leadingContent = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                                headlineContent = { Text("Dark Mode") },
                                supportingContent = { Text("Use dark theme across RoomFinder") },
                                trailingContent = {
                                    Switch(
                                        checked = darkModeEnabled,
                                        onCheckedChange = onToggleDarkMode,
                                        modifier = Modifier.testTag("dark_mode_switch")
                                    )
                                }
                            )
                            HorizontalDivider()
                            SettingsRow(Icons.Filled.Download, "Export My Data", "Download a JSON summary of your account", {
                                onExportData { json -> exportedJson = json }
                            }, "export_data_row")
                            HorizontalDivider()
                            SettingsRow(Icons.Filled.Policy, "Privacy Policy & Terms", "How RoomFinder handles location & PII", {
                                showPrivacyDialog = true
                            }, "privacy_policy_row")
                            HorizontalDivider()
                            SettingsRow(Icons.Filled.DeleteForever, "Delete Account", "Remove profile & pause listings", {
                                showDeleteConfirm = true
                            }, "delete_account_row")
                        }
                    }

                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Edit Profile Dialog
                if (showEditDialog && account != null) {
                    var fullName by remember(account) { mutableStateOf(account.fullName) }
                    var username by remember(account) { mutableStateOf(account.username) }
                    var phone by remember(account) { mutableStateOf(account.phone) }
                    var bio by remember(account) { mutableStateOf(account.bio) }
                    var city by remember(account) { mutableStateOf(account.city) }
                    var accountType by remember(account) { mutableStateOf(AccountType.fromRaw(account.accountType)) }
                    var photoData by remember(account) { mutableStateOf(account.photoData) }
                    var showPhone by remember(account) { mutableStateOf(account.showPhone) }
                    var showEmail by remember(account) { mutableStateOf(account.showEmail) }
                    var isPublic by remember(publicProfile) { mutableStateOf(publicProfile?.visibility != "PRIVATE") }

                    val avatarPicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        if (uri != null) {
                            scope.launch {
                                val dataUri = ImageUtils.compressUriToDataUri(context, uri, maxDimension = 320, quality = 65)
                                if (dataUri != null) photoData = dataUri
                            }
                        }
                    }

                    AlertDialog(
                        onDismissRequest = { showEditDialog = false },
                        title = { Text("Edit Profile & Privacy") },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatar(photoData = photoData, name = fullName, size = 54.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    OutlinedButton(onClick = {
                                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }) {
                                        Text("Change Photo")
                                    }
                                }
                                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, singleLine = true)
                                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
                                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Mobile Number") }, singleLine = true)
                                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true)
                                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, minLines = 2)

                                Text("Account Type", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AccountType.entries.forEach { t ->
                                        FilterChip(selected = accountType == t, onClick = { accountType = t }, label = { Text(t.label) })
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Public Profile", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Show Phone to Inquiries", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = showPhone, onCheckedChange = { showPhone = it })
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Show Email to Inquiries", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = showEmail, onCheckedChange = { showEmail = it })
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onUpdateProfile(fullName, username, phone, bio, city, accountType, photoData, showPhone, showEmail, isPublic) {
                                        showEditDialog = false
                                    }
                                },
                                enabled = fullName.isNotBlank() && username.trim().length >= 2
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Terms") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.privacy_policy_summary), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.terms_summary), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") } }
        )
    }

    exportedJson?.let { json ->
        AlertDialog(
            onDismissRequest = { exportedJson = null },
            title = { Text("Exported Account Data") },
            text = {
                Text(
                    text = json,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { exportedJson = null }) { Text("Done") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account Data?") },
            text = { Text("This will delete your RoomFinder profile, pause your active room listings, and sign you out.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount {
                            onSignOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete & Sign Out") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.clickable(onClick = onClick).testTag(tag)
    )
}
