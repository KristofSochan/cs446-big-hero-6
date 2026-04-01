package ca.uwaterloo.cs446.bighero6.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteNfcScreen(
    stationId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var writeStatus by remember { mutableStateOf("Ready to write to NFC tag") }
    var isWriting by remember { mutableStateOf(false) }
    var writeSuccess by remember { mutableStateOf(false) }

    val uriToWrite = "taplist://station/$stationId"

    DisposableEffect(nfcAdapter, activity) {
        if (nfcAdapter == null) {
            writeStatus = "NFC not supported on this device"
            return@DisposableEffect onDispose {}
        }

        if (!nfcAdapter.isEnabled) {
            writeStatus = "NFC is disabled. Please enable it in settings."
        }

        if (activity == null) return@DisposableEffect onDispose {}

        val readerCallback = NfcAdapter.ReaderCallback { tag ->
            activity.runOnUiThread {
                isWriting = true
                writeStatus = "Tag detected, writing..."
            }

            val success = writeUriToTag(tag, uriToWrite)

            activity.runOnUiThread {
                isWriting = false
                if (success) {
                    writeSuccess = true
                    writeStatus = "Successfully wrote station link"
                    Toast.makeText(context, "Write successful!", Toast.LENGTH_SHORT).show()
                } else {
                    writeStatus = "Failed to write. Ensure tag is NDEF-compatible and not locked."
                    Toast.makeText(context, "Write failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        nfcAdapter.enableReaderMode(
            activity,
            readerCallback,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )

        onDispose {
            nfcAdapter.disableReaderMode(activity)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Write to NFC") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = writeStatus,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (writeSuccess) 
                    "You can now tap this tag to join the waitlist!" 
                    else "Hold the back of your phone against an NFC tag to write the station link.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Indicator disappears if writeSuccess is true
            if (!writeSuccess && (isWriting || (nfcAdapter != null && nfcAdapter.isEnabled))) {
                CircularProgressIndicator()
            }
            
            if (writeSuccess) {
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

private fun writeUriToTag(tag: Tag, uriString: String): Boolean {
    val ndef = Ndef.get(tag) ?: return false
    
    return try {
        ndef.connect()
        if (!ndef.isWritable) return false
        
        // Creating a standard NDEF URI Record
        val record = NdefRecord.createUri(uriString)
        val ndefMessage = NdefMessage(record)
        
        if (ndef.maxSize < ndefMessage.toByteArray().size) return false
        
        ndef.writeNdefMessage(ndefMessage)
        true
    } catch (e: Exception) {
        false
    } finally {
        try { ndef.close() } catch (e: Exception) {}
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
