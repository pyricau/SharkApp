package shark

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import shark.HprofHeapGraph.Companion.openHeapGraph
import java.io.File
import java.util.concurrent.ExecutorService

class HeapDumpLoadingState(val file: File, val ioExecutor: ExecutorService) {
  val loadedGraph: MutableState<CloseableHeapGraph?> = mutableStateOf(null)

  fun load() {
    if (loadedGraph.value != null) {
      return
    }
    println("Opening ${file.path}")
    ioExecutor.execute {
      val fileSourceProvider = FileSourceProvider(file)
      val wrapper = object : DualSourceProvider by fileSourceProvider{
        override fun openRandomAccessSource(): RandomAccessSource {
          val realSource = fileSourceProvider.openRandomAccessSource()
          return object : RandomAccessSource by realSource {
            override fun read(sink: okio.Buffer, position: Long, byteCount: Long): Long {
              println("IO from thread ${Thread.currentThread().name}")
              return realSource.read(sink, position, byteCount)
            }
          }
        }
      }
      loadedGraph.value = wrapper.openHeapGraph()
    }
  }
}