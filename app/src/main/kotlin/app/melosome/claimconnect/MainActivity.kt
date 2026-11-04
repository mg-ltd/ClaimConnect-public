package app.melosome.claimconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.melosome.claimconnect.ui.AppViewModel
import app.melosome.claimconnect.ui.ClaimConnectApp
import app.melosome.claimconnect.ui.ClaimConnectTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    override fun onStart() { super.onStart(); appViewModel.sweepAiShares() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ClaimConnectTheme { ClaimConnectApp(appViewModel) } }
    }
}
