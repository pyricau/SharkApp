package shark.adb

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import shark.adb.AdbDevice.Offline
import shark.adb.AdbDevice.Online
import shark.adb.AdbDumpHeapScreen.ListProcesses
import shark.adb.CommandResult.Error
import shark.adb.CommandResult.Success
import shark.adb.DeviceListState.Failed
import shark.adb.DeviceListState.Loaded
import shark.adb.DeviceListState.Loading

@OptIn(ExperimentalCoroutinesApi::class)
@Composable fun ListDevicesScreen(
  viewModel: ListDevicesViewModel,
  goTo: (AdbDumpHeapScreen) -> Unit
) {
  val scope = rememberCoroutineScope()

  LaunchedEffect(viewModel) {
    viewModel.updateDeviceList()
  }

  when (val state = viewModel.state.collectAsState().value) {
    Loading -> {
      Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
      }
    }
    is Failed -> {
      Box {
        val scrollState = rememberScrollState(0f)
        Column(
          modifier = Modifier.verticalScroll(state = scrollState)
        ) {
          ListItem(
            text = { Text("Error") },
            secondaryText = { Text(state.errorMessage) },
          )
          Box(modifier = Modifier.padding(16.dp)) {
            Button(onClick = {
              scope.launch {
                viewModel.updateDeviceList()
              }
            }) {
              Text("Retry")
            }
          }
        }
        VerticalScrollbar(
          rememberScrollbarAdapter(scrollState),
          Modifier.align(Alignment.CenterEnd)
        )
      }
    }
    is Loaded -> DevicesList(state.devices, onDeviceClicked = { device ->
      goTo(ListProcesses(device))
    })
  }
}

@Composable
fun DevicesList(devices: List<AdbDevice>, onDeviceClicked: (AdbDevice) -> Unit) {
  if (devices.isNotEmpty()) {
    Box {
      val scrollState = rememberScrollState(0f)
      Column(
        modifier = Modifier.verticalScroll(
          state = scrollState
        )
      ) {
        for (device in devices) {
          when (device) {
            is Online -> {
              ListItem(
                modifier = Modifier.clickable {
                  onDeviceClicked(device)
                },
                text = { Text(device.serialNumber) },
                secondaryText = { Text("Product: ${device.product}\nModel: ${device.model}\n") },
                trailing = { Text("Device: ${device.device}") })
            }
            is Offline -> {
              ListItem(
                text = { Text(device.serialNumber) },
                secondaryText = { Text("Offline") },
              )
            }
          }
        }
      }
      VerticalScrollbar(
        rememberScrollbarAdapter(scrollState),
        Modifier.align(Alignment.CenterEnd)
      )
    }
  } else {
    Text("No connected device")
  }
}

sealed class DeviceListState {

  object Loading : DeviceListState()

  data class Failed(
    val errorMessage: String,
  ) : DeviceListState()

  data class Loaded(
    val devices: List<AdbDevice>,
  ) : DeviceListState()
}

interface ListDevicesViewModel {
  val state: StateFlow<DeviceListState>
  suspend fun updateDeviceList()
}

class RealListDevicesViewModel : ListDevicesViewModel {
  override val state = MutableStateFlow<DeviceListState>(Loading)

  override suspend fun updateDeviceList() {
    loadDevices()
  }

  private suspend fun loadDevices() {
    state.value = Loading
    when (val commandResult = listDevices()) {
      is Success -> state.value = Loaded(
        devices = commandResult.value,
      )
      is Error -> state.value = Failed(
        errorMessage = "Error: ${commandResult.errorMessage}",
      )
    }
  }
}