package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.UiState
import com.example.data.repository.AdminRepository
import com.example.data.repository.BookingRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.RoomRepository
import com.example.data.repository.UserRepository
import com.example.data.util.PreferencesManager
import com.example.ui.screens.AddEditRoomScreen
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BookingsScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ConversationsListScreen
import com.example.ui.screens.ExploreNearbyScreen
import com.example.ui.screens.FavouritesScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OwnerDashboardScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.RoomDetailScreen
import com.example.ui.theme.RoomFinderTheme
import com.example.viewmodels.AuthSessionState
import com.example.viewmodels.AuthViewModel
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.RoomDetailViewModel
import com.example.ui.viewmodel.SessionViewModel
import com.example.ui.viewmodel.WorkspaceViewModel
import com.google.firebase.firestore.FirebaseFirestore

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val MESSAGES = "messages"
    const val BOOKINGS = "bookings"
    const val PROFILE = "profile"
    const val ROOM_DETAIL = "room_detail/{roomId}"
    const val ADD_ROOM = "add_room?roomId={roomId}"
    const val CHAT_DETAIL = "chat_detail/{conversationId}"
    const val NOTIFICATIONS = "notifications"
    const val FAVOURITES = "favourites"
    const val OWNER_DASHBOARD = "owner_dashboard"
    const val ADMIN = "admin"

    fun roomDetail(roomId: String) = "room_detail/$roomId"
    fun addRoom(roomId: String? = null) = if (roomId != null) "add_room?roomId=$roomId" else "add_room"
    fun chatDetail(conversationId: String) = "chat_detail/$conversationId"
}

private data class TopTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topTabs = listOf(
    TopTab(Routes.HOME, "Feed", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    TopTab(Routes.EXPLORE, "Nearby", Icons.Filled.Explore, Icons.Outlined.Explore, "nav_explore"),
    TopTab(Routes.MESSAGES, "Chat", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, "nav_messages"),
    TopTab(Routes.BOOKINGS, "Bookings", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_bookings"),
    TopTab(Routes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                AuthViewModel(prefs = PreferencesManager(app))
            }
        }
    )
    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        is AuthSessionState.Unauthenticated -> {
            RoomFinderTheme {
                AuthScreen(authViewModel = authViewModel)
            }
        }
        is AuthSessionState.Authenticated -> {
            val uid = state.user.uid
            key(uid) {
                AuthenticatedRoomFinderApp(
                    currentUserId = uid,
                    onSignOut = { authViewModel.signOut(context) }
                )
            }
        }
    }
}

