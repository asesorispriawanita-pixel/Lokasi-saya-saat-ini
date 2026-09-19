package com.lokasisaya.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.lokasisaya.shared.FirebaseConfig
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.CopyrightOverlay
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

class MainActivity : AppCompatActivity() {

    private lateinit var mapContainer: FrameLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var codeInput: EditText
    private lateinit var message: TextView

    private lateinit var osmMap: MapView

    private val targets = LinkedHashMap<String, TargetInfo>()
    private val markers = HashMap<String, Marker>()
    private val listeners = HashMap<String, ValueEventListener>()

    private val prefs by lazy {
        getSharedPreferences("admin_targets", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Konfigurasi OpenStreetMap
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        Configuration.getInstance().userAgentValue = packageName

        FirebaseConfig.init(this)

        buildScreen()

        setupOpenStreetMap()

        // Login Firebase anonim
        FirebaseAuth.getInstance()
            .signInAnonymously()
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {
                    message.text = "Firebase aktif. Memuat target..."

                    restoreSavedTargets()
                } else {
                    message.text =
                        "Firebase gagal login: ${task.exception?.message}"
                }
            }
    }

    private fun buildScreen() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // =========================
        // HEADER
        // =========================

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 20, 18, 8)
        }

        val title = TextView(this).apply {
            text = "🖥️ ADMIN"
            textSize = 28f
        }

        val subtitle = TextView(this).apply {
            text = "Pantau lokasi TARGET secara realtime"
            textSize = 14f
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        codeInput = EditText(this).apply {
            hint = "Nomor / kode target"
            setSingleLine(true)
        }

        val addButton = Button(this).apply {
            text = "TAMBAH"
            setOnClickListener {
                addTargetFromInput()
            }
        }

        row.addView(
            codeInput,
            LinearLayout.LayoutParams(
                0,
                60,
                1f
            )
        )

        row.addView(
            addButton,
            LinearLayout.LayoutParams(
                -2,
                60
            )
        )

        message = TextView(this).apply {
            text = "Menyiapkan Firebase..."
            textSize = 14f
            setPadding(0, 6, 0, 4)
        }

        val clearButton = Button(this).apply {
            text = "HAPUS SEMUA TARGET"
            setOnClickListener {
                clearAllTargets()
            }
        }

        header.addView(title)
        header.addView(subtitle)
        header.addView(row)
        header.addView(message)
        header.addView(clearButton)

        root.addView(header)

        // =========================
        // MAP
        // =========================

        mapContainer = FrameLayout(this).apply {
            id = View.generateViewId()
        }

        root.addView(
            mapContainer,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        // =========================
        // LIST TARGET
        // =========================

        val scroll = ScrollView(this)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 8, 14, 14)
        }

        scroll.addView(listContainer)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                260
            )
        )

        setContentView(root)
    }

    // =========================================================
    // OPENSTREETMAP
    // =========================================================

    private fun setupOpenStreetMap() {

        osmMap = MapView(this)

        osmMap.setTileSource(
            TileSourceFactory.MAPNIK
        )

        osmMap.setMultiTouchControls(true)

        osmMap.setUseDataConnection(true)

        // Copyright / atribusi OpenStreetMap
        osmMap.overlays.add(
            CopyrightOverlay(this)
        )

        mapContainer.addView(
            osmMap,
            FrameLayout.LayoutParams(
                -1,
                -1
            )
        )

        // Posisi awal Indonesia
        val indonesia = GeoPoint(
            -2.5489,
            118.0149
        )

        osmMap.controller.setCenter(indonesia)
        osmMap.controller.setZoom(4.5)

        osmMap.invalidate()
    }

    // =========================================================
    // TAMBAH TARGET
    // =========================================================

    private fun addTargetFromInput() {

        val raw = codeInput.text
            .toString()
            .trim()

        if (raw.isBlank()) {

            codeInput.error =
                "Masukkan kode target"

            return
        }

        val code = toFirebaseKey(raw)

        if (listeners.containsKey(code)) {

            message.text =
                "Target $raw sudah ada."

            return
        }

        saveCode(code)

        attachTargetListener(
            code,
            raw
        )

        codeInput.text.clear()

        message.text =
            "Target $raw ditambahkan."
    }

    // =========================================================
    // FIREBASE TARGET
    // =========================================================

    private fun attachTargetListener(
        code: String,
        displayCode: String
    ) {

        val ref =
            FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(code)
                .child("target")

        val listener =
            object : ValueEventListener {

                override fun onDataChange(
                    snapshot: DataSnapshot
                ) {

                    if (!snapshot.exists()) {

                        message.text =
                            "Menunggu data dari $displayCode..."

                        return
                    }

                    val info =
                        targets[code]
                            ?: TargetInfo(displayCode)

                    info.lat =
                        snapshot.child("latitude")
                            .getValue(Double::class.java)
                            ?: 0.0

                    info.lng =
                        snapshot.child("longitude")
                            .getValue(Double::class.java)
                            ?: 0.0

                    info.accuracy =
                        snapshot.child("akurasi")
                            .getValue(Double::class.java)
                            ?: 0.0

                    info.battery =
                        snapshot.child("baterai")
                            .getValue(Long::class.java)
                            ?: -1

                    info.movement =
                        snapshot.child("gerak")
                            .getValue(String::class.java)
                            ?: "-"

                    info.status =
                        snapshot.child("status")
                            .getValue(String::class.java)
                            ?: "-"

                    info.time =
                        snapshot.child("waktu")
                            .getValue(String::class.java)
                            ?: "-"

                    info.timestamp =
                        snapshot.child("timestamp")
                            .getValue(Long::class.java)
                            ?: 0L

                    targets[code] = info

                    redraw()
                }

                override fun onCancelled(
                    error: DatabaseError
                ) {

                    message.text =
                        "Firebase: ${error.message}"
                }
            }

        listeners[code] = listener

        ref.addValueEventListener(listener)
    }

    // =========================================================
    // RESTORE TARGET
    // =========================================================

    private fun restoreSavedTargets() {

        val saved =
            prefs.getStringSet(
                "codes",
                emptySet()
            ) ?: emptySet()

        saved.forEach { code ->

            attachTargetListener(
                code,
                code
            )
        }

        if (saved.isNotEmpty()) {

            message.text =
                "${saved.size} target dimuat."
        } else {

            message.text =
                "Tambahkan satu atau beberapa kode target."
        }
    }

    // =========================================================
    // SIMPAN KODE
    // =========================================================

    private fun saveCode(
        code: String
    ) {

        val existing =
            prefs.getStringSet(
                "codes",
                emptySet()
            )
                ?.toMutableSet()
                ?: mutableSetOf()

        existing.add(code)

        prefs.edit()
            .putStringSet(
                "codes",
                existing
            )
            .apply()
    }

    // =========================================================
    // HAPUS SEMUA TARGET
    // =========================================================

    private fun clearAllTargets() {

        val root =
            FirebaseDatabase.getInstance()
                .getReference("rooms")

        listeners.forEach {
                (code, listener) ->

            root.child(code)
                .child("target")
                .removeEventListener(listener)
        }

        listeners.clear()

        targets.clear()

        markers.values.forEach {
            it.remove()
        }

        markers.clear()

        prefs.edit()
            .clear()
            .apply()

        redraw()

        message.text =
            "Semua target dihapus dari daftar Admin."
    }

    // =========================================================
    // TAMPILKAN TARGET
    // =========================================================

    private fun redraw() {

        listContainer.removeAllViews()

        for ((_, info) in targets) {

            val card =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    setPadding(
                        14,
                        12,
                        14,
                        12
                    }
                }

            val title =
                TextView(this).apply {

                    text =
                        "🎯 ${info.code}"

                    textSize = 19f
                }

            val details =
                TextView(this).apply {

                    text =
                        buildDetails(info)

                    textSize = 14f
                }

            val openButton =
                Button(this).apply {

                    text =
                        "🗺️ BUKA DI GOOGLE MAPS"

                    setOnClickListener {

                        openInGoogleMaps(info)
                    }
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

        osmMap.invalidate()
    }

    // =========================================================
    // DETAIL TARGET
    // =========================================================

    private fun buildDetails(
        info: TargetInfo
    ): String {

        val lat =
            String.format(
                Locale.US,
                "%.6f",
                info.lat
            )

        val lng =
            String.format(
                Locale.US,
                "%.6f",
                info.lng
            )

        val accuracy =
            String.format(
                Locale.US,
                "%.1f m",
                info.accuracy
            )

        val age =
            if (info.timestamp > 0L) {

                val seconds =
                    (
                        System.currentTimeMillis()
                            - info.timestamp
                        ) / 1000L

                "${seconds.coerceAtLeast(0L)} detik lalu"

            } else {

                "-"
            }

        return "📍 $lat, $lng\n" +
            "🎯 Akurasi GPS: $accuracy\n" +
            "🔋 Baterai: " +
            if (info.battery >= 0) {
                "${info.battery}%"
            } else {
                "-"
            } +
            "\n" +
            "🚗 Status: ${info.movement}\n" +
            "🟢 Koneksi: ${info.status}\n" +
            "⏱️ Update terakhir: ${info.time} ($age)"
    }

    // =========================================================
    // PINDAH KE TARGET
    // =========================================================

    private fun centerOnTarget(
        info: TargetInfo
    ) {

        if (
            info.lat == 0.0 &&
            info.lng == 0.0
        ) {
            return
        }

        val point =
            GeoPoint(
                info.lat,
                info.lng
            )

        osmMap.controller.setZoom(17.0)

        osmMap.controller.animateTo(
            point
        )
    }

    // =========================================================
    // BUKA GOOGLE MAPS / WEB MAPS
    // =========================================================

    private fun openInGoogleMaps(
        info: TargetInfo
    ) {

        if (
            info.lat == 0.0 &&
            info.lng == 0.0
        ) {
            return
        }

        val url =
            "https://www.google.com/maps/search/?api=1" +
            "&query=${info.lat},${info.lng}"

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )

        startActivity(intent)
    }

    // =========================================================
    // UPDATE PIN OPENSTREETMAP
    // =========================================================

    private fun updateMarker(
        info: TargetInfo
    ) {

        if (
            info.lat == 0.0 &&
            info.lng == 0.0
        ) {
            return
        }

        val position =
            GeoPoint(
                info.lat,
                info.lng
            )

        var marker =
            markers[info.code]

        if (marker == null) {

            marker =
                Marker(osmMap)

            marker.position =
                position

            marker.title =
                "🎯 ${info.code}"

            marker.snippet =
                "Baterai ${info.battery}% • " +
                "${info.movement} • " +
                "Akurasi ${info.accuracy}m"

            marker.setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )

            osmMap.overlays.add(
                marker
            )

            markers[info.code] =
                marker

        } else {

            marker.position =
                position

            marker.snippet =
                "Baterai ${info.battery}% • " +
                "${info.movement} • " +
                "Akurasi ${info.accuracy}m"
        }
    }

    // =========================================================
    // FIREBASE KEY
    // =========================================================

    private fun toFirebaseKey(
        value: String
    ): String {

        return value
            .replace(".", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("/", "_")
    }

    // =========================================================
    // LIFECYCLE OPENSTREETMAP
    // =========================================================

    override fun onResume() {

        super.onResume()

        if (::osmMap.isInitialized) {
            osmMap.onResume()
        }
    }

    override fun onPause() {

        if (::osmMap.isInitialized) {
            osmMap.onPause()
        }

        super.onPause()
    }

    override fun onDestroy() {

        listeners.clear()

        if (::osmMap.isInitialized) {
            osmMap.onDetach()
        }

        super.onDestroy()
    }
}
