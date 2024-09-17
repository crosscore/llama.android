// llama.android/app/src/main/java/com/example/llama/Downloadable.kt
package com.example.llama

import android.app.DownloadManager
import android.net.Uri
import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File

data class Downloadable(val name: String, val source: Uri, val destination: File) {
    companion object {
        @JvmStatic
        private val tag: String? = this::class.qualifiedName

        sealed interface State
        data object Ready : State
        data class Downloaded(val downloadable: Downloadable) : State
        data class Error(val message: String) : State

        @JvmStatic
        @Composable
        fun Button(viewModel: MainViewModel, dm: DownloadManager, item: Downloadable) {
            var status: State by remember {
                mutableStateOf(
                    if (item.destination.exists()) Downloaded(item)
                    else Ready
                )
            }

            fun onClick() {
                when (status) {
                    is Downloaded -> {
                        viewModel.load(item.destination.path)
                    }
                    is Ready -> {
                        if (item.destination.exists()) {
                            status = Downloaded(item)
                            viewModel.load(item.destination.path)
                        } else {
                            val errorMessage = "Model file not found: ${item.destination.path}"
                            Log.e(tag, errorMessage)
                            viewModel.log(errorMessage)
                            status = Error(errorMessage)
                        }
                    }
                    is Error -> {
                        // エラー状態の場合、再試行を許可
                        if (item.destination.exists()) {
                            status = Downloaded(item)
                            viewModel.load(item.destination.path)
                        } else {
                            val errorMessage = "Model file still not found: ${item.destination.path}"
                            Log.e(tag, errorMessage)
                            viewModel.log(errorMessage)
                            status = Error(errorMessage)
                        }
                    }
                }
            }

            Button(onClick = { onClick() }, enabled = true) {
                when (status) {
                    is Downloaded -> Text("Load ${item.name}")
                    is Ready -> Text("Load ${item.name}")
                    is Error -> Text("Retry Loading ${item.name}")
                }
            }
        }
    }
}
