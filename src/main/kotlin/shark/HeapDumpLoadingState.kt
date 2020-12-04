package shark

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import shark.HprofHeapGraph.Companion.openHeapGraph
import java.io.File
import java.util.concurrent.ExecutorService

class HeapDumpLoadingState(val file: File, val ioExecutor: ExecutorService) {
  val loadedGraph: MutableState<CloseableHeapGraph?> = mutableStateOf(null)

  fun load() {
    if (loadedGraph.value != null) {
      return
    }
    ioExecutor.execute {
      loadedGraph.value = file.openHeapGraph()
    }
  }
}