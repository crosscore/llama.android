// llama.android/app/src/main/java/com/example/llama/MainActivity.kt
package com.example.llama

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.example.llama.ui.theme.LlamaAndroidTheme
import java.io.File

class MainActivity(
    activityManager: ActivityManager? = null,
    downloadManager: DownloadManager? = null,
    clipboardManager: ClipboardManager? = null,
): ComponentActivity() {
    private val activityManager by lazy { activityManager ?: getSystemService<ActivityManager>()!! }
    private val downloadManager by lazy { downloadManager ?: getSystemService<DownloadManager>()!! }
    private val clipboardManager by lazy { clipboardManager ?: getSystemService<ClipboardManager>()!! }

    private val viewModel: MainViewModel by viewModels()

    private var downloadId: Long = 0

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                viewModel.log("Download completed")
                val modelFile = File(getExternalFilesDir(null), MODEL_FILE_NAME)
                if (modelFile.exists()) {
                    viewModel.load(modelFile.absolutePath)
                } else {
                    viewModel.log("Downloaded file not found")
                }
            }
        }
    }

    private fun availableMemory(): ActivityManager.MemoryInfo {
        return ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager.getMemoryInfo(memoryInfo)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StrictMode.setVmPolicy(
            VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )

        registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        val free = Formatter.formatFileSize(this, availableMemory().availMem)
        val total = Formatter.formatFileSize(this, availableMemory().totalMem)

        viewModel.log("Current memory: $free / $total")
        viewModel.log("Downloads directory: ${getExternalFilesDir(null)}")

        val extFilesDir = getExternalFilesDir(null)
        val modelFile = File(extFilesDir, MODEL_FILE_NAME)

        if (!modelFile.exists()) {
            downloadModel()
        } else {
            viewModel.load(modelFile.absolutePath)
        }

        val models = listOf(
            Downloadable(
                MODEL_NAME,
                Uri.EMPTY,
                modelFile
            )
        )

        setContent {
            LlamaAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainCompose(
                        viewModel,
                        clipboardManager,
                        downloadManager,
                        models,
                    )
                }
            }
        }
    }

    private fun downloadModel() {
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Downloading Llama 3 Youko 8B Model")
            .setDescription("Downloading $MODEL_FILE_NAME")
            .setNotificationVisibility(DownloadManager.REQUEST_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, null, MODEL_FILE_NAME)

        downloadId = downloadManager.enqueue(request)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadReceiver)
    }

    companion object {
        const val MODEL_URL = "https://huggingface.co/QuantFactory/llama-3-youko-8b-GGUF/raw/main/llama-3-youko-8b.Q2_K.gguf"
        const val MODEL_FILE_NAME = "llama-3-youko-8b.Q2_K.gguf"
        const val MODEL_NAME = "Llama 3 Youko 8B Q2_K"
    }
}

@Composable
fun MainCompose(
    viewModel: MainViewModel,
    clipboard: ClipboardManager,
    dm: DownloadManager,
    models: List<Downloadable>
) {
    Column {
        val scrollState = rememberLazyListState()

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(state = scrollState) {
                items(viewModel.messages) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge.copy(color = LocalContentColor.current),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        OutlinedTextField(
            value = viewModel.message,
            onValueChange = { viewModel.updateMessage(it) },
            label = { Text("Message") },
        )
        Row {
            Button({ viewModel.send() }) { Text("Send") }
            Button({ viewModel.bench(8, 4, 1) }) { Text("Bench") }
            Button({ viewModel.clear() }) { Text("Clear") }
            Button({
                viewModel.messages.joinToString("\n").let {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", it))
                }
            }) { Text("Copy") }
        }

        Column {
            for (model in models) {
                Downloadable.Button(viewModel, dm, model)
            }
        }
    }
}
