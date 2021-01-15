package shark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LoadingScreen(fileName: String) {
  Box(modifier = Modifier.fillMaxSize()) {
    ListItem(text = { Text("Loading heap dump...") },
      secondaryText = { Text(fileName) })
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
  }
}