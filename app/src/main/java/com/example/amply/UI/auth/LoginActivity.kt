package com.example.amply.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.amply.data.AuthDatabaseHelper
import com.example.amply.ui.dashboard.EvOperatorDashboard
import com.example.amply.ui.dashboard.EvOwnerDashboard
import com.example.amply.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var dbHelper: AuthDatabaseHelper

    // -------------------- Retrofit Models --------------------
    data class UserProfile(
        val email: String,
        val password: String,
        val role: String?,
        val fullName: String,
        val nic: String?,
        val phone: String?,
        val address_no: String?,
        val address_street: String?,
        val address_city: String?
    )

    // -------------------- Retrofit API --------------------
    interface UserProfileApi {
        @GET("api/v1/userprofiles")
        fun getUserProfiles(): Call<List<UserProfile>>
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        dbHelper = AuthDatabaseHelper(this)

        val etEmail = findViewById<EditText>(R.id.email)
        val etPassword = findViewById<EditText>(R.id.password)
        val btnLogin = findViewById<Button>(R.id.loginBtn)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter both email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // First, try local database
            if (dbHelper.validateUser(email, password)) {
                Toast.makeText(this, "Login successful (offline)", Toast.LENGTH_SHORT).show()
                val role = dbHelper.getUserRole(email)
                navigateBasedOnRole(role)
            } else {
                // Authenticate via API and save full profile
                authenticateAndSaveUser(email, password)
            }
        }
    }

    private fun authenticateAndSaveUser(email: String, password: String) {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://conor-truculent-rurally.ngrok-free.dev/") // replace with your API
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val userApi = retrofit.create(UserProfileApi::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = userApi.getUserProfiles().execute()
                withContext(Dispatchers.Main) {
                    if (!response.isSuccessful) {
                        Toast.makeText(this@LoginActivity, "Failed to fetch users", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    val users = response.body() ?: emptyList()
                    val matchedUser = users.find { it.email.equals(email, true) && it.password == password }

                    if (matchedUser != null) {
                        // Save full profile to SQLite
                        if (!dbHelper.checkUser(email)) {
                            dbHelper.addUser(
                                email = matchedUser.email,
                                password = matchedUser.password,
                                role = matchedUser.role ?: "",
                                fullName = matchedUser.fullName,
                                nic = matchedUser.nic ?: "",
                                phone = matchedUser.phone ?: "",
                                addressNo = matchedUser.address_no ?: "",
                                addressStreet = matchedUser.address_street ?: "",
                                addressCity = matchedUser.address_city ?: ""
                            )
                        }

                        Toast.makeText(this@LoginActivity, "Welcome ${matchedUser.fullName}", Toast.LENGTH_SHORT).show()
                        navigateBasedOnRole(matchedUser.role)
                    } else {
                        Toast.makeText(this@LoginActivity, "Invalid email or password", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Server timeout. Try again later.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateBasedOnRole(role: String?) {
        when (role) {
            "ElectiveOperator" -> startActivity(Intent(this, EvOperatorDashboard::class.java))
            "EvOwner" -> startActivity(Intent(this, EvOwnerDashboard::class.java))
            else -> Toast.makeText(this, "Unknown role: $role", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
