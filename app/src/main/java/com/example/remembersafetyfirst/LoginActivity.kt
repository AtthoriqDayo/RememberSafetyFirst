package com.example.remembersafetyfirst

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.remembersafetyfirst.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel

    // Google Sign In Launcher
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                viewModel.signInWithGoogle(credential)
            } catch (e: Exception) {
                Toast.makeText(this, "Google Sign In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        val emailInput = findViewById<EditText>(R.id.email_input)
        val passInput = findViewById<EditText>(R.id.password_input)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val btnGoogle = findViewById<Button>(R.id.btn_google)
        val btnRegister = findViewById<Button>(R.id.btn_register)

        // 1. Observe Login Success
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        // 2. Observe Errors
        viewModel.error.observe(this) { error ->
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }

        // 3. Email Login Action
        btnLogin.setOnClickListener {
            val email = emailInput.text.toString()
            val pass = passInput.text.toString()
            if (email.isNotEmpty() && pass.isNotEmpty()) {
                viewModel.signInWithEmailPassword(email, pass)
            }
        }

        // 4. Google Login Action
        btnGoogle.setOnClickListener {
            val signInIntent = viewModel.googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // 5. Go to Register Page
        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}