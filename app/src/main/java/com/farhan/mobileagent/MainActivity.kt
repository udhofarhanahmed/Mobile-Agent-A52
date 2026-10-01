package com.farhan.mobileagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farhan.mobileagent.agent.security.SecurityKernel

class MainActivity : ComponentActivity() {
    private val kernel = SecurityKernel()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Mobile Agent A52", style = MaterialTheme.typography.headlineMedium)
                    Text(if (kernel.isStopped()) "EMERGENCY STOP ACTIVE" else "Protected / Ready")
                    Button(onClick = { kernel.emergencyStop() }) { Text("EMERGENCY STOP") }
                    Text("Local-first • no network permission • policy-gated actions")
                }
            }
        }
    }
}
