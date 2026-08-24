package io.github.haku4130.noscrollguard.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.haku4130.noscrollguard.GuardApp
import io.github.haku4130.noscrollguard.R
import io.github.haku4130.noscrollguard.pause.DEFAULT_PAUSE_MS
import io.github.haku4130.noscrollguard.service.GuardService
import io.github.haku4130.noscrollguard.settings.AndroidSecureSettings
import io.github.haku4130.noscrollguard.state.AccessibilityStateReader

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        GuardService.start(this)

        findViewById<Button>(R.id.shareButton).setOnClickListener {
            val text = GuardApp.eventLog(this).asText()
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    getString(R.string.share_log)
                )
            )
        }

        findViewById<Button>(R.id.pauseButton).setOnClickListener {
            val pause = GuardApp.pauseState(this)
            if (pause.isPaused()) pause.resume() else pause.pauseFor(DEFAULT_PAUSE_MS)
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val reader = AccessibilityStateReader(AndroidSecureSettings(contentResolver))
        val hasPermission = checkSelfPermission(
            android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        findViewById<TextView>(R.id.status).text = when {
            !hasPermission -> getString(R.string.status_no_permission)
            reader.isHealthy() -> getString(R.string.status_ok)
            else -> getString(R.string.status_broken)
        }

        val paused = GuardApp.pauseState(this).isPaused()
        findViewById<Button>(R.id.pauseButton).setText(
            if (paused) R.string.resume else R.string.pause
        )

        findViewById<TextView>(R.id.logView).text = GuardApp.eventLog(this).asText()
    }
}
