package com.example.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.StatusItem
import com.example.data.DownloadLog
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Chat
import kotlinx.coroutines.launch

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem("home", "Recent", Icons.Default.Home)
    object Saved : BottomNavItem("saved", "Saved", Icons.Default.Save)
    object Trending : BottomNavItem("trending", "Trending", Icons.AutoMirrored.Filled.TrendingUp)
    object Contacts : BottomNavItem("contacts", "Contacts", Icons.Default.Contacts)
    object Admin : BottomNavItem("admin", "Admin", Icons.Default.AdminPanelSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, viewModel: StatusViewModel = viewModel(), authViewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    var hasContactsPermission by remember { 
        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) 
    }
    val hasStoragePermission by viewModel.hasPermission.collectAsState()

    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    val updateState by viewModel.updateState.collectAsState()

    LaunchedEffect(updateState) {
        val state = updateState
        if (state is UpdateState.UpdateAvailable) {
            viewModel.downloadAndInstallUpdate(state.config, context)
        }
    }

    if (updateState !is UpdateState.Idle) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "System Update in Progress",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                when (val state = updateState) {
                    is UpdateState.UpdateAvailable -> {
                        Text(
                            "Preparing new version V${state.config.versionName}...",
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
                    }
                    is UpdateState.Downloading -> {
                        val progressPercent = (state.progress * 100).toInt()
                        Text(
                            "Downloading latest version: $progressPercent%",
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.DarkGray
                        )
                    }
                    is UpdateState.Installing -> {
                        Text(
                            "Launching Package Installer...",
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    is UpdateState.Error -> {
                        Text(
                            "Update Failed: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.listenForUpdates() }) {
                            Text("Retry Update")
                        }
                    }
                    else -> {}
                }
            }
        }
        return
    }

    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.checkPermissionAndLoad(false)
    }
    val storagePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.checkPermissionAndLoad(false)
    }

    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasContactsPermission = granted
        if (granted && !viewModel.hasPermission.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    storageLauncher.launch(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        storageLauncher.launch(intent)
                    } catch (e2: Exception) {
                        // ignore if neither is supported
                    }
                }
            } else {
                storagePermLauncher.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
    }

    if (!hasStoragePermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Storage Permission Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("We need access to your device storage to find and retrieve WhatsApp statuses. Please grant permission to continue.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        storageLauncher.launch(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            storageLauncher.launch(intent)
                        } catch (e2: Exception) {
                            // ignore
                        }
                    }
                } else {
                    storagePermLauncher.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Grant Storage Permission")
            }
        }
        return
    }

    var selectedTab by remember { mutableStateOf<BottomNavItem>(BottomNavItem.Home) }
    val isAdmin by authViewModel.isAdmin.collectAsState()
    val items = if (isAdmin) {
        listOf(BottomNavItem.Home, BottomNavItem.Saved, BottomNavItem.Trending, BottomNavItem.Contacts, BottomNavItem.Admin)
    } else {
        listOf(BottomNavItem.Home, BottomNavItem.Saved, BottomNavItem.Trending, BottomNavItem.Contacts)
    }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            scope.launch { drawerState.close() }
        }
    }
    
    val userEmail by authViewModel.currentUserEmail.collectAsState()
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginMobile by remember { mutableStateOf("") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(32.dp))
                if (isLoggedIn) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(userEmail ?: "", modifier = Modifier.align(Alignment.CenterHorizontally), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(if (isAdmin) "Admin Panel Access Enabled" else "User Account", modifier = Modifier.align(Alignment.CenterHorizontally), color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { authViewModel.logout() }, modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                        Text("Log Out")
                    }
                } else {
                    val authError by authViewModel.authError.collectAsState()
                    var isLoginMode by remember { mutableStateOf(true) }
                    Text(if (isLoginMode) "Login" else "Register", modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = loginEmail,
                        onValueChange = { loginEmail = it },
                        label = { Text("Email") },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = loginMobile,
                        onValueChange = { loginMobile = it },
                        label = { Text("Mobile Number (Required)") },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text("Password") },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    if (!authError.isNullOrEmpty()) {
                        Text(authError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { 
                            if (loginMobile.isBlank()) return@Button
                            if (isLoginMode) {
                                authViewModel.login(loginEmail, loginPassword, loginMobile)
                            } else {
                                authViewModel.register(loginEmail, loginPassword, loginMobile)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()
                    ) {
                        Text(if (isLoginMode) "Login" else "Register")
                    }
                    TextButton(
                        onClick = { isLoginMode = !isLoginMode },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (isLoginMode) "Need an account? Register" else "Already have an account? Login")
                    }
                }
                Spacer(Modifier.weight(1f))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(12.dp))
                
                // Privacy and Terms link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { showPrivacyPolicyDialog = true }
                            .padding(4.dp)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "Terms & Conditions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { showTermsDialog = true }
                            .padding(4.dp)
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Developed by Sufyan TechLabs & Made in Pakistan 🇵🇰 card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F261E).copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Developed by Sufyan TechLabs",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF25D366)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Made in Pakistan with",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "💚 🇵🇰",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = { shareApk(context) },
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share App APK")
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MODERN NATIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.alpha(0.8f)
                            )
                            Text(
                                text = "Status Saver",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).clickable { scope.launch { drawerState.open() } },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            }
                            Box(
                                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(16.dp).background(Color.White, RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selectedTab == item,
                        onClick = { selectedTab = item }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                is BottomNavItem.Home -> RecentTab(viewModel, navController)
                is BottomNavItem.Saved -> SavedTab(viewModel, navController)
                is BottomNavItem.Trending -> TrendingScreen(viewModel, navController)
                is BottomNavItem.Contacts -> ContactsScreen(viewModel)
                is BottomNavItem.Admin -> AdminDashboardNew(viewModel)
            }
        }
    }

    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Privacy Policy", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = "Last Updated: May 2026",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "We, Sufyan TechLabs, respect your privacy. This privacy policy describes how our WhatsApp Status Saver App handles your data.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "1. Permissions & Access:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Photos/Videos storage permission is used exclusively to load local WhatsApp statuses (stored in your WhatsApp directories on your device) and allow you to view, play, and save them to your permanent device storage.\n• Contacts read permission enables you to sync your local address book backups (optional and user-initiated) onto our secure cloud database (Turso DB) for central management and easy recovery.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "2. Data Storage & Backups:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Downloaded statuses are stored and kept purely offline on your device storage.\n• When backing up contacts, we safely store them inside our Turso SQLite database with HTTPS encryption. No data is ever shared with third-party advertisers or unauthorized platforms.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "3. Developer Commitment:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Developed by Sufyan TechLabs, proudly supporting digital safety and privacy in Pakistan.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyPolicyDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("I Understand")
                }
            }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ListAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Terms & Conditions", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = "Last Updated: May 2026",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "By installing and using our Status Saver application, you agree to these Terms and Conditions:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "1. Content Ownership & Rights:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• You are solely responsible for obtaining permission from the original creators / authors before saving or resharing status files (images, videos, quotes).\n• We strictly forbid the reupload or reuse of copyright protected media downloaded through this application for commercial or harmful intentions.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "2. Disclaimer:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• The word 'WhatsApp' is copyright by Meta Inc / WhatsApp Inc. This application is an independent extension built under Sufyan TechLabs. We are in no way affiliated with, sponsored by, or endorsed by WhatsApp or Meta.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "3. Fair Use & Privacy Limits:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• While backing up your contacts database, keep in mind we require valid login mobile digits. Please use this service genuinely and refrain from using disposable numbers.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTermsDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Accept Terms")
                }
            }
        )
    }
    }
}

