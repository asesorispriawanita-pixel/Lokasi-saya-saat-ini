package com.lokasisaya.shared

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseConfig {
    const val DATABASE_URL =
        "https://meme-project-cab7f-default-rtdb.asia-southeast1.firebasedatabase.app/"
    const val PROJECT_ID = "meme-project-cab7f"
    const val API_KEY = "AIzaSyDpu6bCd6PLVVeJNyZMd0yLaxDKwz90ea8"
    const val STORAGE_BUCKET = "meme-project-cab7f.firebasestorage.app"

    /*
     * Menggunakan App ID Firebase yang sudah ada dari project sebelumnya.
     * Anonymous Auth + Realtime Database digunakan; analytics tidak diperlukan.
     */
    const val APPLICATION_ID = "1:602572669357:web:13dc5e693d079623292124"

    fun init(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(API_KEY)
                .setApplicationId(APPLICATION_ID)
                .setProjectId(PROJECT_ID)
                .setDatabaseUrl(DATABASE_URL)
                .setStorageBucket(STORAGE_BUCKET)
                .build()

            FirebaseApp.initializeApp(context, options)
        }
    }
}
