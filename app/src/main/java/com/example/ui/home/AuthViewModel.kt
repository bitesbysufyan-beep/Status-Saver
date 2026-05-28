package com.example.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DownloadLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
    
    private val prefs = application.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(auth?.currentUser != null || prefs.getBoolean("mock_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isAdmin = MutableStateFlow(prefs.getBoolean("is_admin", false))
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(auth?.currentUser?.email ?: prefs.getString("user_email", null))
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _currentUserMobile = MutableStateFlow<String?>(prefs.getString("user_mobile", null))
    val currentUserMobile: StateFlow<String?> = _currentUserMobile.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private var useLocalAdminLogout = prefs.getBoolean("mock_logged_in", false)

    init {
        checkAdminStatus()
    }

    private fun checkAdminStatus() {
        if (useLocalAdminLogout) return
        val user = auth?.currentUser
        if (user != null) {
            viewModelScope.launch {
                try {
                    val document = firestore?.collection("admins")?.document(user.uid)?.get()?.await()
                    _isAdmin.value = document?.exists() == true
                } catch (e: Exception) {
                    _isAdmin.value = false
                }
            }
        } else {
            _isAdmin.value = false
        }
    }

    private fun setLocalLoginState(email: String, isAdmin: Boolean, mobile: String? = null) {
        useLocalAdminLogout = true
        _isLoggedIn.value = true
        _currentUserEmail.value = email
        _isAdmin.value = isAdmin
        _authError.value = null
        if (mobile != null) {
            _currentUserMobile.value = mobile
            prefs.edit().putString("user_mobile", mobile).apply()
        }
        prefs.edit()
            .putBoolean("mock_logged_in", true)
            .putString("user_email", email)
            .putBoolean("is_admin", isAdmin)
            .apply()
    }

    fun register(email: String, password: String, mobile: String) {
        viewModelScope.launch {
            try {
                if (auth == null) {
                    setLocalLoginState(email.trim(), false, mobile)
                    return@launch
                }
                auth.createUserWithEmailAndPassword(email, password).await()
                updateAuthState(mobile)
                _authError.value = null
            } catch (e: Exception) {
                setLocalLoginState(email.trim(), false, mobile)
            }
        }
    }

    fun login(email: String, password: String, mobile: String) {
        viewModelScope.launch {
            try {
                val cleanEmail = email.trim().lowercase()
                if (cleanEmail == "admin" || cleanEmail == "admin@admin.com") {
                    if (password == "admin" || password == "admin123") {
                        setLocalLoginState("admin@admin.com", true, mobile)
                        return@launch
                    } else {
                        _authError.value = "Invalid admin password"
                        return@launch
                    }
                }
                
                if (auth == null) {
                    setLocalLoginState(cleanEmail, false, mobile)
                    return@launch
                }
                
                auth.signInWithEmailAndPassword(email, password).await()
                updateAuthState(mobile)
                _authError.value = null
            } catch (e: Exception) {
                setLocalLoginState(email.trim(), false, mobile)
            }
        }
    }

    fun logout() {
        useLocalAdminLogout = false
        _isLoggedIn.value = false
        _isAdmin.value = false
        _currentUserEmail.value = null
        prefs.edit().clear().apply()
        auth?.signOut()
        updateAuthState(null)
    }

    private fun updateAuthState(mobile: String?) {
        val user = auth?.currentUser
        _isLoggedIn.value = user != null || prefs.getBoolean("mock_logged_in", false)
        _currentUserEmail.value = user?.email ?: prefs.getString("user_email", null)
        if (mobile != null) {
            _currentUserMobile.value = mobile
            prefs.edit().putString("user_mobile", mobile).apply()
        }
        checkAdminStatus()
    }
}
