package shark

import androidx.compose.material.AmbientContentAlpha
import androidx.compose.material.AmbientContentColor
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable

@Composable
fun HeapGraphTopBar(
  navigator: ScreenNavigator<SharkScreen>,
  toggleDrawer: () -> Unit,
) {
  TopAppBar(
    title = { Text(navigator.currentScreen.title) },
    navigationIcon = {
      IconButton(onClick = toggleDrawer) {
        Icon(Icons.Default.Menu)
      }
    },
    actions = {
      IconButton(onClick = { navigator.goBack() }, enabled = navigator.canGoBack) {
        Icon(
          Icons.Default.ArrowBack,
          tint = if (navigator.canGoBack) AmbientContentColor.current else AmbientContentColor.current.copy(
            alpha = AmbientContentAlpha.current
          )
        )
      }
      IconButton(onClick = { navigator.goForward() }, enabled = navigator.canGoForward) {
        Icon(
          Icons.Default.ArrowForward,
          tint = if (navigator.forwardStack.isNotEmpty()) AmbientContentColor.current else AmbientContentColor.current.copy(
            alpha = AmbientContentAlpha.current
          )
        )
      }
    }
  )
}