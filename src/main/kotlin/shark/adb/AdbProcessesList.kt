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
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import shark.adb.AdbDumpHeapScreen.DumpHeap
import shark.adb.CommandResult.Error
import shark.adb.CommandResult.Success
import shark.adb.ProcessListState.Failed
import shark.adb.ProcessListState.Loaded
import shark.adb.ProcessListState.Loading

@OptIn(ExperimentalCoroutinesApi::class)
@Composable fun ListProcessesScreen(
  viewModel: ListProcessesViewModel,
  goTo: (AdbDumpHeapScreen) -> Unit
) {
  val scope = rememberCoroutineScope()

  LaunchedEffect(viewModel) {
    viewModel.updateProcessList()
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
                viewModel.updateProcessList()
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
    is Loaded -> ProcessesList(state.processes, onProcessClicked = { process ->
      goTo(DumpHeap(viewModel.device, process))
    })
  }
}

@Composable
fun ProcessesList(processes: List<AndroidProcess>, onProcessClicked: (AndroidProcess) -> Unit) {
  if (processes.isNotEmpty()) {
    var filter by remember { mutableStateOf("") }
    Column {
      OutlinedTextField(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        value = filter,
        onValueChange = { filter = it },
        label = {
          Text("Process name filter")
        },
      )

      val filteredProcesses = processes.filter { filter in it.name }.sortedBy { it.name }

      Box {
        val scrollState = rememberScrollState(0f)
        Column(
          modifier = Modifier.verticalScroll(
            state = scrollState
          )
        ) {
          for (process in filteredProcesses) {
            ListItem(
              modifier = Modifier.clickable {
                onProcessClicked(process)
              },
              text = { Text(process.name) },
              secondaryText = { Text("Pid: ${process.pid}") })
          }
        }
        VerticalScrollbar(
          rememberScrollbarAdapter(scrollState),
          Modifier.align(Alignment.CenterEnd)
        )
      }
    }
  } else {
    Text("No process found.")
  }
}

sealed class ProcessListState {

  object Loading : ProcessListState()

  data class Failed(
    val errorMessage: String,
  ) : ProcessListState()

  data class Loaded(
    val processes: List<AndroidProcess>,
  ) : ProcessListState()
}

interface ListProcessesViewModel {
  val device: AdbDevice
  val state: StateFlow<ProcessListState>
  suspend fun updateProcessList()
}

class RealListProcessesViewModel(override val device: AdbDevice) : ListProcessesViewModel {
  override val state = MutableStateFlow<ProcessListState>(Loading)

  override suspend fun updateProcessList() {
    loadProcesses()
  }

  private suspend fun loadProcesses() {
    state.value = Loading
    when (val commandResult = device.listProcesses()) {
      is Success -> state.value = Loaded(
        processes = commandResult.value,
      )
      is Error -> state.value = Failed(
        errorMessage = "Error: ${commandResult.errorMessage}",
      )
    }
  }
}