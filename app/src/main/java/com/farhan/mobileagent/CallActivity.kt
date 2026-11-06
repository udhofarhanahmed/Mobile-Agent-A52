package com.farhan.mobileagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private data class ResolvedPhone(val name: String, val number: String)

class CallActivity : ComponentActivity() {
    private var targetName by mutableStateOf("")
    private var phoneNumber by mutableStateOf<String?>(null)
    private var matches by mutableStateOf<List<ResolvedPhone>>(emptyList())
    private var status by mutableStateOf("Preparing a protected call.")

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) resolveTarget(true)
        else status = "Contacts access was denied. You can still use a phone number."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetName = intent.getStringExtra(EXTRA_TARGET).orEmpty().trim()

        if (isLikelyPhoneNumber(targetName)) {
            phoneNumber = targetName
        } else if (targetName.isNotBlank()) {
            if (hasContactsPermission()) resolveTarget(true)
            else status = "Allow Contacts so I can resolve “" + targetName + "” without opening the Contacts app."
        } else {
            status = "Enter a contact name or number through the agent."
        }

        setContent {
            MobileAgentTheme(AgentSettings.themeMode(this@CallActivity)) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Protected call", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "I resolve the number locally, then open the Phone app. You still press the final Call button.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(targetName.ifBlank { "No target" }, style = MaterialTheme.typography.titleLarge)
                            phoneNumber?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                            Text(status, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (targetName.isNotBlank() && phoneNumber == null && !hasContactsPermission()) {
                        Button(
                            onClick = {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        ) { Text("Allow contacts access") }
                    }
                    if (matches.isNotEmpty()) {
                        Text("Choose a matching number", style = MaterialTheme.typography.titleMedium)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(matches) { match ->
                                Card(
                                    onClick = {
                                        targetName = match.name
                                        phoneNumber = match.number
                                        matches = emptyList()
                                        openDialer(match.number)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(match.name)
                                        Text(match.number)
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (phoneNumber != null) {
                        Button(
                            onClick = { openDialer(phoneNumber.orEmpty()) }
                        ) { Text("Open Phone with number ready") }
                    }
                    OutlinedButton(onClick = { finish() }) {
                        Text("Close")
                    }
                }
            }
        }
    }

    private fun hasContactsPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < 23 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun resolveTarget(autoLaunch: Boolean) {
        if (!hasContactsPermission() || targetName.isBlank()) return
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI
            .buildUpon().appendPath(Uri.encode(targetName)).build()
        val found = mutableListOf<ResolvedPhone>()
        contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            while (cursor.moveToNext() && found.size < 8) {
                val name = cursor.getString(0).orEmpty()
                val number = cursor.getString(1).orEmpty()
                if (number.isNotBlank() && found.none { it.number == number }) {
                    found += ResolvedPhone(name.ifBlank { targetName }, number)
                }
            }
        }
        matches = found
        when {
            found.isEmpty() -> status = "No phone number matched “" + targetName + "”."
            found.size == 1 -> {
                targetName = found.first().name
                phoneNumber = found.first().number
                status = "Matched locally. Phone app is ready."
                if (autoLaunch) openDialer(phoneNumber!!)
            }
            else -> status = "I found " + found.size + " matching phone numbers."
        }
    }

    private fun openDialer(number: String) {
        val normalized = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", normalized, null))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            status = "Phone app opened. Review the number and press Call."
        } else {
            status = "No Phone app is available."
        }
    }

    private fun isLikelyPhoneNumber(value: String): Boolean =
        value.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }.count(Char::isDigit) >= 7

    companion object {
        const val EXTRA_TARGET = "call_target"
    }
}
