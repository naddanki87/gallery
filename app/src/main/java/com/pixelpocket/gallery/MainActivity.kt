package com.pixelpocket.gallery

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixelpocket.gallery.ui.CropScreen
import com.pixelpocket.gallery.ui.GalleryScreen
import com.pixelpocket.gallery.ui.GalleryViewModel
import com.pixelpocket.gallery.ui.PermissionScreen
import com.pixelpocket.gallery.ui.RegistrationScreen
import com.pixelpocket.gallery.ui.Screen
import com.pixelpocket.gallery.ui.SettingsScreen
import com.pixelpocket.gallery.ui.ViewerScreen
import com.pixelpocket.gallery.ui.theme.PixelPocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPocketTheme {
                PixelPocketRoot()
            }
        }
    }
}

@Composable
private fun PixelPocketRoot(vm: GalleryViewModel = viewModel()) {
    // Shared launcher for the system "allow PixelPocket to delete/modify" dialogs.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onIntentResult(it.resultCode == Activity.RESULT_OK)
    }
    val request = vm.intentRequest
    LaunchedEffect(request) {
        if (request != null) {
            vm.onIntentLaunched()
            launcher.launch(IntentSenderRequest.Builder(request).build())
        }
    }

    when (val screen = vm.screen) {
        Screen.Register -> RegistrationScreen(vm)
        Screen.Permission -> PermissionScreen(vm)
        Screen.Gallery -> GalleryScreen(vm)
        is Screen.Viewer -> ViewerScreen(vm, screen.startKey)
        is Screen.Crop -> CropScreen(vm, screen.item, screen.returnKey)
        Screen.Settings -> SettingsScreen(vm)
    }
}
