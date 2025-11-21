package com.example.remembersafetyfirst

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.remembersafetyfirst.viewmodel.AuthViewModel

class RegisterActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        val nameInput = findViewById<EditText>(R.id.name_input)
        val emailInput = findViewById<EditText>(R.id.email_input)
        val passInput = findViewById<EditText>(R.id.password_input)
        val btnRegister = findViewById<Button>(R.id.btn_register)

        // Observer for errors
        viewModel.error.observe(this) { error ->
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }

        // Observer for success (Current User becomes not null)
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                // Registration successful -> Go to Main
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }

        btnRegister.setOnClickListener {
            val name = nameInput.text.toString()
            val email = emailInput.text.toString()
            val pass = passInput.text.toString()

            if (email.isNotEmpty() && pass.isNotEmpty() && name.isNotEmpty()) {
                viewModel.signUpWithEmailPassword(email, pass, name)
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
}