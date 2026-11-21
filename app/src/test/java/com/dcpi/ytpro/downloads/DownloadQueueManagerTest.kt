package com.dcpi.ytpro.downloads

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueManagerTest {
    private suspend fun awaitTerminal(manager: DownloadQueueManager, id: String) =
        withTimeout(3000L) { manager.tasks.first { list -> list.any { it.id == id && (it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED) } } }.first { it.id == id }

    @Test fun enqueueReturnsNonEmptyId() { val m = DownloadQueueManager {}; assertTrue(m.enqueue("A", "url", "a").isNotBlank()) }
    @Test fun successfulTaskCompletes(): Unit = runBlocking { val m = DownloadQueueManager {}; val id = m.enqueue("A", "url", "a"); assertEquals(DownloadStatus.COMPLETED, awaitTerminal(m, id).status) }
    @Test fun successfulTaskHasCompletionMessage(): Unit = runBlocking { val m = DownloadQueueManager {}; val id = m.enqueue("A", "url", "a"); assertEquals("Descarga completada", awaitTerminal(m, id).message) }
    @Test fun failingTaskIsMarkedFailed(): Unit = runBlocking { val m = DownloadQueueManager { error("network") }; val id = m.enqueue("A", "url", "a"); assertEquals(DownloadStatus.FAILED, awaitTerminal(m, id).status) }
    @Test fun failingTaskKeepsErrorMessage(): Unit = runBlocking { val m = DownloadQueueManager { error("network") }; val id = m.enqueue("A", "url", "a"); assertEquals("network", awaitTerminal(m, id).message) }
    @Test fun taskKeepsItsTitle(): Unit = runBlocking { val m = DownloadQueueManager {}; val id = m.enqueue("Song title", "url", "a"); assertEquals("Song title", awaitTerminal(m, id).title) }
    @Test fun taskKeepsItsSource(): Unit = runBlocking { val m = DownloadQueueManager {}; val id = m.enqueue("A", "source-url", "a"); assertEquals("source-url", awaitTerminal(m, id).source) }
    @Test fun taskKeepsContentId(): Unit = runBlocking { val m = DownloadQueueManager {}; val id = m.enqueue("A", "url", "video-123"); assertEquals("video-123", awaitTerminal(m, id).contentId) }
    @Test fun taskIdsAreUnique() { val m = DownloadQueueManager {}; assertTrue(m.enqueue("A", "u1", "a") != m.enqueue("B", "u2", "b")) }
    @Test fun multipleTasksAreRetained(): Unit = runBlocking { val m = DownloadQueueManager {}; val a = m.enqueue("A", "u1", "a"); val b = m.enqueue("B", "u2", "b"); val list = withTimeout(3000L) { m.tasks.first { it.any { t -> t.id == a && t.status == DownloadStatus.COMPLETED } && it.any { t -> t.id == b && t.status == DownloadStatus.COMPLETED } } }; assertEquals(2, list.size) }
    @Test fun removeFinishedClearsCompletedTasks(): Unit = runBlocking { val m = DownloadQueueManager {}; val id = m.enqueue("A", "url", "a"); awaitTerminal(m, id); m.removeFinished(); assertTrue(m.tasks.value.isEmpty()) }
    @Test fun removeFinishedKeepsQueuedTasks(): Unit = runBlocking { val gate = kotlinx.coroutines.CompletableDeferred<Unit>(); val m = DownloadQueueManager { gate.await() }; m.enqueue("A", "url", "a"); val second = m.enqueue("B", "url2", "b"); m.removeFinished(); assertTrue(m.tasks.value.any { it.id == second }); gate.complete(Unit); Unit }
    @Test fun queueProcessesInOrder(): Unit = runBlocking { val order = mutableListOf<String>(); val m = DownloadQueueManager { order.add(it.title) }; val a = m.enqueue("A", "u1", "a"); val b = m.enqueue("B", "u2", "b"); awaitTerminal(m, a); awaitTerminal(m, b); assertEquals(listOf("A", "B"), order) }
    @Test fun queueContinuesAfterFailure(): Unit = runBlocking { var count = 0; val m = DownloadQueueManager { if (it.title == "A") error("bad"); count++ }; val a = m.enqueue("A", "u1", "a"); val b = m.enqueue("B", "u2", "b"); awaitTerminal(m, a); assertEquals(DownloadStatus.COMPLETED, awaitTerminal(m, b).status); assertEquals(1, count) }
    @Test fun emptyQueueStartsEmpty() { val m = DownloadQueueManager {}; assertTrue(m.tasks.value.isEmpty()) }
    @Test fun removeFinishedLeavesActiveTask(): Unit = runBlocking { val gate = kotlinx.coroutines.CompletableDeferred<Unit>(); val m = DownloadQueueManager { gate.await() }; val id = m.enqueue("A", "url", "a"); withTimeout(3000L) { m.tasks.first { it.any { t -> t.id == id && t.status == DownloadStatus.RUNNING } } }; m.removeFinished(); assertTrue(m.tasks.value.any { it.id == id }); gate.complete(Unit) }
    @Test fun failedTaskCanBeRemoved(): Unit = runBlocking { val m = DownloadQueueManager { error("bad") }; val id = m.enqueue("A", "url", "a"); awaitTerminal(m, id); m.removeFinished(); assertTrue(m.tasks.value.none { it.id == id }) }
    @Test fun twoTasksHaveSeparateStatuses(): Unit = runBlocking { val m = DownloadQueueManager { if (it.title == "bad") error("bad") }; val a = m.enqueue("ok", "u1", "a"); val b = m.enqueue("bad", "u2", "b"); assertEquals(DownloadStatus.COMPLETED, awaitTerminal(m, a).status); assertEquals(DownloadStatus.FAILED, awaitTerminal(m, b).status) }
    @Test fun taskMessageIsNonEmptyAfterSuccess(): Unit = runBlocking { val m = DownloadQueueManager {}; val id = m.enqueue("A", "url", "a"); assertTrue(awaitTerminal(m, id).message.isNotBlank()) }
    @Test fun taskMessageIsNonEmptyAfterFailure(): Unit = runBlocking { val m = DownloadQueueManager { error("bad") }; val id = m.enqueue("A", "url", "a"); assertTrue(awaitTerminal(m, id).message.isNotBlank()) }
}