@Composable
fun AuthenticatedRoomFinderApp(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val sessionVm: SessionViewModel = viewModel(
        key = "session_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                SessionViewModel(
                    userRepository = UserRepository(db),
                    notificationRepository = NotificationRepository(db),
                    chatRepository = ChatRepository(db),
                    adminRepository = AdminRepository(db),
                    prefs = PreferencesManager(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val discoveryVm: DiscoveryViewModel = viewModel(
        key = "discovery_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                DiscoveryViewModel(
                    roomRepository = RoomRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val workspaceVm: WorkspaceViewModel = viewModel(
        key = "workspace_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                WorkspaceViewModel(
                    roomRepository = RoomRepository(db),
                    chatRepository = ChatRepository(db),
                    bookingRepository = BookingRepository(db),
                    adminRepository = AdminRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val darkOverride by sessionVm.darkModeOverride.collectAsStateWithLifecycle()
    val isDark = darkOverride ?: isSystemInDarkTheme()

    val accountState by sessionVm.userAccount.collectAsStateWithLifecycle()
    val publicProfiles by sessionVm.publicProfiles.collectAsStateWithLifecycle()
    val followingIds by sessionVm.followingIds.collectAsStateWithLifecycle()
    val notificationsState by sessionVm.notifications.collectAsStateWithLifecycle()
    val unreadNotifs by sessionVm.unreadNotificationCount.collectAsStateWithLifecycle()
    val unreadMessages by sessionVm.unreadMessageCount.collectAsStateWithLifecycle()
    val locationState by sessionVm.locationState.collectAsStateWithLifecycle()
    val isAdmin by sessionVm.isAdmin.collectAsStateWithLifecycle()
    val sessionBanner by sessionVm.statusBanner.collectAsStateWithLifecycle()

    val roomsState by discoveryVm.availableRooms.collectAsStateWithLifecycle()
    val selectedFeedTab by discoveryVm.selectedFeedTab.collectAsStateWithLifecycle()
    val filterState by discoveryVm.filterState.collectAsStateWithLifecycle()
    val likedRoomIds by discoveryVm.likedRoomIds.collectAsStateWithLifecycle()
    val favourites by discoveryVm.favourites.collectAsStateWithLifecycle()
    val favouriteRoomIds by discoveryVm.favouriteRoomIds.collectAsStateWithLifecycle()
    val discoveryMsg by discoveryVm.actionMessage.collectAsStateWithLifecycle()

    val ownerRoomsState by workspaceVm.ownerRooms.collectAsStateWithLifecycle()
    val conversationsState by workspaceVm.conversations.collectAsStateWithLifecycle()
    val bookingsState by workspaceVm.bookings.collectAsStateWithLifecycle()
    val isSavingRoom by workspaceVm.isSavingRoom.collectAsStateWithLifecycle()
    val workspaceMsg by workspaceVm.statusMessage.collectAsStateWithLifecycle()

    val myAccount = (accountState as? UiState.Success)?.data
    val myName = myAccount?.fullName?.ifBlank { "RoomFinder Member" } ?: "RoomFinder Member"
    val myPhoto = myAccount?.photoData ?: ""

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        sessionVm.fetchCurrentLocation(context, granted)
    }

    fun requestLocation() {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (coarse || fine) {
            sessionVm.fetchCurrentLocation(context, true)
        } else {
            locationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    LaunchedEffect(Unit) {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (coarse || fine) {
            sessionVm.fetchCurrentLocation(context, true)
        }
    }

    LaunchedEffect(sessionBanner, discoveryMsg, workspaceMsg) {
        val msg = sessionBanner ?: discoveryMsg ?: workspaceMsg
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            sessionVm.clearBanner()
            discoveryVm.clearMessage()
            workspaceVm.clearStatusMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTopLevelNav = currentRoute in topTabs.map { it.route }
    val useNavRail = LocalConfiguration.current.screenWidthDp >= 600

    RoomFinderTheme(darkTheme = isDark) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (currentRoute == Routes.HOME || currentRoute == Routes.EXPLORE) {
                    FloatingActionButton(
                        onClick = { navController.navigate(Routes.addRoom()) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_room")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Post a room")
                    }
                }
            },
            bottomBar = {
                if (showTopLevelNav && !useNavRail) {
                    NavigationBar {
                        topTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
                                    if (tab.route == Routes.MESSAGES && unreadMessages > 0) {
                                        BadgedBox(badge = { Badge { Text("$unreadMessages") } }) {
                                            Icon(icon, contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useNavRail) {
                    NavigationRail {
                        topTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
                                    if (tab.route == Routes.MESSAGES && unreadMessages > 0) {
                                        BadgedBox(badge = { Badge { Text("$unreadMessages") } }) {
                                            Icon(icon, contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME
                    ) {
                        composable(Routes.HOME) {
                            HomeFeedScreen(
                                roomsState = roomsState,
                                selectedTab = selectedFeedTab,
                                likedRoomIds = likedRoomIds,
                                favouriteRoomIds = favouriteRoomIds,
                                locationState = locationState,
                                unreadNotifications = unreadNotifs,
                                actorName = myName,
                                discoveryViewModel = discoveryVm,
                                onRequestLocation = { requestLocation() },
                                onRoomClick = { id -> navController.navigate(Routes.roomDetail(id)) },
                                onAddRoomClick = { navController.navigate(Routes.addRoom()) },
                                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                                onSavedRoomsClick = { navController.navigate(Routes.FAVOURITES) }
                            )
                        }

                        composable(Routes.EXPLORE) {
                            ExploreNearbyScreen(
                                roomsState = roomsState,
                                filterState = filterState,
                                likedRoomIds = likedRoomIds,
                                favouriteRoomIds = favouriteRoomIds,
                                locationState = locationState,
                                actorName = myName,
                                discoveryViewModel = discoveryVm,
                                onRequestLocation = { requestLocation() },
                                onManualCitySearch = { q -> sessionVm.setManualCityLocation(context, q) },
                                onRoomClick = { id -> navController.navigate(Routes.roomDetail(id)) }
                            )
                        }

                        composable(Routes.MESSAGES) {
                            ConversationsListScreen(
                                conversationsState = conversationsState,
                                currentUserId = currentUserId,
                                onConversationClick = { convId -> navController.navigate(Routes.chatDetail(convId)) }
                            )
                        }

                        composable(Routes.BOOKINGS) {
                            BookingsScreen(
                                bookingsState = bookingsState,
                                currentUserId = currentUserId,
                                actorName = myName,
                                onRoomClick = { id -> navController.navigate(Routes.roomDetail(id)) },
                                onUpdateStatus = { booking, status, date ->
                                    workspaceVm.updateBookingStatus(booking, status, myName, date)
                                }
                            )
                        }

                        composable(Routes.PROFILE) {
                            ProfileSettingsScreen(
                                accountState = accountState,
                                publicProfile = publicProfiles[currentUserId],
                                followingCount = followingIds.size,
                                savedCount = favourites.size,
                                darkModeEnabled = isDark,
                                isAdmin = isAdmin,
                                onToggleDarkMode = { sessionVm.setDarkMode(it) },
                                onUpdateProfile = { fullName, username, phone, bio, city, accountType, photoData, showPhone, showEmail, isPublic, onDone ->
                                    sessionVm.updateProfile(fullName, username, phone, bio, city, accountType, photoData, showPhone, showEmail, isPublic, onDone)
                                },
                                onExportData = { sessionVm.exportUserData(it) },
                                onDeleteAccount = { sessionVm.deleteAccount(it) },
                                onOpenOwnerDashboard = { navController.navigate(Routes.OWNER_DASHBOARD) },
                                onOpenFavourites = { navController.navigate(Routes.FAVOURITES) },
                                onOpenBookings = { navController.navigate(Routes.BOOKINGS) },
                                onOpenAdminPanel = { navController.navigate(Routes.ADMIN) },
                                onSignOut = onSignOut
                            )
                        }

                        composable(
                            route = Routes.ROOM_DETAIL,
                            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
                        ) { entry ->
                            val roomId = entry.arguments?.getString("roomId") ?: ""
                            val detailVm: RoomDetailViewModel = viewModel(
                                key = "detail_${currentUserId}_$roomId",
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY])
                                        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                        RoomDetailViewModel(
                                            roomRepository = RoomRepository(db),
                                            bookingRepository = BookingRepository(db),
                                            chatRepository = ChatRepository(db),
                                            roomId = roomId,
                                            currentUserId = currentUserId
                                        )
                                    }
                                }
                            )
                            val roomObj = (detailVm.roomState.collectAsStateWithLifecycle().value as? UiState.Success)?.data
                            RoomDetailScreen(
                                viewModel = detailVm,
                                currentUserId = currentUserId,
                                myName = myName,
                                myPhoto = myPhoto,
                                isSaved = roomId in favouriteRoomIds,
                                isFollowingOwner = roomObj?.ownerId in followingIds,
                                ownerPublicProfile = roomObj?.ownerId?.let { publicProfiles[it] },
                                userLocation = locationState,
                                onToggleSave = { discoveryVm.toggleFavourite(it) },
                                onToggleFollow = { ownerId, ownerName -> sessionVm.toggleFollow(ownerId, ownerName) },
                                onOpenChat = { convId -> navController.navigate(Routes.chatDetail(convId)) },
                                onEditRoom = { id -> navController.navigate(Routes.addRoom(id)) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Routes.ADD_ROOM,
                            arguments = listOf(navArgument("roomId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            })
                        ) { entry ->
                            val editId = entry.arguments?.getString("roomId")
                            val existingRoom = if (!editId.isNullOrBlank()) {
                                ((ownerRoomsState as? UiState.Success)?.data ?: emptyList()).firstOrNull { it.id == editId }
                                    ?: ((roomsState as? UiState.Success)?.data ?: emptyList()).firstOrNull { it.id == editId }
                            } else null

                            AddEditRoomScreen(
                                existingRoom = existingRoom,
                                currentUser = myAccount,
                                userLocation = locationState,
                                isSaving = isSavingRoom,
                                onRequestLocation = { requestLocation() },
                                onSaveRoom = { listing, _ ->
                                    workspaceVm.saveRoom(editId, listing) {
                                        navController.popBackStack()
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Routes.CHAT_DETAIL,
                            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                        ) { entry ->
                            val convId = entry.arguments?.getString("conversationId") ?: ""
                            ChatDetailScreen(
                                conversationId = convId,
                                conversationsState = conversationsState,
                                workspaceViewModel = workspaceVm,
                                currentUserId = currentUserId,
                                myName = myName,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.NOTIFICATIONS) {
                            NotificationsScreen(
                                notificationsState = notificationsState,
                                onMarkRead = { sessionVm.markNotificationRead(it) },
                                onMarkAllRead = { sessionVm.markAllNotificationsRead() },
                                onNavigateTarget = { notif ->
                                    when (notif.type) {
                                        "MESSAGE" -> if (notif.targetId.isNotBlank()) navController.navigate(Routes.chatDetail(notif.targetId))
                                        else -> if (notif.targetId.isNotBlank()) navController.navigate(Routes.roomDetail(notif.targetId))
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.FAVOURITES) {
                            FavouritesScreen(
                                favourites = favourites,
                                availableRooms = (roomsState as? UiState.Success)?.data ?: emptyList(),
                                onRoomClick = { id -> navController.navigate(Routes.roomDetail(id)) },
                                onRemoveFavourite = { discoveryVm.toggleFavourite(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.OWNER_DASHBOARD) {
                            OwnerDashboardScreen(
                                ownerRoomsState = ownerRoomsState,
                                bookingsState = bookingsState,
                                currentUserId = currentUserId,
                                onAddRoom = { navController.navigate(Routes.addRoom()) },
                                onEditRoom = { id -> navController.navigate(Routes.addRoom(id)) },
                                onRoomClick = { id -> navController.navigate(Routes.roomDetail(id)) },
                                onUpdateStatus = { id, status -> workspaceVm.updateRoomStatus(id, status) },
                                onDeleteRoom = { id -> workspaceVm.deleteRoom(id) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.ADMIN) {
                            AdminScreen(
                                workspaceViewModel = workspaceVm,
                                onRoomClick = { id -> navController.navigate(Routes.roomDetail(id)) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
