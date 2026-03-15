package com.gihansgamage.aksharakb

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Example: Button to set background image
        val btnSetImage = findViewById<Button>(R.id.btn_set_bg)
        btnSetImage.setOnClickListener {
            // Open Gallery to pick image
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 100)
        }

        // Toggle for Border
        // Toggle for Layout (Wijesekara vs QWERTY - for Sinhala)
        // Save these to SharedPreferences
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val imageUri = data?.data
            // Save this URI to SharedPreferences
            // The Service will read this and call setKeyboardImage()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}