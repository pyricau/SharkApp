package shark.start

import androidx.compose.desktop.AppWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.KeyStroke
import androidx.compose.ui.window.Menu
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuItem
import shark.adb.showAdbDumpHeapWindow
import shark.showHeapGraphWindow
import shark.showSelectHeapDumpFileWindow
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities.invokeLater
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
    // TODO Display error in screen, asking to file.
    System.err.println("Error on thread ${thread.name}")
    exception.printStackTrace()
    exitProcess(1)
  }

  // To use Apple global menu.
  System.setProperty("apple.laf.useScreenMenuBar", "true")
  // Set application name
  System.setProperty("com.apple.mrj.application.apple.menu.about.name", "SharkApp")

  val windowIcon = getWindowIcon()

  if (args.size == 1 && args.first().endsWith(".hprof")) {
    val heapDumpFile = File(args.first())
    if (heapDumpFile.name.endsWith(".hprof") && heapDumpFile.exists()) {
      showHeapGraphWindow(windowIcon, heapDumpFile)
      return
    }
  }

  // TODO These should be suspending functions
  showStartWindow(windowIcon, onOpenHeapDumpSelector = { closeStartWindow ->
    showSelectHeapDumpFileWindow(onHprofFileSelected = { hprofFile ->
      showHeapGraphWindow(windowIcon, hprofFile, onWindowShown = {
        closeStartWindow()
      })
    })
  })
}

private fun showStartWindow(
  windowIcon: BufferedImage,
  onOpenHeapDumpSelector: (() -> Unit) -> Unit
) {
  invokeLater {
    lateinit var appWindow: AppWindow
    val selectHeapDumpFile = {
      onOpenHeapDumpSelector {
        appWindow.close()
      }
    }
    appWindow = AppWindow(
      title = "SharkApp", size = IntSize(400, 400), icon = windowIcon,
      menuBar = MenuBar(
        Menu(
          name = "File",
          MenuItem(
            name = "Open Heap Dump",
            onClick = {
              selectHeapDumpFile()
            },
            shortcut = KeyStroke(Key.O)
          ),
          MenuItem(
            name = "Dump Heap with adb",
            onClick = { showAdbDumpHeapWindow(windowIcon, onWindowShown = { appWindow.close() }) },
            shortcut = KeyStroke(Key.D)
          ),
        )
      )
    )

    appWindow.show {
      StartingWindow(onSelectFileClick = selectHeapDumpFile, onDumpHeapClicked = {
        showAdbDumpHeapWindow(windowIcon, onWindowShown = { appWindow.close() })
      })
    }
  }
}

private fun getWindowIcon(): BufferedImage {
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