@Composable
fun AdminDashboardOld(viewModel: StatusViewModel) {
    val statuses by viewModel.statuses.collectAsState()
    val savedStatuses by viewModel.savedStatuses.collectAsState()
    val logs by viewModel.adminLogs.collectAsState()
    
    var activePreviewLog by remember { mutableStateOf<DownloadLog?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.listenForAdminLogs()
    }
    
    if (activePreviewLog != null) {
        val log = activePreviewLog!!
        if (!log.mediaUrl.isNullOrBlank()) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { activePreviewLog = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    ViewerScreen(
                        uri = Uri.parse(log.mediaUrl),
                        isVideo = log.isVideo,
                        onBack = { activePreviewLog = null }
                    )
                }
            }
        }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Overall Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Text("${statuses.size}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                             Text("Available Statuses", style = MaterialTheme.typography.bodyMedium)
                         }
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Text("${logs.size}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                             Text("Total Downloads", style = MaterialTheme.typography.bodyMedium)
                         }
                    }
                }
            }
        }
        
        item {
             Text("Live Activity Logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
             Text("Real-time cloud data capture (tap items with play / image to preview)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
             Spacer(modifier = Modifier.height(8.dp))
        }
        
        if (logs.isNotEmpty()) {
            items(logs) { log ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if (log.isVideo) Icons.Default.PlayCircleOutline else Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (log.isVideo) "Video Downloaded" else "Image Downloaded", fontWeight = FontWeight.Bold)
                            Text("User / Mobile: ${log.mobileNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text("File: ${log.fileName}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            
                            val timeVal = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
                            Text(timeVal, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        
                        if (!log.mediaUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { activePreviewLog = log },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(if (log.isVideo) "Play Video" else "View Image", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text("No downloads yet.", modifier = Modifier.padding(32.dp).fillMaxWidth(), textAlign = TextAlign.Center, color = Color.Gray)
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun RecentTab(viewModel: StatusViewModel, navController: NavController) {
    val context = LocalContext.current
    val hasPermission by viewModel.hasPermission.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.checkPermissionAndLoad(false)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        viewModel.checkPermissionAndLoad(false)
    }

    if (!hasPermission) {
        PermissionScreen {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    launcher.launch(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        launcher.launch(intent)
                    } catch (e2: Exception) {
                        // ignore
                    }
                }
            } else {
                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    } else {
        var selectedFilter by remember { mutableStateOf("All") }
        var isBusiness by remember { mutableStateOf(false) }
        val filteredStatuses = statuses.filter {
            when (selectedFilter) {
                "Videos" -> it.isVideo
                "Photos" -> !it.isVideo
                else -> true
            }
        }
        
        Column(modifier = Modifier.fillMaxSize()) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val segmentBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFEEEEEE)
            val selectedBg = if (isDark) Color(0xFF333333) else Color.White
            val selectedText = if (isDark) Color.White else Color.Black
            val unselectedText = Color.Gray

            // Source Segmented Control
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .background(segmentBg, RoundedCornerShape(20.dp))
                    .padding(6.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (!isBusiness) selectedBg else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { 
                                isBusiness = false
                                viewModel.loadStatuses(false)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("WhatsApp", fontSize = 12.sp, fontWeight = if (!isBusiness) FontWeight.Bold else FontWeight.Medium, color = if (!isBusiness) selectedText else unselectedText)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isBusiness) selectedBg else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { 
                                isBusiness = true
                                viewModel.loadStatuses(true)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Business", fontSize = 12.sp, fontWeight = if (isBusiness) FontWeight.Bold else FontWeight.Medium, color = if (isBusiness) selectedText else unselectedText)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("All", "Videos", "Photos").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else segmentBg,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else unselectedText
                        )
                    }
                }
            }
            
            StatusGrid(filteredStatuses, isSaved = false, onSave = { viewModel.saveStatus(it) }) { item ->
                val encodedUri = Uri.encode(item.uri.toString())
                navController.navigate("viewer?encodedUri=$encodedUri&isVideo=${item.isVideo}")
            }
        }
    }
}

@Composable
fun SavedTab(viewModel: StatusViewModel, navController: NavController) {
    val savedStatuses by viewModel.savedStatuses.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.loadSavedStatuses()
    }
    
    var selectedFilter by remember { mutableStateOf("All") }
    val filteredStatuses = savedStatuses.filter {
        when (selectedFilter) {
            "Videos" -> it.isVideo
            "Photos" -> !it.isVideo
            else -> true
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val segmentBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFEEEEEE)
        val unselectedText = Color.Gray
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf("All", "Videos", "Photos").forEach { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else segmentBg,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = filter,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else unselectedText
                    )
                }
            }
        }
        
        StatusGrid(filteredStatuses, isSaved = true, onDelete = { viewModel.deleteStatus(it) }) { item ->
            val encodedUri = Uri.encode(item.uri.toString())
            navController.navigate("viewer?encodedUri=$encodedUri&isVideo=${item.isVideo}")
        }
    }
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("We need access to WhatsApp Status folder.", style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun StatusGrid(statuses: List<StatusItem>, isSaved: Boolean = false, onSave: ((StatusItem) -> Unit)? = null, onDelete: ((StatusItem) -> Unit)? = null, onClick: (StatusItem) -> Unit) {
    if (statuses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No statuses found")
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(statuses, key = { it.uri.toString() }) { item ->
                StatusCard(item, isSaved, onSave, onDelete, onClick)
            }
        }
    }
}

