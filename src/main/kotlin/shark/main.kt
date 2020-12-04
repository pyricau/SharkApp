package shark

import androidx.compose.desktop.AppManager
import androidx.compose.desktop.Window
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.onActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuItem
import androidx.compose.ui.window.Tray
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Executors
import javax.imageio.ImageIO

fun main() {
  val image = getWindowIcon()
  showStartWindow(image)
}

private fun showStartWindow(image: BufferedImage) {
  Window(title = "SharkApp", size = IntSize(300, 300), icon = image) {
    MaterialTheme {
      Column(Modifier.fillMaxSize(), Arrangement.spacedBy(25.dp)) {
        Image(
          bitmap = imageResource("shark.png"),
          modifier = Modifier.fillMaxWidth()
        )
        Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
          val fileDialog = FileDialog(null as Frame?, "Select hprof file")
          fileDialog.isVisible = true
          val heapDumpFile = File(fileDialog.directory, fileDialog.file)
          showHeapGraphWindow(heapDumpFile)
        }) {
          Text("Open Heap Dump")
        }
      }
    }
  }
}

fun getWindowIcon(): BufferedImage {
  val sharkIconUrl = Thread.currentThread().contextClassLoader.getResource("shark.png")!!
  var image: BufferedImage? = null
  try {
    image = ImageIO.read(File(sharkIconUrl.file))
  } catch (e: Exception) {
    // image file does not exist
  }
  if (image == null) {
    image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
  }
  return image
}

fun showHeapGraphWindow(heapDumpFile: File) {
  val loadingState = HeapDumpLoadingState(heapDumpFile, Executors.newSingleThreadExecutor())
  Window(title = "${heapDumpFile.name} - SharkApp") {
    onActive {
      val tray = Tray().apply {
        icon(getWindowIcon())
        menu(
          MenuItem(
            name = "Quit App",
            onClick = { AppManager.exit() }
          )
        )
      }
      onDispose {
        tray.remove()
        loadingState.loadedGraph.value?.close()
        loadingState.ioExecutor.shutdown()
        println("Closed ${heapDumpFile.path}")
      }
    }

    MaterialTheme {
      val graph = loadingState.loadedGraph.value
      if (graph == null) {
        LoadingScreen(loadingState.file.name)
      } else {
        HeapGraphScreen(graph, loadingState.ioExecutor)
      }
    }
  }
  loadingState.load()
}

