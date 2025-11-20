package com.example.remembersafetyfirst

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            checkLoginStatus()
        }, 2000) // Show logo for 2 seconds
    }

    private fun checkLoginStatus() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // User is logged in -> Go to Dashboard
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // User is NOT logged in -> Go to Login
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}