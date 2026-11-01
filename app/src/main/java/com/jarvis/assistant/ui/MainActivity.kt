package com.jarvis.assistant.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.PermissionCatalog
import com.jarvis.assistant.core.UiRequest

class MainActivity : ComponentActivity() {
    private val vm: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                JarvisScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisScreen(vm: JarvisViewModel) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> vm.onPermissionResult(result) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // The grant still works for this session.
            }
            vm.onFolderPicked(uri.toString())
        } else {
            vm.onFolderPickCancelled()
        }
    }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    fun send() {
        val text = input
        input = ""
        vm.submit(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JARVIS") },
                actions = {
                    TextButton(onClick = { vm.showModules = true }) { Text("Modules") }
                    Text("Speak", style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = vm.speakReplies,
                        onCheckedChange = { vm.updateSpeakReplies(it) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a command") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                )
                Button(onClick = { vm.toggleVoice() }) { Text(if (vm.listening) "Stop" else "Mic") }
                Button(onClick = { send() }) { Text("Send") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.messages) { ChatBubble(it) }
        }
    }

    when (val d = vm.dialog) {
        is DialogState.Permission -> AlertDialog(
            onDismissRequest = { vm.dismissDialog("Okay, I won't ask for that permission.") },
            title = { Text("Permission needed") },
            text = { Text(PermissionCatalog.rationale(d.permissions)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.beginSystemPrompt()
                    permissionLauncher.launch(d.permissions.toTypedArray())
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissDialog("Okay, I won't ask for that permission.") }) { Text("Not now") }
            },
        )

        is DialogState.Confirm -> AlertDialog(
            onDismissRequest = { vm.dismissDialog("Cancelled. Nothing was done.") },
            title = { Text(d.action.title) },
            text = { Text(d.action.details) },
            confirmButton = { TextButton(onClick = { vm.confirm(d.action, d.viaVoice) }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { vm.dismissDialog("Cancelled. Nothing was done.") }) { Text("Cancel") } },
        )

        is DialogState.Ui -> AlertDialog(
            onDismissRequest = { vm.dismissDialog("Okay.") },
            title = { Text(if (d.request == UiRequest.PICK_FOLDER) "Choose a folder" else "Notification access") },
            text = { Text(d.message) },
            confirmButton = {
                TextButton(onClick = {
                    if (d.request == UiRequest.PICK_FOLDER) {
                        vm.beginSystemPrompt()
                        folderLauncher.launch(null)
                    } else {
                        vm.openNotificationAccessSettings()
                    }
                }) { Text(if (d.request == UiRequest.PICK_FOLDER) "Choose folder" else "Open settings") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissDialog("Okay.") }) { Text("Not now") } },
        )

        null -> {}
    }

    if (vm.showModules) {
        val statuses = remember { vm.moduleStatuses() }
        AlertDialog(
            onDismissRequest = { vm.showModules = false },
            title = { Text("Modules") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    statuses.forEach { (name, status) ->
                        Column {
                            Text((if (status.ready) "Ready: " else "Not ready: ") + name, style = MaterialTheme.typography.titleSmall)
                            Text(status.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.showModules = false }) { Text("Close") } },
        )
    }
}

@Composable
fun ChatBubble(line: ChatLine) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (line.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(line.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}
