package shark

import androidx.compose.material.Text
import androidx.compose.runtime.Composable

@Composable
fun LoadingScreen(fileName: String) {
  Text("Loading $fileName")
}