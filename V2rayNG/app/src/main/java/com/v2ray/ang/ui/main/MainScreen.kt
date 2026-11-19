package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.ui.compose.QRCodeDialog

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    if (shareQRCodeBitmap != null) {
        QRCodeDialog(
            bitmap = shareQRCodeBitmap,
            onDismiss = { onAction(MainAction.DismissQRCodeDialog) }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0D0E15),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
    ) { innerPadding ->
        MinimalVpnHome(
            mainViewModel = mainViewModel,
            onAction = onAction,
            onNavigate = onNavigate,
            contentPadding = innerPadding,
        )
    }
}
