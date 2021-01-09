package shark

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.desktop.AppManager
import androidx.compose.desktop.AppWindow
import androidx.compose.desktop.WindowEvents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.onActive
import androidx.compose.ui.input.key.ExperimentalKeyInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.KeyStroke
import androidx.compose.ui.window.Menu
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuItem
import androidx.compose.ui.window.Tray
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import javax.swing.SwingUtilities.invokeLater

fun main() {
  // To use Apple global menu.
  System.setProperty("apple.laf.useScreenMenuBar", "true")
  // Set application name
  System.setProperty("com.apple.mrj.application.apple.menu.about.name", "SharkApp")

  val image = getWindowIcon()

  showStartWindow(image)
}

private fun selectHeapDumpFile(onHeapGraphWindowShown: () -> Unit = {}) {
  val fileDialog = FileDialog(null as Frame?, "Select hprof file")
  fileDialog.isVisible = true

  if (fileDialog.file != null && fileDialog.file.endsWith(".hprof")) {
    val heapDumpFile = File(fileDialog.directory, fileDialog.file)
    showHeapGraphWindow(heapDumpFile) {
      onHeapGraphWindowShown()
    }
  }
}

private fun showStartWindow(image: BufferedImage) {
  invokeLater {
    lateinit var appWindow: AppWindow
    val selectHeapDumpFile = {
      selectHeapDumpFile {
        appWindow.close()
      }
    }
    appWindow = AppWindow(
      title = "SharkApp", size = IntSize(300, 300), icon = image,
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
        )
      )
    )

    appWindow.show {
      StartingWindow(onSelectFileClick = selectHeapDumpFile)
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

@OptIn(ExperimentalKeyInput::class)
fun showHeapGraphWindow(heapDumpFile: File, onWindowShown: () -> Unit) {
  val loadingState = HeapDumpLoadingState(heapDumpFile, Executors.newSingleThreadExecutor())
  loadingState.load()

  invokeLater {
    lateinit var appWindow: AppWindow
    appWindow = AppWindow(
      title = "${heapDumpFile.name} - SharkApp",
      events = WindowEvents(onClose = {
        loadingState.loadedGraph.value?.close()
        loadingState.ioExecutor.shutdown()
        println("Closed ${heapDumpFile.path}")
      }),
      menuBar = MenuBar(
        Menu(
          name = "File",
          MenuItem(
            name = "Open Heap Dump",
            onClick = ::selectHeapDumpFile,
            shortcut = KeyStroke(Key.O)
          ),
          MenuItem(
            name = "Close Heap Dump",
            onClick = {
              appWindow.close()
            },
            shortcut = KeyStroke(Key.W)
          )
        ),
      )
    )

    val pressedKeys = PressedKeys()

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { keyEvent ->
      when (keyEvent.id) {
        KeyEvent.KEY_PRESSED -> {
          when (keyEvent.keyCode) {
            KeyEvent.VK_META -> pressedKeys.meta = true
            KeyEvent.VK_CONTROL -> pressedKeys.ctrl = true
            KeyEvent.VK_SHIFT -> pressedKeys.shift = true
          }
        }
        KeyEvent.KEY_RELEASED -> {
          when (keyEvent.keyCode) {
            KeyEvent.VK_META -> pressedKeys.meta = false
            KeyEvent.VK_CONTROL -> pressedKeys.ctrl = false
            KeyEvent.VK_SHIFT -> pressedKeys.shift = false
          }
        }
      }
      false
    }

    appWindow.show {
      HeapGraphWindow(loadingState, pressedKeys)
    }

    onWindowShown()
  }
}

class PressedKeys {
  var meta = false
  var ctrl = false
  var shift = false
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScreenTransition(visible: Boolean, forward: Boolean, content: @Composable () -> Unit) {
  if (visible) {
    content()
  }
  // TODO Figure out how to make transitions work.
  if (true) {
    return
  }
  if (forward) {
    AnimatedVisibility(
      initiallyVisible = false,
      visible = visible,
      enter = slideInHorizontally(
        // Offsets the content by 1/3 of its width to the left, and slide towards right
        initialOffsetX = { fullWidth -> fullWidth },
        // Overwrites the default animation with tween for this slide animation.
        animSpec = tween(durationMillis = 4000)
      ),
      exit = slideOutHorizontally(
        // Overwrites the ending position of the slide-out to 200 (pixels) to the right
        targetOffsetX = { fullWidth -> -fullWidth },
        animSpec = tween(durationMillis = 4000)
        // animSpec = spring(stiffness = Spring.StiffnessHigh)
      )
    ) {
      content()
    }
  } else {
    AnimatedVisibility(
      initiallyVisible = false,
      visible = visible,
      enter = slideInHorizontally(
        // Offsets the content by 1/3 of its width to the left, and slide towards right
        initialOffsetX = { fullWidth -> -fullWidth },
        // Overwrites the default animation with tween for this slide animation.
        // animSpec = tween(durationMillis = 200)
        animSpec = tween(durationMillis = 4000)
      ),
      exit = slideOutHorizontally(
        // Overwrites the ending position of the slide-out to 200 (pixels) to the right
        targetOffsetX = { fullWidth -> fullWidth },
        // animSpec = spring(stiffness = Spring.StiffnessHigh)
        animSpec = tween(durationMillis = 4000)
      )
    ) {
      content()
    }
  }
}
