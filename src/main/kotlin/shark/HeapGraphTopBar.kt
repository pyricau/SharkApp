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
  backstack: List<Screen>,
  forwardStack: List<Screen>,
  toggleDrawer: () -> Unit,
  goBack: () -> Unit,
  goForward: () -> Unit
) {
  TopAppBar(
    title = { Text(backstack.last().title) },
    navigationIcon = {
      IconButton(onClick = toggleDrawer) {
        Icon(Icons.Default.Menu)
      }
    },
    actions = {
      IconButton(onClick = { goBack() }, enabled = backstack.size > 1) {
        Icon(
          Icons.Default.ArrowBack,
          tint = if (backstack.size > 1) AmbientContentColor.current else AmbientContentColor.current.copy(
            alpha = AmbientContentAlpha.current
          )
        )
      }
      IconButton(onClick = { goForward() }, enabled = forwardStack.isNotEmpty()) {
        Icon(
          Icons.Default.ArrowForward,
          tint = if (forwardStack.isNotEmpty()) AmbientContentColor.current else AmbientContentColor.current.copy(
            alpha = AmbientContentAlpha.current
          )
        )
      }
    }
  )
}