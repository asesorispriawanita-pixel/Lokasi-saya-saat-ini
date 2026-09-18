package com.lokasisaya.target

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.lokasisaya.shared.FirebaseConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class LocationService : LifecycleService() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var targetRef: DatabaseReference

    private val auth by lazy { FirebaseAuth.getInstance() }
    private var authBusy = false
    private var previousLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::saveLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseConfig.init(this)
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LOKASI SAYA")
            .setContentText("Lokasi GPS sedang dibagikan")
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        fused = LocationServices.getFusedLocationProviderClient(this)

        val code = getSharedPreferences("lokasi", MODE_PRIVATE)
            .getString("code", "")
            ?.trim()
            .orEmpty()

        if (code.isBlank()) {
            stopSelf()
            return
        }

        targetRef = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(toFirebaseKey(code))
            .child("target")

        auth.signInAnonymously()
            .addOnSuccessListener {
                targetRef.onDisconnect().updateChildren(
                    mapOf(
                        "status" to "offline",
                        "timestamp" to ServerValue.TIMESTAMP
                    )
                )
                requestUpdates()
            }
            .addOnFailureListener {
                requestUpdates()
            }
    }

    private fun requestUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMaxUpdateDelayMillis(UPDATE_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        fused.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun saveLocation(location: Location) {
        if (!::targetRef.isInitialized) return

        if (auth.currentUser == null) {
            if (!authBusy) {
                authBusy = true
                auth.signInAnonymously().addOnCompleteListener {
                    authBusy = false
                    if (it.isSuccessful) saveLocation(location)
                }
            }
            return
        }

        val code = getSharedPreferences("lokasi", MODE_PRIVATE)
            .getString("code", "")
            ?.trim()
            .orEmpty()

        val moving = isMoving(location)
        val timeText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        val data = mapOf(
            "nama" to code,
            "nomor" to code,
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "akurasi" to location.accuracy.toDouble(),
            "baterai" to readBatteryPercent(),
            "waktu" to timeText,
            "timestamp" to ServerValue.TIMESTAMP,
            "status" to "aktif",
            "gerak" to if (moving) "bergerak" else "parkir"
        )

        targetRef.updateChildren(data)
        previousLocation = Location(location)
    }

    private fun isMoving(current: Location): Boolean {
        if (current.hasSpeed()) return current.speed > 1.0f

        val prev = previousLocation ?: return false
        val seconds = max(1.0, (current.time - prev.time) / 1000.0)
        return prev.distanceTo(current) / seconds > 1.0
    }

    private fun readBatteryPercent(): Int {
        val battery = registerReceiver(
            null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return -1

        val level = battery.getIntExtra("level", -1)
        val scale = battery.getIntExtra("scale", 100)

        return if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            -1
        }
    }

    override fun onDestroy() {
        if (::fused.isInitialized) {
            fused.removeLocationUpdates(locationCallback)
        }
        if (::targetRef.isInitialized) {
            targetRef.updateChildren(mapOf("status" to "berhenti"))
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lokasi GPS",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun toFirebaseKey(value: String): String =
        value.replace(".", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("/", "_")

    companion object {
        private const val CHANNEL_ID = "lokasi_saya_gps"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 10_000L
        private const val MIN_UPDATE_INTERVAL_MS = 4_000L
    }
}
