package com.farhan.mobileagent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class CallActivity : ComponentActivity() {
    private var targetName by mutableStateOf("")
    private var phoneNumber by mutableStateOf<String?>(null)
    private var status by mutableStateOf("Choose a contact to continue.")

    private val contactPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri == null) {
            status = "No contact selected."
            return@registerForActivityResult
        }

        val selected = contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0).orEmpty()
                val number = cursor.getString(1).orEmpty()
                name to number
            } else {
                null
            }
        }

        if (selected == null || selected.second.isBlank()) {
            status = "The selected contact has no usable phone number."
            phoneNumber = null
        } else {
            targetName = selected.first.ifBlank { "Selected contact" }
            phoneNumber = selected.second
            status = "Ready to open the Phone app."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialTarget = intent.getStringExtra(EXTRA_TARGET).orEmpty().trim()
        targetName = initialTarget

        if (isLikelyPhoneNumber(initialTarget)) {
            phoneNumber = initialTarget
            status = "Ready to open the Phone app."
        } else if (initialTarget.isNotBlank()) {
            status = "Select the contact matching “$initialTarget”."
            openContactPicker()
        }

        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Call", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "The agent will only prepare the call. Android’s Phone app keeps the final Call button under your control.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                targetName.ifBlank { "No target selected" },
                                style = MaterialTheme.typography.titleLarge
                            )
                            phoneNumber?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (phoneNumber == null) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openContactPicker() }
                        ) {
                            Text("Choose contact")
                        }
                    } else {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openDialer(phoneNumber.orEmpty()) }
                        ) {
                            Text("Open Phone app")
                        }

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openContactPicker() }
                        ) {
                            Text("Choose a different contact")
                        }
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { finish() }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    private fun openContactPicker() {
        val intent = Intent(
            Intent.ACTION_PICK,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        )
        contactPicker.launch(intent)
    }

    private fun openDialer(number: String) {
        val intent = Intent(
            Intent.ACTION_DIAL,
            Uri.fromParts("tel", number, null)
        )
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            status = "Phone app opened. Review the number and press Call there."
        } else {
            status = "No Phone app is available to handle this number."
        }
    }

    private fun isLikelyPhoneNumber(value: String): Boolean {
        val normalized = value.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val digitCount = normalized.count(Char::isDigit)
        return digitCount >= 7 &&
            (normalized.firstOrNull()?.let { it.isDigit() || it == '+' || it == '*' || it == '#' } == true)
    }

    companion object {
        const val EXTRA_TARGET = "call_target"
    }
}
