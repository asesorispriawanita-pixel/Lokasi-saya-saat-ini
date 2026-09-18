package com.lokasisaya.target

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.lokasisaya.shared.FirebaseConfig

class MainActivity : AppCompatActivity() {

    private lateinit var codeInput: EditText
    private lateinit var statusText: TextView

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val ok = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (ok) requestNotificationThenStart()
            else statusText.text = "Izin lokasi ditolak. Aktifkan izin lokasi untuk melanjutkan."
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startLocationService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseConfig.init(this)
        FirebaseAuth.getInstance().signInAnonymously()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
        }

        val title = TextView(this).apply {
            text = "📱 LOKASI SAYA"
            textSize = 28f
            setPadding(0, 0, 0, 18)
        }

        val subtitle = TextView(this).apply {
            text = "Bagikan lokasi GPS ke Admin menggunakan nomor atau kode pengguna."
            textSize = 15f
            setPadding(0, 0, 0, 16)
        }

        codeInput = EditText(this).apply {
            hint = "Nomor HP / kode pengguna"
            textSize = 18f
            setSingleLine(true)
        }

        val startButton = Button(this).apply {
            text = "📍 MULAI BAGIKAN LOKASI"
            setOnClickListener { beginSharing() }
        }

        val stopButton = Button(this).apply {
            text = "⏹ BERHENTI"
            setOnClickListener {
                stopService(Intent(this@MainActivity, LocationService::class.java))
                statusText.text = "Berbagi lokasi dihentikan."
            }
        }

        statusText = TextView(this).apply {
            text = "Belum aktif."
            textSize = 16f
            setPadding(0, 22, 0, 0)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(codeInput, LinearLayout.LayoutParams(-1, 65))
        root.addView(startButton)
        root.addView(stopButton)
        root.addView(statusText)

        setContentView(root)
    }

    private fun beginSharing() {
        val code = codeInput.text.toString().trim()
        if (code.isBlank()) {
            codeInput.error = "Masukkan nomor/kode pengguna"
            return
        }

        getSharedPreferences("lokasi", MODE_PRIVATE)
            .edit()
            .putString("code", code)
            .apply()

        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (fine == PackageManager.PERMISSION_GRANTED ||
            coarse == PackageManager.PERMISSION_GRANTED) {
            requestNotificationThenStart()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun requestNotificationThenStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startLocationService()
        }
    }

    private fun startLocationService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, LocationService::class.java)
        )
        statusText.text = "✅ Lokasi sedang dibagikan secara realtime."
    }
}
