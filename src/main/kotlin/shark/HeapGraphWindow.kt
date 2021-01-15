package shark

import androidx.compose.desktop.AppWindow
import androidx.compose.desktop.WindowEvents
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.ExperimentalKeyInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.Key.Companion
import androidx.compose.ui.input.key.plus
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.KeyStroke
import androidx.compose.ui.window.Menu
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuItem
import backstack.Backstack
import shark.SharkScreen.HeapObjectTree
import shark.SharkScreen.Home
import shark.adb.showAdbDumpHeapWindow
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Executors
import javax.swing.SwingUtilities

@OptIn(
  ExperimentalKeyInput::class,
  ExperimentalFoundationApi::class,
)
@Composable
fun HeapGraphWindow(
  navigator: ScreenNavigator<SharkScreen>,
  loadingState: HeapDumpLoadingState,
  pressedKeys: PressedKeys
) {
  MaterialTheme {
    val loadedGraph = loadingState.loadedGraph.value
    if (loadedGraph == null) {
      LoadingScreen(loadingState.file.name)
    } else {
      var drawerVisible by remember { mutableStateOf(false) }
      Scaffold(
        topBar = {
          HeapGraphTopBar(
            navigator = navigator,
            toggleDrawer = { drawerVisible = !drawerVisible })
        },
      ) {
        Row {
          HeapGraphDrawer(drawerVisible = drawerVisible, navigator = navigator)
          Backstack(navigator.backstackUnique) { wrappedScreen ->
            when (val screen = wrappedScreen.screen) {
              is Home -> HomeScreen(loadedGraph, navigator::goTo)
              is HeapObjectTree -> HeapObjectTreeScreen(
                loadedGraph,
                pressedKeys,
                screen,
                navigator::goTo
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalKeyInput::class)
fun showHeapGraphWindow(
  windowIcon: BufferedImage,
  heapDumpFile: File,
  onWindowShown: () -> Unit = {}
) {
  val loadingState = HeapDumpLoadingState(heapDumpFile, Executors.newSingleThreadExecutor())
  loadingState.load()

  SwingUtilities.invokeLater {
    val screenSize = Toolkit.getDefaultToolkit().screenSize

    lateinit var appWindow: AppWindow
    appWindow = AppWindow(
      title = "${heapDumpFile.name} - SharkApp",
      size = IntSize((screenSize.width * 0.8f).toInt(), (screenSize.height * 0.8f).toInt()),
      centered = true,
      icon = windowIcon,
      events = WindowEvents(onClose = {
        loadingState.loadedGraph.value?.close()
        loadingState.ioExecutor.shutdown()
      }),
      menuBar = MenuBar(
        Menu(
          name = "File",
          MenuItem(
            name = "Open Heap Dump",
            onClick = {
              showSelectHeapDumpFileWindow(onHprofFileSelected = { selectedFile ->
                showHeapGraphWindow(windowIcon, selectedFile)
              })
            },
            shortcut = KeyStroke(Key.O)
          ),
          MenuItem(
            name = "Dump Heap with adb",
            onClick = { showAdbDumpHeapWindow(windowIcon) },
            shortcut = KeyStroke(Key.D)
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
      setShortcut(Key.AltLeft + Key(KeyEvent.VK_LEFT), navigator::goBack)
      setShortcut(Key.AltRight + Key(KeyEvent.VK_LEFT), navigator::goBack)
      setShortcut(Key.AltLeft + Key(KeyEvent.VK_RIGHT), navigator::goForward)
      setShortcut(Key.AltLeft + Key(KeyEvent.VK_RIGHT), navigator::goForward)
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

fun showSelectHeapDumpFileWindow(onHprofFileSelected: (File) -> Unit) {
  val fileDialog = FileDialog(null as Frame?, "Select hprof file")
  fileDialog.isVisible = true

  if (fileDialog.file != null && fileDialog.file.endsWith(".hprof")) {
    val heapDumpFile = File(fileDialog.directory, fileDialog.file)
    onHprofFileSelected(heapDumpFile)
  }
}