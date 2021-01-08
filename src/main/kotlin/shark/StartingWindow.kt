package shark

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StartingWindow(onSelectFileClick: () -> Unit) {
  val scope = rememberCoroutineScope()
  MaterialTheme {
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(25.dp)) {
      Image(
        bitmap = imageResource("shark.png"),
        modifier = Modifier.fillMaxWidth()
      )
      Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
        // TODO Debounce / figure  out  what happens on multi click.
        scope.launch {
          delay(200)
          onSelectFileClick()
        }
      }) {
        Text("Open Heap Dump")
      }
    }
  }
}