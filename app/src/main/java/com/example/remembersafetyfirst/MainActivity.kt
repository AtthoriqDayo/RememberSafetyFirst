package com.example.remembersafetyfirst

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.remembersafetyfirst.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    // Removed the duplicate 'binding' declaration
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // accessing 'appBarMain' now works because we added the ID in step 1
        setSupportActionBar(binding.appBarMain.toolbar)

        drawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // ... inside onCreate() ...
        val headerView = navView.getHeaderView(0)
        val navUserEmail = headerView.findViewById<TextView>(R.id.tv_user_email)
        val navUserName = headerView.findViewById<TextView>(R.id.tv_user_name)

        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            navUserEmail.text = currentUser.email


            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("displayName")
                        navUserName.text = name ?: "SafetyFirst User"
                    }
                }
        } else {
            navUserEmail.text = "Guest"
            navUserName.text = "Guest User"
        }
        // Setup the Hamburger Icon
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.FirstFragment), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        // This connects the sidebar clicks to the navigation controller
        navView.setupWithNavController(navController)

        // Handle Logout manually
        // Handle Navigation Item Clicks
        navView.setNavigationItemSelectedListener { menuItem ->

            if (menuItem.itemId == R.id.nav_logout) {
                // 1. Sign out from Firebase
                FirebaseAuth.getInstance().signOut()

                // 2. Create Intent for Login
                val intent = Intent(this, LoginActivity::class.java)

                // 3. NUCLEAR OPTION: Clear the entire history stack
                // This ensures the user cannot press "Back" to return to the dashboard
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                startActivity(intent)
                finish() // Kill this activity immediately
            } else {
                // Handle other clicks (Dashboard, Devices) normally
                // This line makes the sidebar navigation work for other items
                androidx.navigation.ui.NavigationUI.onNavDestinationSelected(menuItem, navController)
                drawerLayout.closeDrawers()
            }
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}