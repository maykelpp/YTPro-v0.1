package com.dcpi.ytpro.downloads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val source: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val message: String = ""
)

class DownloadQueueManager(
    private val downloader: suspend (DownloadTask) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<DownloadTask>(Channel.UNLIMITED)
    private val mutableTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = mutableTasks.asStateFlow()

    init {
        scope.launch {
            for (task in queue) {
                update(task.id) { it.copy(status = DownloadStatus.RUNNING, message = "Descargando…") }
                try {
                    downloader(task)
                    update(task.id) { it.copy(status = DownloadStatus.COMPLETED, message = "Descarga completada") }
                } catch (error: Exception) {
                    update(task.id) { it.copy(status = DownloadStatus.FAILED, message = error.message ?: "Error de descarga") }
                }
            }
        }
    }

    fun enqueue(title: String, source: String): String {
        val task = DownloadTask(title = title, source = source)
        mutableTasks.value = mutableTasks.value + task
        queue.trySend(task)
        return task.id
    }

    fun removeFinished() {
        mutableTasks.value = mutableTasks.value.filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING
        }
    }

    private fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        mutableTasks.value = mutableTasks.value.map { if (it.id == id) transform(it) else it }
    }
}
