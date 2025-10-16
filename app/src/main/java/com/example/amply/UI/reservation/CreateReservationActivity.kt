package com.example.amply.ui.reservation

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.amply.R
import com.example.amply.data.AuthDatabaseHelper
import com.example.amply.data.ReservationDatabaseHelper
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.text.SimpleDateFormat
import java.util.*

class CreateReservationActivity : AppCompatActivity() {

    private lateinit var dbHelper: ReservationDatabaseHelper
    private var isUpdateMode = false
    private var updateReservationId: String? = null
    private var stationList: List<ChargingStation> = emptyList()

    // -------------------- Data Classes --------------------
    data class ReservationCreateRequest(
        val NIC: String?,
        val FullName: String,
        val VehicleNumber: String,
        val StationId: String,
        val StationName: String,
        val SlotNo: Int,
        val ReservationDate: String,
        val StartTime: String,
        val EndTime: String
    )

    data class Slot(
        val slotNumber: Int,
        val startTime: String,
        val endTime: String,
        val isAvailable: Boolean
    )

    data class ScheduleByDate(
        val date: String,
        val slots: List<Slot>
    )

    data class ChargingStation(
        val stationId: String,
        val stationName: String,
        val scheduleByDate: List<ScheduleByDate>
    )

    // -------------------- API Interfaces --------------------
    interface ReservationApi {
        @POST("api/v1/reservations")
        fun createReservation(@Body reservation: ReservationCreateRequest): Call<Void>

        @PUT("api/v1/reservations/{id}")
        fun updateReservation(@Path("id") id: String, @Body reservation: ReservationCreateRequest): Call<Void>
    }

    interface ChargingStationApi {
        @GET("api/v1/charging-stations/active")
        fun getActiveStations(): Call<List<ChargingStation>>
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_reservation)

