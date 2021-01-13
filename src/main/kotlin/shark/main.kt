package shark

import androidx.compose.desktop.AppWindow
import androidx.compose.desktop.WindowEvents
import androidx.compose.ui.input.key.ExperimentalKeyInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.plus
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.KeyStroke
import androidx.compose.ui.window.Menu
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuItem
import shark.SharkScreen.Home
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import javax.swing.SwingUtilities.invokeLater
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
    exception.printStackTrace()
    exitProcess(1)
  }

  // To use Apple global menu.
  System.setProperty("apple.laf.useScreenMenuBar", "true")
  // Set application name
  System.setProperty("com.apple.mrj.application.apple.menu.about.name", "SharkApp")

  if (args.size == 1 && args.first().endsWith(".hprof")) {
    val heapDumpFile = File(args.first())
    if (heapDumpFile.name.endsWith(".hprof") && heapDumpFile.exists()) {
      showHeapGraphWindow(heapDumpFile)
      return
    }
  }

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
fun showHeapGraphWindow(heapDumpFile: File, onWindowShown: () -> Unit = {}) {
  val loadingState = HeapDumpLoadingState(heapDumpFile, Executors.newSingleThreadExecutor())
  loadingState.load()

  invokeLater {
    val screenSize = Toolkit.getDefaultToolkit().screenSize

    lateinit var appWindow: AppWindow
    appWindow = AppWindow(
      title = "${heapDumpFile.name} - SharkApp",
      size = IntSize((screenSize.width * 0.8f).toInt(), (screenSize.height * 0.8f).toInt()),
      centered = true,
      events = WindowEvents(onClose = {
        loadingState.loadedGraph.value?.close()
        loadingState.ioExecutor.shutdown()
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
            KeyEvent.VK_ALT -> pressedKeys.alt = true
            KeyEvent.VK_META -> pressedKeys.meta = true
            KeyEvent.VK_CONTROL -> pressedKeys.ctrl = true
            KeyEvent.VK_SHIFT -> pressedKeys.shift = true
          }
        }
        KeyEvent.KEY_RELEASED -> {
          when (keyEvent.keyCode) {
            KeyEvent.VK_ALT -> pressedKeys.alt = false
            KeyEvent.VK_META -> pressedKeys.meta = false
            KeyEvent.VK_CONTROL -> pressedKeys.ctrl = false
            KeyEvent.VK_SHIFT -> pressedKeys.shift = false
          }
        }
      }
      false
    }

    val navigator = ScreenNavigator<SharkScreen>(
      Home(),
      recentsEquals = { screen1, screen2 -> screen1.title == screen2.title })
    appWindow.keyboard.apply {
      setShortcut(Key.AltLeft + Key(VK_LEFT), navigator::goBack)
      setShortcut(Key.AltRight + Key(VK_LEFT), navigator::goBack)
      setShortcut(Key.AltLeft + Key(VK_RIGHT), navigator::goForward)
      setShortcut(Key.AltLeft + Key(VK_RIGHT), navigator::goForward)
    }
    appWindow.show {
      HeapGraphWindow(navigator, loadingState, pressedKeys)
    }

    onWindowShown()
  }
}

class PressedKeys {
  var alt = false
  var ctrl = false
  var meta = false
  var shift = false
}