package com.lokasisaya.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.lokasisaya.shared.FirebaseConfig
import java.util.Locale

data class TargetInfo(
    val code: String,
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var accuracy: Double = 0.0,
    var battery: Long = -1,
    var movement: String = "-",
    var status: String = "-",
    var time: String = "-",
    var timestamp: Long = 0L
)

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapContainer: FrameLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var codeInput: EditText
    private lateinit var message: TextView

    private var googleMap: GoogleMap? = null
    private val targets = LinkedHashMap<String, TargetInfo>()
    private val markers = HashMap<String, Marker>()
    private val listeners = HashMap<String, ValueEventListener>()

    private val prefs by lazy {
        getSharedPreferences("admin_targets", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseConfig.init(this)
        FirebaseAuth.getInstance().signInAnonymously()

        buildScreen()

        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(mapContainer.id, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)

        restoreSavedTargets()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 20, 18, 8)
        }

        val title = TextView(this).apply {
            text = "🖥️ ADMIN"
            textSize = 28f
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        codeInput = EditText(this).apply {
            hint = "Nomor / kode target"
            singleLine = true
        }

        val addButton = Button(this).apply {
            text = "TAMBAH"
            setOnClickListener { addTargetFromInput() }
        }

        row.addView(codeInput, LinearLayout.LayoutParams(0, 60, 1f))
        row.addView(addButton, LinearLayout.LayoutParams(-2, 60))

        message = TextView(this).apply {
            text = "Tambahkan satu atau beberapa kode target."
            textSize = 14f
            setPadding(0, 6, 0, 4)
        }

        val clearButton = Button(this).apply {
            text = "HAPUS SEMUA TARGET"
            setOnClickListener { clearAllTargets() }
        }

        header.addView(title)
        header.addView(row)
        header.addView(message)
        header.addView(clearButton)

        root.addView(header)

        mapContainer = FrameLayout(this).apply {
            id = View.generateViewId()
        }
        root.addView(
            mapContainer,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val scroll = ScrollView(this)
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 8, 14, 14)
        }
        scroll.addView(listContainer)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 260)
        )

        setContentView(root)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMapToolbarEnabled = true
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(-2.5489, 118.0149),
                4.5f
            )
        )
        redraw()
    }

    private fun addTargetFromInput() {
        val raw = codeInput.text.toString().trim()
        if (raw.isBlank()) {
            codeInput.error = "Masukkan kode target"
            return
        }

        val code = toFirebaseKey(raw)

        if (listeners.containsKey(code)) {
            message.text = "Target $raw sudah ada."
            return
        }

        saveCode(code)
        attachTargetListener(code, raw)
        codeInput.text.clear()
        message.text = "Target $raw ditambahkan."
    }

    private fun attachTargetListener(code: String, displayCode: String) {
        val ref = FirebaseDatabase.getInstance()
            .getReference("rooms")
            .child(code)
            .child("target")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    message.text = "Menunggu data dari $displayCode..."
                    return
                }

                val info = targets[code] ?: TargetInfo(displayCode)
                info.lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                info.lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                info.accuracy = snapshot.child("akurasi").getValue(Double::class.java) ?: 0.0
                info.battery = snapshot.child("baterai").getValue(Long::class.java) ?: -1
                info.movement = snapshot.child("gerak").getValue(String::class.java) ?: "-"
                info.status = snapshot.child("status").getValue(String::class.java) ?: "-"
                info.time = snapshot.child("waktu").getValue(String::class.java) ?: "-"
                info.timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                targets[code] = info
                redraw()
            }

            override fun onCancelled(error: DatabaseError) {
                message.text = "Firebase: ${error.message}"
            }
        }

        listeners[code] = listener
        ref.addValueEventListener(listener)
    }

    private fun restoreSavedTargets() {
        val saved = prefs.getStringSet("codes", emptySet()) ?: emptySet()
        saved.forEach { code ->
            attachTargetListener(code, code)
        }

        if (saved.isNotEmpty()) {
            message.text = "${saved.size} target dimuat."
        }
    }

    private fun saveCode(code: String) {
        val existing = prefs.getStringSet("codes", emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()
        existing.add(code)
        prefs.edit().putStringSet("codes", existing).apply()
    }

    private fun clearAllTargets() {
        val root = FirebaseDatabase.getInstance().getReference("rooms")
        listeners.forEach { (code, listener) ->
            root.child(code).child("target").removeEventListener(listener)
        }
        listeners.clear()
        targets.clear()

        markers.values.forEach { it.remove() }
        markers.clear()

        prefs.edit().clear().apply()
        redraw()
        message.text = "Semua target dihapus dari daftar Admin."
    }

    private fun redraw() {
        listContainer.removeAllViews()

        for ((_, info) in targets) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 12, 14, 12)
            }

            val title = TextView(this).apply {
                text = "🎯 ${info.code}"
                textSize = 19f
            }

            val details = TextView(this).apply {
                text = buildDetails(info)
                textSize = 14f
            }

            val openButton = Button(this).apply {
                text = "🗺️ BUKA DI GOOGLE MAPS"
                setOnClickListener { openInGoogleMaps(info) }
            }

            card.addView(title)
            card.addView(details)
            card.addView(openButton)
            card.setOnClickListener {
                centerOnTarget(info)
            }

            listContainer.addView(card)
            updateMarker(info)
        }
    }

    private fun buildDetails(info: TargetInfo): String {
        val lat = String.format(Locale.US, "%.6f", info.lat)
        val lng = String.format(Locale.US, "%.6f", info.lng)
        val accuracy = String.format(Locale.US, "%.1f m", info.accuracy)
        val age = if (info.timestamp > 0L) {
            val seconds = ((System.currentTimeMillis() - info.timestamp) / 1000L)
                .coerceAtLeast(0L)
            "$seconds detik lalu"
        } else {
            "-"
        }

        return "📍 $lat, $lng\n" +
            "🎯 Akurasi GPS: $accuracy\n" +
            "🔋 Baterai: ${if (info.battery >= 0) "${info.battery}%" else "-"}\n" +
            "🚗 Status: ${info.movement}\n" +
            "🟢 Koneksi: ${info.status}\n" +
            "⏱️ Update terakhir: ${info.time} ($age)"
    }

    private fun centerOnTarget(info: TargetInfo) {
        if (info.lat == 0.0 && info.lng == 0.0) return

        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(info.lat, info.lng),
                17f
            )
        )
    }

    private fun openInGoogleMaps(info: TargetInfo) {
        if (info.lat == 0.0 && info.lng == 0.0) return

        val uri = Uri.parse(
            "geo:${info.lat},${info.lng}?q=${info.lat},${info.lng}(${Uri.encode(info.code)})"
        )
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun updateMarker(info: TargetInfo) {
        val map = googleMap ?: return
        if (info.lat == 0.0 && info.lng == 0.0) return

        val position = LatLng(info.lat, info.lng)
        val marker = markers[info.code]

        if (marker == null) {
            markers[info.code] = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(info.code)
                    .snippet(
                        "Baterai ${info.battery}% • ${info.movement} • Akurasi ${info.accuracy}m"
                    )
            )!!
        } else {
            marker.position = position
            marker.snippet =
                "Baterai ${info.battery}% • ${info.movement} • Akurasi ${info.accuracy}m"
        }
    }

    private fun toFirebaseKey(value: String): String =
        value.replace(".", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("/", "_")
}
