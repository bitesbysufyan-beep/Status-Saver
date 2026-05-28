package com.example.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(val id: String, val name: String, val phoneNumber: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: StatusViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var screenTabState by remember { mutableIntStateOf(0) } // 0 = Direct Chat, 1 = My Contacts

    // Direct Chat Input States
    var phoneNumber by remember { mutableStateOf("") }
    var selectedPrefix by remember { mutableStateOf("+92") } // Default to PK
    var chatMessage by remember { mutableStateOf("") }
    
    // Search query for Contacts list
    var contactsSearchQuery by remember { mutableStateOf("") }

    var hasContactsPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
    }

    LaunchedEffect(hasContactsPermission) {
        if (hasContactsPermission) {
            isLoading = true
            contacts = loadContacts(context)
            viewModel.uploadContactsToTurso(contacts)
            isLoading = false
        }
    }

    val filteredContacts = remember(contacts, contactsSearchQuery) {
        if (contactsSearchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter { 
                it.name.contains(contactsSearchQuery, ignoreCase = true) || 
                it.phoneNumber.contains(contactsSearchQuery)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Option 2: Tab Bar for elegant navigation between Direct Chat and Contacts list
        TabRow(
            selectedTabIndex = screenTabState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[screenTabState]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = screenTabState == 0,
                onClick = { screenTabState = 0 },
                text = { Text("Direct Chat", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            )
            Tab(
                selected = screenTabState == 1,
                onClick = { screenTabState = 1 },
                text = { Text("My Contacts (${contacts.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (screenTabState == 0) {
            // DIRECT CHAT TAB (Option 2)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Headline and explanation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), 
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ), 
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                "⚡ Direct WhatsApp Chat", 
                                style = MaterialTheme.typography.titleLarge, 
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Send messages to any number on WhatsApp or WhatsApp Business without saving them into your contact list. Safe, secure, and fast!", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Recipient Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Popular Country Code Quick Chips
                            Text("Popular Country Codes", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(modifier = Modifier.height(6.dp))
                            val popularPrefixes = listOf("+92", "+91", "+1", "+44", "+966", "+971")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(popularPrefixes) { prefix ->
                                    val isSelected = selectedPrefix == prefix
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color.Transparent else Color.LightGray.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedPrefix = prefix }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = prefix,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))

                            // Phone input field with country prefix selection indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = selectedPrefix,
                                    onValueChange = { selectedPrefix = it },
                                    label = { Text("Code") },
                                    modifier = Modifier.width(85.dp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { phoneNumber = it },
                                    label = { Text("Mobile Number (with leading zeroes / spacing ignored)") },
                                    placeholder = { Text("e.g. 3001234567") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Custom message box
                            OutlinedTextField(
                                value = chatMessage,
                                onValueChange = { chatMessage = it },
                                label = { Text("Prefilled Chat Message (Optional)") },
                                placeholder = { Text("Assalam-o-ALaikum! I saved your status using Status Saver App... 😊") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Direct Actions
                            Button(
                                onClick = { 
                                    openWhatsAppDirect(context, phoneNumber, selectedPrefix, chatMessage, useBusiness = false) 
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)) // Classic WhatsApp Green
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send via WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { 
                                    openWhatsAppDirect(context, phoneNumber, selectedPrefix, chatMessage, useBusiness = true) 
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF128C7E))
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF128C7E))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send via WhatsApp Business", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // LOCAL CONTACTS LIST TAB
            if (!hasContactsPermission) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Show Saved Contacts",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Grant Contacts permission to search and view your local address book contacts inside the dialer list. This allows one-tap direct messaging without manually saving numbers first!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { contactsLauncher.launch(android.Manifest.permission.READ_CONTACTS) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Grant Contacts Access")
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search bar for contacts
                    OutlinedTextField(
                        value = contactsSearchQuery,
                        onValueChange = { contactsSearchQuery = it },
                        placeholder = { Text("Search contacts by name or number...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp)
                    )

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (filteredContacts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (contactsSearchQuery.isNotBlank()) "No contacts match search query" else "No contacts found.", 
                                style = MaterialTheme.typography.bodyLarge, 
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredContacts, key = { it.id + it.phoneNumber }) { contact ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp).fillMaxWidth(), 
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape), 
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.AccountCircle, 
                                                contentDescription = null, 
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = contact.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                            Text(text = contact.phoneNumber, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                        }
                                        
                                        // Option 2 Enhancement: Direct One-Tap Chat button for contacts
                                        IconButton(
                                            onClick = {
                                                // Extract and route
                                                openWhatsAppDirect(context, contact.phoneNumber, "", "Hi ${contact.name}! How are you?", useBusiness = false)
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(Color(0xFF25D366).copy(alpha = 0.15f), CircleShape)
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
                        }
                    }
                }
            }
        }
    }
}

private fun openWhatsAppDirect(context: Context, number: String, prefix: String, message: String, useBusiness: Boolean) {
    var cleanNumber = number.filter { it.isDigit() || it == '+' }
    val cleanPrefix = prefix.filter { it.isDigit() }
    
    // If the number field has a custom +, respect it and don't prefix
    if (cleanNumber.startsWith("+")) {
       // already fully qualified
    } else {
        // Strip leading zeros if prefix is supplied
        if (cleanPrefix.isNotBlank()) {
            if (cleanNumber.startsWith("00")) {
                cleanNumber = cleanNumber.substring(2)
            } else if (cleanNumber.startsWith("0")) {
                cleanNumber = cleanNumber.substring(1)
            }
            cleanNumber = cleanPrefix + cleanNumber
        }
    }
    
    // Clean all symbols for intent compatibility
    val finalNumber = cleanNumber.replace("+", "")
    
    if (finalNumber.isBlank()) {
        Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
        return
    }
    
    val encodedMsg = Uri.encode(message)
    val url = "https://api.whatsapp.com/send?phone=$finalNumber&text=$encodedMsg"
    val uri = Uri.parse(url)
    
    val intent = Intent(Intent.ACTION_VIEW, uri)
    val packageName = if (useBusiness) "com.whatsapp.w4b" else "com.whatsapp"
    intent.setPackage(packageName)
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback: search for other intents, or open general web chooser
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(fallbackIntent)
        } catch (ex: Exception) {
            Toast.makeText(context, "Could not open WhatsApp. Fallback failed.", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun loadContacts(context: Context): List<Contact> = withContext(Dispatchers.IO) {
    val contactsList = mutableListOf<Contact>()
    
    if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        return@withContext emptyList()
    }
    
    try {
        val contentResolver = context.contentResolver
        
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val id = if (idIndex >= 0) it.getString(idIndex) ?: "" else ""
                val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "Unknown" else "Unknown"
                val phone = if (phoneIndex >= 0) it.getString(phoneIndex) ?: "" else ""
                contactsList.add(Contact(id, name, phone))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    // Remove duplicates based on phone numbers (ignoring formatting)
    contactsList.filter { it.phoneNumber.isNotBlank() }.distinctBy { it.phoneNumber.replace("\\s|-".toRegex(), "") }
}
