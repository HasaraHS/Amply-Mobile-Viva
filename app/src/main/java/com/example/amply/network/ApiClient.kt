package com.example.amply.network

import com.example.amply.BuildConfig
import com.example.amply.model.Reservation
import com.example.amply.model.ReservationStatusUpdate
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * ApiClient
 * Singleton object that provides a centralized API client for the application
 * Uses the same pattern and base URL as MainActivity.kt
 */
object ApiClient {
    
    // Base URL - same as used in MainActivity.kt
    private const val BASE_URL = BuildConfig.BASE_URL
    
    /**
     * Retrofit API Interface for Reservation operations
     */
    interface ReservationApiService {
        @GET("api/v1/reservations/{id}")
        fun getReservationById(@Path("id") id: String): Call<Reservation>
        
        @GET("api/v1/reservations")
        fun getAllReservations(): Call<List<Reservation>>
        
        @PUT("api/v1/reservations/{id}")
        fun updateReservation(@Path("id") id: String, @Body reservation: Reservation): Call<Void>
        
        @PATCH("api/v1/reservations/{id}/confirm")
        fun confirmReservation(@Path("id") id: String): Call<Void>
        
        @PATCH("api/v1/reservations/{id}/done")
        fun markReservationAsDone(@Path("id") id: String): Call<Void>
    }
    
    /**
     * Create OkHttpClient with logging interceptor
     * Same configuration as MainActivity.kt
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()
    
    /**
     * Retrofit instance
     * Configured with the same base URL and converter as MainActivity.kt
     */
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    /**
     * Reservation API Service instance
     * This is the main service used by BookingDetailsActivity and FinalizeOperationActivity
     */
    val reservationApiService: ReservationApiService = retrofit.create(ReservationApiService::class.java)
}


