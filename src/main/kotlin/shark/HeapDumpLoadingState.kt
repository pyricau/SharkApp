package shark

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.util.concurrent.ExecutorService

class HeapDumpLoadingState(val file: File, val ioExecutor: ExecutorService) {
  val loadedGraph: MutableState<LoadedGraph?> = mutableStateOf(null)

  fun load() {
    if (loadedGraph.value != null) {
      return
    }
    println("Opening ${file.path}")
    ioExecutor.execute {
      loadedGraph.value = LoadedGraph.load(file)
    }
  }
}