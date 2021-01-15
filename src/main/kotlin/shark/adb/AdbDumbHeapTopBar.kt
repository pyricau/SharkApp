package shark.adb

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material.AmbientContentAlpha
import androidx.compose.material.AmbientContentColor
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import shark.ScreenNavigator

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdbDumbHeapTopBar(
  navigator: ScreenNavigator<AdbDumpHeapScreen>,
  onRefresh: (() -> Unit)?
) {
  TopAppBar(
    title = { Text(navigator.currentScreen.title) },
    navigationIcon = {
      AnimatedVisibility(
        navigator.canGoBack,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        IconButton(onClick = { navigator.goBack() }, enabled = navigator.canGoBack) {
          Icon(
            Icons.Default.ArrowBack,
            tint = AmbientContentColor.current
          )
        }
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