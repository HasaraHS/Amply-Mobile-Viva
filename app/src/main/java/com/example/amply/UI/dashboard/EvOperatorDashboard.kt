package com.example.amply.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.amply.R
import com.example.amply.data.AuthDatabaseHelper
import com.example.amply.ui.EVOperatorApp.QRScannerActivity
import com.example.amply.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton

class EvOperatorDashboard : AppCompatActivity() {

    private lateinit var btnStartScanning: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ev_operator_dashboard)

        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            // Clear local SQLite user data (optional)
            val dbHelper = AuthDatabaseHelper(this)
            dbHelper.clearCurrentUser() // implement this in your helper

            // Navigate back to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        btnStartScanning = findViewById(R.id.btnStartScanning)
    }

    private fun setupClickListeners() {
        btnStartScanning.setOnClickListener {
            startQRScanner()
        }
    }

    private fun startQRScanner() {
        val intent = Intent(this, QRScannerActivity::class.java)
        startActivity(intent)
    }
}