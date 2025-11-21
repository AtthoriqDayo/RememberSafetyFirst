@file:Suppress("DEPRECATION")

package com.example.remembersafetyfirst.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.remembersafetyfirst.BuildConfig
import com.example.remembersafetyfirst.data.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    val googleSignInClient: GoogleSignInClient

    // LiveData for UI to observe
    private val _currentUser = MutableLiveData<FirebaseUser?>()
    val currentUser: LiveData<FirebaseUser?> = _currentUser

    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> = _userData

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        // Configure Google Sign In (Same as FastPark)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID) // Make sure this is in build.gradle
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(application, gso)

        // Listen for auth changes
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.postValue(user)
            if (user != null) {
                fetchUserData(user.uid)
            } else {
                _userData.postValue(null)
            }
        }
    }

    // 1. Sign In Email
    fun signInWithEmailPassword(email: String, pass: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, pass).await()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 2. Sign Up Email
    fun signUpWithEmailPassword(email: String, pass: String, name: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, pass).await()
                val user = result.user
                if (user != null) {
                    val newUser = User(user.uid, email, name)
                    saveUserToFirestore(newUser)
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 3. Google Sign In
    fun signInWithGoogle(credential: AuthCredential) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = auth.signInWithCredential(credential).await()
                val user = result.user
                if (user != null) {
                    // Check if user exists, if not create basic profile
                    val doc = db.collection("users").document(user.uid).get().await()
                    if (!doc.exists()) {
                        val newUser = User(user.uid, user.email ?: "", user.displayName ?: "User")
                        saveUserToFirestore(newUser)
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
    }

    private suspend fun saveUserToFirestore(user: User) {
        withContext(Dispatchers.IO) {
            db.collection("users").document(user.uid).set(user, SetOptions.merge()).await()
        }
    }

    private fun fetchUserData(uid: String) {
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                _userData.postValue(snapshot.toObject(User::class.java))
            }
        }
    }
}