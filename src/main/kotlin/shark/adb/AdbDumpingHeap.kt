package shark.adb

import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import shark.adb.CommandResult.Error
import shark.adb.CommandResult.Success
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@Composable fun DumpingHeapScreen(
  viewModel: DumpingHeapViewModel,
  onOpenHeapDump: (File) -> Unit
) {
  val scope = rememberCoroutineScope()

  LaunchedEffect(viewModel) {
    viewModel.dumpHeap()?.let {
      onOpenHeapDump(it)
    }
  }

  when (val state = viewModel.state.collectAsState().value) {
    is DumpingHeapState.Loading -> {
      Box(modifier = Modifier.fillMaxSize()) {
        ListItem(text = { Text("Dumping in progress...") },
          secondaryText = { Text(state.progress.value) })
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
      }
    }
    is DumpingHeapState.Failed -> {
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
                viewModel.dumpHeap()?.let {
                  onOpenHeapDump(it)
                }
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
  }
}

sealed class DumpingHeapState {

  class Loading : DumpingHeapState() {
    val progress = mutableStateOf("")
  }

  data class Failed(
    val errorMessage: String,
  ) : DumpingHeapState()
}

interface DumpingHeapViewModel {
  val state: StateFlow<DumpingHeapState>
  suspend fun dumpHeap(): File?
}

class RealDumpingHeapViewModel(private val device: AdbDevice, private val process: AndroidProcess) :
  DumpingHeapViewModel {
  override val state = MutableStateFlow<DumpingHeapState>(DumpingHeapState.Loading())

  override suspend fun dumpHeap(): File? {
    val loading = DumpingHeapState.Loading()
    state.value = loading
    return when (val commandResult = device.dumpHeap(process, loading.progress)) {

      is Success -> commandResult.value
      is Error -> {
        state.value = DumpingHeapState.Failed(
          errorMessage = commandResult.errorMessage,
        )
        null
      }
    }
  }
}