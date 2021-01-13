package shark

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.ExperimentalKeyInput
import backstack.Backstack
import shark.SharkScreen.HeapObjectTree
import shark.SharkScreen.Home

@OptIn(
  ExperimentalKeyInput::class,
  ExperimentalFoundationApi::class,
)
@Composable
fun HeapGraphWindow(
  navigator: ScreenNavigator<SharkScreen>,
  loadingState: HeapDumpLoadingState,
  pressedKeys: PressedKeys
) {
  MaterialTheme {
    val loadedGraph = loadingState.loadedGraph.value
    if (loadedGraph == null) {
      LoadingScreen(loadingState.file.name)
    } else {
      var drawerVisible by remember { mutableStateOf(false) }
      Scaffold(
        topBar = {
          HeapGraphTopBar(
            navigator = navigator,
            toggleDrawer = { drawerVisible = !drawerVisible })
        },
      ) {
        Row {
          HeapGraphDrawer(drawerVisible = drawerVisible, navigator = navigator)
          Backstack(navigator.backstackUnique) { wrappedScreen ->
            when (val screen = wrappedScreen.screen) {
              is Home -> HomeScreen(loadedGraph, navigator::goTo)
              is HeapObjectTree -> HeapObjectTreeScreen(
                loadedGraph,
                pressedKeys,
                screen,
                navigator::goTo
              )
            }
          }
        }
      }
    }
  }
}
