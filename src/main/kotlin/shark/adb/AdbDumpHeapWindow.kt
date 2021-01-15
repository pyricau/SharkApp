package shark.adb

import androidx.compose.desktop.AppWindow
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.window.KeyStroke
import androidx.compose.ui.window.Menu
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuItem
import backstack.Backstack
import kotlinx.coroutines.launch
import shark.ScreenNavigator
import shark.adb.AdbDumpHeapScreen.DumpHeap
import shark.adb.AdbDumpHeapScreen.ListDevices
import shark.adb.AdbDumpHeapScreen.ListProcesses
import shark.showHeapGraphWindow
import shark.showSelectHeapDumpFileWindow
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.SwingUtilities.invokeLater

sealed class AdbDumpHeapScreen {

  abstract val title: String

  object ListDevices : AdbDumpHeapScreen() {
    override val title = "Attached Android devices"
  }

  class ListProcesses(val device: AdbDevice) : AdbDumpHeapScreen() {
    override val title = "Processes for ${device.serialNumber}"
  }

  class DumpHeap(val device: AdbDevice, val process: AndroidProcess) : AdbDumpHeapScreen() {
    override val title = "Dumping heap on ${device.serialNumber} for ${process.name}"
  }
}

fun showAdbDumpHeapWindow(windowIcon: BufferedImage, onWindowShown: () -> Unit = {}) {
  invokeLater {
    val appWindow = AppWindow(
      title = "Dumb Heap with adb - SharkApp",
      icon = windowIcon,
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
        ),
      )
    )

    appWindow.show {
      AdbDumpHeapWindow(ScreenNavigator(
        firstScreen = ListDevices,
        recentsEquals = { _, _ -> true }
      ), onOpenHeapDump = { heapDumpFile ->
        showHeapGraphWindow(
          windowIcon,
          heapDumpFile,
          onWindowShown = {
            appWindow.close()
          }
        )
      })
    }
    onWindowShown()
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AdbDumpHeapWindow(
  navigator: ScreenNavigator<AdbDumpHeapScreen>,
  onOpenHeapDump: (File) -> Unit
) {
  MaterialTheme {
    val refreshHandler = remember { RefreshHandler() }

    Scaffold(
      topBar = {
        AdbDumbHeapTopBar(navigator, onRefresh = refreshHandler.onRefreshClicked)
      }
    ) {
      Backstack(navigator.backstackUnique) { wrappedScreen ->
        when (val screen = wrappedScreen.screen) {
          is ListDevices -> {
            val viewModel = remember { RealListDevicesViewModel() }

            refreshHandler.onRefreshClickedWhile(viewModel) {
              updateDeviceList()
            }
            ListDevicesScreen(
              viewModel = viewModel,
              goTo = navigator::goTo
            )
          }
          is ListProcesses -> {
            val viewModel = remember { RealListProcessesViewModel(screen.device) }
            refreshHandler.onRefreshClickedWhile(viewModel) {
              updateProcessList()
            }
            ListProcessesScreen(
              viewModel = viewModel,
              goTo = navigator::goTo
            )
          }
          is DumpHeap -> {
            DumpingHeapScreen(
              viewModel = remember { RealDumpingHeapViewModel(screen.device, screen.process) },
              onOpenHeapDump = onOpenHeapDump
            )
          }
        }
      }
    }
  }
}

class RefreshHandler {
  var onRefreshClicked by mutableStateOf<(() -> Unit)?>(null)
    private set

  @Composable
  fun <T> onRefreshClickedWhile(subject: T, onRefresh: suspend T.() -> Unit) {
    val compositionScope = rememberCoroutineScope()
    DisposableEffect(subject) {
      val handleRefresh: () -> Unit = {
        compositionScope.launch { subject.onRefresh() }
      }
      onRefreshClicked = handleRefresh
      onDispose {
        // The previous disposable cancels after the new one sets up.
        if (onRefreshClicked == handleRefresh) {
          onRefreshClicked = null
        }
      }
    }
  }
}