@Composable
fun StatusCard(item: StatusItem, isSaved: Boolean, onSave: ((StatusItem) -> Unit)?, onDelete: ((StatusItem) -> Unit)?, onClick: (StatusItem) -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .aspectRatio(0.8f)
            .clickable { onClick(item) },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), startY = 100f))
            )
            
            if (item.isVideo) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(32.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Small visual indicator for photo
                    Box(modifier = Modifier.size(8.dp).background(Color.White, RoundedCornerShape(2.dp)))
                }
            }
            
            if (!isSaved && onSave != null) {
                IconButton(
                    onClick = { onSave(item) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 12.dp, end = 12.dp)
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            } else if (isSaved) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = if (item.isVideo) "video/mp4" else "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share status via"))
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    if (onDelete != null) {
                        IconButton(
                            onClick = { onDelete(item) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

fun shareApk(context: Context) {
    try {
        val applicationInfo = context.applicationInfo
        val apkFile = java.io.File(applicationInfo.publicSourceDir)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Status Saver APK")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Throwable) {
        android.util.Log.e("ShareApk", "Error sharing APK", e)
        android.widget.Toast.makeText(context, "Error sharing APK: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AdminDashboardNew(viewModel: StatusViewModel) {
    val statuses by viewModel.statuses.collectAsState()
    val savedStatuses by viewModel.savedStatuses.collectAsState()
    val logs by viewModel.adminLogs.collectAsState()
    val adminContacts by viewModel.adminContacts.collectAsState()
    
    var activePreviewLog by remember { mutableStateOf<DownloadLog?>(null) }
    
    // States for Download Logs Tab
    var logSearchQuery by remember { mutableStateOf("") }
    var mediaFilterType by remember { mutableStateOf("All") } // "All", "Videos", "Images"
    
    val filteredLogs = remember(logs, logSearchQuery, mediaFilterType) {
        logs.filter { log ->
            val matchesSearch = log.mobileNumber.contains(logSearchQuery, ignoreCase = true) || 
                                log.fileName.contains(logSearchQuery, ignoreCase = true)
            val matchesType = when (mediaFilterType) {
                "Videos" -> log.isVideo
                "Images" -> !log.isVideo
                else -> true
            }
            matchesSearch && matchesType
        }
    }
    
    // States for User Contacts Tab
    var contactSearchQuery by remember { mutableStateOf("") }
    var adminSubTab by remember { mutableStateOf(0) } // 0 = Download Logs, 1 = User Contacts DB
    
    val filteredAdminContacts = remember(adminContacts, contactSearchQuery) {
        adminContacts.filter { contact ->
            contact.userIdAsMobile.contains(contactSearchQuery, ignoreCase = true) || 
            contact.name.contains(contactSearchQuery, ignoreCase = true) || 
            contact.phone.contains(contactSearchQuery)
        }
    }
    
    val context = LocalContext.current
    var inputVersionCode by remember { mutableStateOf((com.example.BuildConfig.VERSION_CODE + 1).toString()) }
    var inputVersionName by remember { mutableStateOf(com.example.BuildConfig.VERSION_NAME + ".1") }
    var inputReleaseNotes by remember { mutableStateOf("Bug fixes and performance upgrades.") }
    var inputApkUrl by remember { mutableStateOf("") }
    
    var selectedApkUri by remember { mutableStateOf<Uri?>(null) }
    var selectedApkFileName by remember { mutableStateOf("") }
    
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedApkUri = uri
            selectedApkFileName = uri.path?.substringAfterLast("/") ?: "selected-app.apk"
            if (!selectedApkFileName.endsWith(".apk")) {
                selectedApkFileName += ".apk"
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.listenForAdminLogs()
        viewModel.fetchContactsFromTurso()
    }
    
    if (activePreviewLog != null) {
        val log = activePreviewLog!!
        if (!log.mediaUrl.isNullOrBlank()) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { activePreviewLog = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    ViewerScreen(
                        uri = Uri.parse(log.mediaUrl),
                        isVideo = log.isVideo,
                        onBack = { activePreviewLog = null }
                    )
                }
            }
        }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Manage real-time cloud stats, app downloads, and publish system-wide updates instantly.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Overall Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                           Text("${statuses.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                           Text("Statuses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                           Text("${logs.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                           Text("Downloads", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                           Text("${adminContacts.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                           Text("Contacts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "System Update",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "App Auto-Update Center",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Upload a new APK here to trigger an automatic background update on all users' screens. When they open the app next time, it will download and install automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Active App Version: Version ${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputVersionCode,
                        onValueChange = { inputVersionCode = it },
                        label = { Text("New Version Code (Number)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputVersionName,
                        onValueChange = { inputVersionName = it },
                        label = { Text("New Version Name (e.g. 1.1)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputReleaseNotes,
                        onValueChange = { inputReleaseNotes = it },
                        label = { Text("Updates & Release Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputApkUrl,
                        onValueChange = { inputApkUrl = it },
                        label = { Text("Manual APK URL (Optional direct download)") },
                        placeholder = { Text("Or select file below to auto-upload to Firebase") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { apkPickerLauncher.launch("application/vnd.android.package-archive") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (selectedApkUri == null) "📁 Select APK File from Device" else "✅ APK Attached: $selectedApkFileName")
                    }

                    if (uploadProgress != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val progressPercent = (uploadProgress!! * 100).toInt()
                        Column {
                            Text("Uploading file to Firebase: $progressPercent%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { uploadProgress!! }, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val verCode = inputVersionCode.toIntOrNull() ?: (com.example.BuildConfig.VERSION_CODE + 1)
                                if (selectedApkUri != null) {
                                    viewModel.uploadApkToFirebase(
                                        uri = selectedApkUri!!,
                                        context = context,
                                        versionCode = verCode,
                                        versionName = inputVersionName,
                                        releaseNotes = inputReleaseNotes,
                                        isMandatory = true
                                    ) { success ->
                                        if (success) {
                                            selectedApkUri = null
                                            selectedApkFileName = ""
                                            android.widget.Toast.makeText(context, "Update published successfully!", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to publish update. Check internet or storage connections.", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else if (inputApkUrl.isNotBlank()) {
                                    viewModel.publishUpdate(
                                        AppUpdateConfig(
                                            versionCode = verCode,
                                            versionName = inputVersionName,
                                            apkUrl = inputApkUrl,
                                            releaseNotes = inputReleaseNotes,
                                            isMandatory = true
                                        )
                                    ) { success ->
                                        if (success) {
                                            android.widget.Toast.makeText(context, "Update published with URL!", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to publish update configuration.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Please select an APK file OR enter a manual URL first!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = uploadProgress == null
                        ) {
                            Text("Publish Update")
                        }

                        Button(
                            onClick = {
                                viewModel.disableUpdates { success ->
                                    if (success) {
                                        android.widget.Toast.makeText(context, "Enforced system updates disabled.", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "Failed to disable updates.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            shape = RoundedCornerShape(12.dp),
                            enabled = uploadProgress == null
                        ) {
                            Text("Disable Update")
                        }
                    }
                }
            }
        }
        
        item {
             Card(
                 modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                 colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                 shape = RoundedCornerShape(16.dp)
             ) {
                 Row(
                     modifier = Modifier.fillMaxWidth().padding(4.dp),
                     horizontalArrangement = Arrangement.spacedBy(4.dp)
                 ) {
                     val tabs = listOf("📥 Activity Logs", "👥 User Contacts DB")
                     tabs.forEachIndexed { index, tabName ->
                         val isSelected = adminSubTab == index
                         Button(
                             onClick = { adminSubTab = index },
                             modifier = Modifier.weight(1f),
                             colors = ButtonDefaults.buttonColors(
                                 containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                 contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                             ),
                             shape = RoundedCornerShape(12.dp),
                             elevation = null,
                             contentPadding = PaddingValues(vertical = 10.dp)
                         ) {
                             Text(tabName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                         }
                     }
                 }
             }
        }

        if (adminSubTab == 0) {
            item {
                 Text("Live Activity Logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                 Text("Real-time cloud data capture (tap items with play / image to preview)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                 Spacer(modifier = Modifier.height(12.dp))
                 
                 OutlinedTextField(
                     value = logSearchQuery,
                     onValueChange = { logSearchQuery = it },
                     label = { Text("Search logs by Mobile or File...") },
                     leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                     trailingIcon = {
                         if (logSearchQuery.isNotEmpty()) {
                             IconButton(onClick = { logSearchQuery = "" }) {
                                 Icon(Icons.Default.Close, contentDescription = "Clear search")
                             }
                         }
                     },
                     modifier = Modifier.fillMaxWidth(),
                     singleLine = true,
                     shape = RoundedCornerShape(12.dp)
                 )
                 
                 Spacer(modifier = Modifier.height(8.dp))
                 
                 Row(
                     modifier = Modifier.fillMaxWidth(),
                     horizontalArrangement = Arrangement.spacedBy(8.dp)
                 ) {
                     val options = listOf("All", "Videos", "Images")
                     options.forEach { option ->
                         val isSelected = mediaFilterType == option
                         Box(
                             modifier = Modifier
                                 .background(
                                     color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                     shape = RoundedCornerShape(16.dp)
                                 )
                                 .clickable { mediaFilterType = option }
                                 .padding(horizontal = 16.dp, vertical = 8.dp),
                             contentAlignment = Alignment.Center
                         ) {
                             Text(
                                 text = option,
                                 color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                 style = MaterialTheme.typography.bodyMedium,
                                 fontWeight = FontWeight.Bold
                             )
                         }
                     }
                 }
                 Spacer(modifier = Modifier.height(12.dp))
            }
            
            if (filteredLogs.isNotEmpty()) {
                items(filteredLogs) { log ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                                Icon(if (log.isVideo) Icons.Default.PlayCircleOutline else Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (log.isVideo) "Video Downloaded" else "Image Downloaded", fontWeight = FontWeight.Bold)
                                Text("User / Mobile: ${log.mobileNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text("File: ${log.fileName}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                
                                val timeVal = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
                                Text(timeVal, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            
                            if (!log.mediaUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { activePreviewLog = log },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(if (log.isVideo) "Play Video" else "View Image", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = if (logs.isEmpty()) "No downloads yet." else "No records match search & filter criteria.",
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
            }
        } else {
            item {
                 Row(
                     modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                     horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     Column {
                         Text("Live Address Book Backups", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                         Text("User contacts parsed & backed up securely in Turso DB", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                     }
                     IconButton(onClick = { viewModel.fetchContactsFromTurso() }) {
                         Icon(Icons.Default.Refresh, contentDescription = "Refresh Contacts List", tint = MaterialTheme.colorScheme.primary)
                     }
                 }
                 Spacer(modifier = Modifier.height(12.dp))
                 
                 OutlinedTextField(
                     value = contactSearchQuery,
                     onValueChange = { contactSearchQuery = it },
                     label = { Text("Search contact database by Name, Mobile, or Uploader...") },
                     leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                     trailingIcon = {
                         if (contactSearchQuery.isNotEmpty()) {
                             IconButton(onClick = { contactSearchQuery = "" }) {
                                 Icon(Icons.Default.Close, contentDescription = "Clear search")
                             }
                         }
                     },
                     modifier = Modifier.fillMaxWidth(),
                     singleLine = true,
                     shape = RoundedCornerShape(12.dp)
                 )
                 Spacer(modifier = Modifier.height(12.dp))
            }
            
            if (filteredAdminContacts.isNotEmpty()) {
                items(filteredAdminContacts) { contact ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = contact.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(text = "Phone: ${contact.phone}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "Uploaded By Uploader: ${contact.userIdAsMobile}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    var cleanNum = contact.phone.filter { it.isDigit() }
                                    if (cleanNum.startsWith("0")) {
                                        cleanNum = "92" + cleanNum.substring(1)
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNum"))
                                    intent.setPackage("com.whatsapp")
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNum")))
                                        } catch (ex: Exception) {
                                            android.widget.Toast.makeText(context, "Could not open WhatsApp", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF25D366).copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = "WhatsApp Chat",
                                    tint = Color(0xFF128C7E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = if (adminContacts.isEmpty()) "No uploaded contacts found in Turso DB yet." else "No contacts match search query.",
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

