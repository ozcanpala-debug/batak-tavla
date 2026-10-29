package com.ensar.oyun

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.ensar.oyun.lobby.Game
import com.ensar.oyun.ui.BatakScreen
import com.ensar.oyun.ui.GameScreen
import com.ensar.oyun.ui.HomeScreen
import com.ensar.oyun.ui.JoinScreen
import com.ensar.oyun.ui.LobbyScreen
import com.ensar.oyun.ui.TavlaScreen
import com.ensar.oyun.ui.XoxScreen

class MainActivity : ComponentActivity() {

    private val vm: SessionViewModel by viewModels()
    private var pending: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                pending?.invoke()
            } else {
                Toast.makeText(this, "İzinler verilmeden telefonlar birbirini bulamaz.", Toast.LENGTH_LONG).show()
            }
            pending = null
        }

    private fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        )
        Build.VERSION.SDK_INT >= 31 -> arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun withPermissions(action: () -> Unit) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pending = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        BackHandler(enabled = vm.screen != Screen.HOME) { vm.leave() }
                        when (vm.screen) {
                            Screen.HOME -> HomeScreen(
                                vm,
                                onHost = { withPermissions { vm.hostRoom() } },
                                onJoin = { withPermissions { vm.searchRooms() } },
                            )
                            Screen.JOIN -> JoinScreen(vm)
                            Screen.LOBBY -> LobbyScreen(vm)
                            Screen.GAME -> when (vm.game) {
                                Game.XOX -> XoxScreen(vm)
                                Game.TAVLA -> TavlaScreen(vm)
                                Game.BATAK -> BatakScreen(vm)
                            }
                        }
                    }
                }
            }
        }
    }
}
