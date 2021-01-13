package shark.adb

import androidx.compose.desktop.AppWindow
import androidx.compose.foundation.layout.Column
import androidx.compose.material.AmbientContentAlpha
import androidx.compose.material.AmbientContentColor
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarResult.ActionPerformed
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import backstack.Backstack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import shark.ScreenNavigator
import shark.adb.AdbDevice.Offline
import shark.adb.AdbDevice.Online
import shark.adb.AdbDumpHeapScreen.ListDevices
import shark.adb.CommandResult.Error
import shark.adb.CommandResult.Success
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities.invokeLater

sealed class AdbDumpHeapScreen {

  abstract val title: String

  object ListDevices : AdbDumpHeapScreen() {
    override val title = ""
  }

}

fun showAdbDumpHeapWindow(windowIcon: BufferedImage, onWindowShown: () -> Unit = {}) {
  invokeLater {
    AppWindow(
      title = "Dumb Heap with adb - SharkApp",
      icon = windowIcon
    ).show {
      AdbDumpHeapWindow(ScreenNavigator(
        firstScreen = ListDevices,
        recentsEquals = { _, _ -> true }
      ))
    }
    onWindowShown()
  }
}

class RealListDevicesViewModel : ListDevicesViewModel {
  override val state = MutableStateFlow<DeviceListState>(DeviceListState.Loading)

  override suspend fun onShown(compositionScope: CoroutineScope) {
    loadDevices(compositionScope)
  }

  private suspend fun loadDevices(compositionScope: CoroutineScope) {
    state.value = DeviceListState.Loading
    when (val commandResult = listDevices()) {
      is Success -> state.value = DeviceListState.Loaded(
        devices = commandResult.value,
        onRefresh = {
          compositionScope.launch { loadDevices(compositionScope) }
        }
      )
      is Error -> state.value = DeviceListState.Failed(
        errorMessage = "Error: ${commandResult.errorMessage}",
        onRetry = {
          compositionScope.launch { loadDevices(compositionScope) }
        },
      )
    }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AdbDumpHeapWindow(navigator: ScreenNavigator<AdbDumpHeapScreen>) {
  MaterialTheme {
    val scaffoldState = rememberScaffoldState()

    val onRefreshClickedRegistrar = remember { mutableStateOf<(() -> Unit)?>(null) }

    Scaffold(
      scaffoldState = scaffoldState,
      topBar = {
        TopBar(navigator, onRefresh = onRefreshClickedRegistrar.value)
      }
    ) {
      Backstack(navigator.backstackUnique) { wrappedScreen ->
        when (val screen = wrappedScreen.screen) {
          is ListDevices -> {
            ListDevicesScreen(
              viewModel = remember { RealListDevicesViewModel() },
              showRetryableError = { message ->
                scaffoldState.snackbarHostState.showSnackbar(message, "Retry") == ActionPerformed
              },
              onRefreshClickedRegistrar = onRefreshClickedRegistrar::value.setter
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TopBar(
  navigator: ScreenNavigator<AdbDumpHeapScreen>,
  onRefresh: (() -> Unit)?
) {
  TopAppBar(
    title = { Text(navigator.currentScreen.title) },
    navigationIcon = {
      IconButton(onClick = { navigator.goBack() }, enabled = navigator.canGoBack) {
        Icon(
          Icons.Default.ArrowBack,
          tint = if (navigator.canGoBack) AmbientContentColor.current else AmbientContentColor.current.copy(
            alpha = AmbientContentAlpha.current
          )
        )
      }
    },
    actions = {
      if (onRefresh != null) {
        IconButton(onClick = onRefresh) {
          Icon(
            Icons.Default.Refresh,
            tint = AmbientContentColor.current
          )
        }
      }
    }
  )
}

sealed class DeviceListState {

  object Loading : DeviceListState()

  data class Failed(
    val errorMessage: String,
    val onRetry: () -> Unit,
  ) : DeviceListState()

  data class Loaded(
    val devices: List<AdbDevice>,
    val onRefresh: () -> Unit
  ) : DeviceListState()
}

interface ListDevicesViewModel {
  val state: StateFlow<DeviceListState>
  suspend fun onShown(compositionScope: CoroutineScope)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable fun ListDevicesScreen(
  viewModel: ListDevicesViewModel,
  showRetryableError: suspend (String) -> Boolean,
  onRefreshClickedRegistrar: ((() -> Unit)?) -> Unit
) {

  val compositionScope = rememberCoroutineScope()
  val state by viewModel.state.collectAsState()
  LaunchedEffect(viewModel) {
    viewModel.onShown(compositionScope)
  }

  // Extracting state via let to avoid smart cast issues.
  state.let { state ->
    DisposableEffect(state) {
      onRefreshClickedRegistrar(
        when (state) {
          DeviceListState.Loading -> null
          is DeviceListState.Failed -> state.onRetry
          is DeviceListState.Loaded -> state.onRefresh
        }
      )
      onDispose {
        onRefreshClickedRegistrar(null)
      }
    }

    when (state) {
      DeviceListState.Loading -> Text("Listing devices...")
      is DeviceListState.Failed -> {
        LaunchedEffect(state) {
          if (showRetryableError(state.errorMessage)) {
            state.onRetry()
          }
        }
        Text("Error")
      }
      is DeviceListState.Loaded -> DevicesList(state.devices)
    }
  }
}

@Composable
fun DevicesList(devices: List<AdbDevice>) {
  if (devices.isNotEmpty()) {
    Column {
      for (device in devices) {
        when (device) {
          is Online -> {
            ListItem(
              text = { Text(device.serialNumber) },
              secondaryText = { Text("Product: ${device.product}\nModel: ${device.model}\nDevice: ${device.device}") },
              trailing = { Text("This is trailing text") })
          }
          is Offline -> {
            ListItem(
              text = { Text(device.serialNumber) },
              secondaryText = { Text("Offline") },
              trailing = { Text("This is trailing text") })
          }
        }
      }
    }
  } else {
    Text("No connected device")
  }
}