        dbHelper = ReservationDatabaseHelper(this)
        val authDbHelper = AuthDatabaseHelper(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.createReservation)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val tvNIC = findViewById<EditText>(R.id.tvNIC)
        val tvFullName = findViewById<EditText>(R.id.tvFullName)
        val tvVehicleNumber = findViewById<EditText>(R.id.tvVehicleNumber)
        val tvStationId = findViewById<EditText>(R.id.tvStationId)
        val tvReservationDate = findViewById<EditText>(R.id.tvReservationDate)
        val tvSlotNo = findViewById<EditText>(R.id.tvSlotNo)
        val tvStartTime = findViewById<EditText>(R.id.tvStartTime)
        val tvEndTime = findViewById<EditText>(R.id.tvEndTime)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitReservation)
        val spinnerStation = findViewById<Spinner>(R.id.spinnerStation)

        // -------------------- Load logged-in user --------------------
        val loggedEmail = authDbHelper.getLoggedInUserEmail()
        val userProfile = loggedEmail?.let { authDbHelper.getUserProfile(it) }
        if (userProfile != null) {
            tvFullName.setText(userProfile["fullName"] ?: "")
            tvNIC.setText(userProfile["nic"] ?: "")
            tvFullName.isEnabled = false
            tvNIC.isEnabled = false
        }

        // -------------------- Update mode --------------------
        isUpdateMode = intent.getBooleanExtra("isUpdate", false)
        if (isUpdateMode) {
            updateReservationId = intent.getStringExtra("id")
            tvNIC.setText(intent.getStringExtra("nic"))
            tvFullName.setText(intent.getStringExtra("fullName"))
            tvVehicleNumber.setText(intent.getStringExtra("vehicleNumber"))
            tvSlotNo.setText(intent.getIntExtra("slotNo", 0).toString())
            tvReservationDate.setText(intent.getStringExtra("reservationDate"))
            tvStartTime.setText(formatTo12Hour(intent.getStringExtra("startTime") ?: "00:00:00"))
            tvEndTime.setText(formatTo12Hour(intent.getStringExtra("endTime") ?: "00:00:00"))
            btnSubmit.text = "Update Reservation"
        }

        // -------------------- Fetch stations --------------------
        fetchActiveStations(spinnerStation, tvStationId, tvSlotNo, tvStartTime, tvEndTime, tvReservationDate)

        // -------------------- Date Picker --------------------
        tvReservationDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val today = calendar.timeInMillis
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    tvReservationDate.setText(formattedDate)
                    autoFillSlotAndTime(formattedDate, spinnerStation, tvSlotNo, tvStartTime, tvEndTime)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = today
            calendar.add(Calendar.DAY_OF_MONTH, 7)
            datePicker.datePicker.maxDate = calendar.timeInMillis
            datePicker.show()
        }

        // -------------------- Time Picker --------------------
        fun showTimePicker(editText: EditText) {
            val calendar = Calendar.getInstance()
            val timePicker = TimePickerDialog(this, { _, hourOfDay, minute ->
                val amPm = if (hourOfDay >= 12) "PM" else "AM"
                val hour = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
                editText.setText(String.format("%02d:%02d %s", hour, minute, amPm))
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
            timePicker.show()
        }

        tvStartTime.setOnClickListener { showTimePicker(tvStartTime) }
        tvEndTime.setOnClickListener { showTimePicker(tvEndTime) }

        // -------------------- Submit Button --------------------
        btnSubmit.setOnClickListener {
            val selectedStationName = spinnerStation.selectedItem?.toString() ?: ""
            val reservation = ReservationCreateRequest(
                NIC = tvNIC.text.toString(),
                FullName = tvFullName.text.toString(),
                VehicleNumber = tvVehicleNumber.text.toString(),
                StationName = selectedStationName,
                StationId = tvStationId.text.toString(),
                ReservationDate = tvReservationDate.text.toString(),
                SlotNo = tvSlotNo.text.toString().toIntOrNull() ?: 0,
                StartTime = convertTo24Hour(tvStartTime.text.toString()),
                EndTime = convertTo24Hour(tvEndTime.text.toString())
            )

            if (reservation.FullName.isEmpty() || reservation.VehicleNumber.isEmpty() ||
                reservation.StationName.isEmpty() || reservation.ReservationDate.isEmpty() ||
                reservation.StartTime.isEmpty() || reservation.EndTime.isEmpty()
            ) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showReservationPreviewDialog(reservation)
        }
    }

    // -------------------- Time Conversion Functions --------------------
    private fun convertTo24Hour(timeStr: String): String {
        val parts = timeStr.split(" ")
        if (parts.size != 2) return "00:00:00"
        val hm = parts[0].split(":")
        var hour = hm[0].toIntOrNull() ?: 0
        val minute = hm[1].toIntOrNull() ?: 0
        val amPm = parts[1]
        if (amPm.equals("PM", true) && hour < 12) hour += 12
        if (amPm.equals("AM", true) && hour == 12) hour = 0
        return String.format("%02d:%02d:00", hour, minute)
    }

    private fun formatTo12Hour(time24: String): String {
        return try {
            val sdf24 = if (time24.count { it == ':' } == 1)
                SimpleDateFormat("HH:mm", Locale.getDefault())
            else
                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = sdf24.parse(time24)
            sdf12.format(date!!)
        } catch (e: Exception) {
            time24
        }
    }

    // -------------------- Fetch Active Stations --------------------
    private fun fetchActiveStations(
        spinner: Spinner,
        tvStationId: EditText,
        tvSlotNo: EditText,
        tvStartTime: EditText,
        tvEndTime: EditText,
        tvReservationDate: EditText
    ) {
        val api = getRetrofit().create(ChargingStationApi::class.java)
        api.getActiveStations().enqueue(object : Callback<List<ChargingStation>> {
            override fun onResponse(call: Call<List<ChargingStation>>, response: Response<List<ChargingStation>>) {
                if (response.isSuccessful && response.body() != null) {
                    stationList = response.body()!!
                    val stationNames = mutableListOf("Select Station").apply { addAll(stationList.map { it.stationName }) }
                    val adapter = object : ArrayAdapter<String>(this@CreateReservationActivity, R.layout.spinner_item, stationNames) {
                        override fun isEnabled(position: Int) = position != 0
                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val view = super.getDropDownView(position, convertView, parent) as TextView
                            view.setTextColor(resources.getColor(R.color.white))
                            return view
                        }
                    }
                    adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                    spinner.adapter = adapter

                    // Set previous station if in update mode
                    if (isUpdateMode) {
                        val prevStationName = intent.getStringExtra("stationName")
                        val index = stationNames.indexOf(prevStationName)
                        if (index >= 0) {
                            spinner.setSelection(index)
                        }
                    } else {
                        spinner.setSelection(0)
                    }

                    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                            if (position != 0) {
                                val selectedStation = stationList[position - 1]
                                tvStationId.setText(selectedStation.stationId)
                                val dateStr = tvReservationDate.text.toString()
                                if (dateStr.isNotEmpty()) autoFillSlotAndTime(dateStr, spinner, tvSlotNo, tvStartTime, tvEndTime)
                            } else tvStationId.setText("")
                        }
                        override fun onNothingSelected(parent: AdapterView<*>) {}
                    }
                } else Toast.makeText(this@CreateReservationActivity, "Failed to load stations", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(call: Call<List<ChargingStation>>, t: Throwable) {
                Toast.makeText(this@CreateReservationActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // -------------------- Auto-fill Slot --------------------
    private fun autoFillSlotAndTime(date: String, spinner: Spinner, tvSlotNo: EditText, tvStartTime: EditText, tvEndTime: EditText) {
        val pos = spinner.selectedItemPosition
        if (pos <= 0) return
        val station = stationList[pos - 1]
        val backendFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val compareFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val schedule = station.scheduleByDate.find { sch ->
            try { compareFormat.format(backendFormat.parse(sch.date)!!) == date } catch (e: Exception) { false }
        }

        if (schedule != null) {
            val slot = schedule.slots.find { it.isAvailable }
            if (slot != null) {
                tvSlotNo.setText(slot.slotNumber.toString())
                tvStartTime.setText(formatTo12Hour(slot.startTime))
                tvEndTime.setText(formatTo12Hour(slot.endTime))
            } else {
                tvSlotNo.setText(""); tvStartTime.setText(""); tvEndTime.setText("")
                Toast.makeText(this, "No available slots for this date", Toast.LENGTH_SHORT).show()
            }
        } else {
            tvSlotNo.setText(""); tvStartTime.setText(""); tvEndTime.setText("")
            Toast.makeText(this, "No schedule for this date", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------- Retrofit Helper --------------------
    private fun getRetrofit(): Retrofit {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        return Retrofit.Builder()
            .baseUrl("https://conor-truculent-rurally.ngrok-free.dev/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun saveOffline(reservation: ReservationCreateRequest) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = sdf.format(Date())
        val code = "OFFLINE-${System.currentTimeMillis()}"
        dbHelper.addReservation(
            reservationCode = code,
            fullName = reservation.FullName,
            nic = reservation.NIC ?: "",
            vehicleNumber = reservation.VehicleNumber,
            stationId = reservation.StationId,
            stationName = reservation.StationName,
            slotNo = reservation.SlotNo,
            bookingDate = now,
            reservationDate = reservation.ReservationDate,
            startTime = reservation.StartTime,
            endTime = reservation.EndTime,
            status = "Pending Sync",
            qrCode = null,
            createdAt = now,
            updatedAt = now
        )
    }

    // -------------------- API Calls --------------------
    private fun createReservation(reservation: ReservationCreateRequest) {
        val api = getRetrofit().create(ReservationApi::class.java)
        api.createReservation(reservation).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@CreateReservationActivity, "Reservation created successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else Toast.makeText(this@CreateReservationActivity, "API Error: ${response.code()}", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                saveOffline(reservation)
                Toast.makeText(this@CreateReservationActivity, "Offline — saved locally", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateReservation(id: String, reservation: ReservationCreateRequest) {
        val api = getRetrofit().create(ReservationApi::class.java)
        api.updateReservation(id, reservation).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@CreateReservationActivity, "Reservation updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else Toast.makeText(this@CreateReservationActivity, "Error: ${response.code()}", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@CreateReservationActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // -------------------- Preview Dialog --------------------
    @SuppressLint("SetTextI18n")
    private fun showReservationPreviewDialog(reservation: ReservationCreateRequest) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reservation_preview, null)
        val tvSummary = dialogView.findViewById<TextView>(R.id.tvReservationSummary)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        tvSummary.text = """
            NIC: ${reservation.NIC}
            Full Name: ${reservation.FullName}
            Vehicle Number: ${reservation.VehicleNumber}
            Station ID: ${reservation.StationId}
            Station Name: ${reservation.StationName}
            Slot Number: ${reservation.SlotNo}
            Reservation Date: ${reservation.ReservationDate}
            Start Time: ${formatTo12Hour(reservation.StartTime)}
            End Time: ${formatTo12Hour(reservation.EndTime)}
        """.trimIndent()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            if (isOnline(this)) {
                if (isUpdateMode && updateReservationId != null) updateReservation(updateReservationId!!, reservation)
                else createReservation(reservation)
            } else {
                saveOffline(reservation)
                Toast.makeText(this, "Offline — Reservation saved locally", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        dialog.show()
    }
}
