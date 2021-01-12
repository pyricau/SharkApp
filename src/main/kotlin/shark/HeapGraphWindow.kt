package shark

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.material.DrawerValue
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.ExperimentalKeyInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.plus
import androidx.compose.ui.platform.Keyboard
import shark.Screen.HeapObjectTree
import shark.Screen.Home
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT

@OptIn(
  ExperimentalKeyInput::class,
  ExperimentalFoundationApi::class,
)
@Composable
fun HeapGraphWindow(
  keyboard: Keyboard,
  loadingState: HeapDumpLoadingState,
  pressedKeys: PressedKeys
) {
  var goBackLazyForKeys: (() -> Unit)? = null
  var goForwardForKeys: (() -> Unit)? = null
  keyboard.setShortcut(Key.AltLeft + Key(VK_LEFT)) {
    goBackLazyForKeys?.invoke()
  }
  keyboard.setShortcut(Key.AltRight + Key(VK_LEFT)) {
    goBackLazyForKeys?.invoke()
  }
  keyboard.setShortcut(Key.AltLeft + Key(VK_RIGHT)) {
    goForwardForKeys?.invoke()
  }
  keyboard.setShortcut(Key.AltRight + Key(VK_RIGHT)) {
    goForwardForKeys?.invoke()
  }
  MaterialTheme {
    val loadedGraph = loadingState.loadedGraph.value
    if (loadedGraph == null) {
      LoadingScreen(loadingState.file.name)
    } else {

      // Wraps screen so that the same screen can be at different parts of the backstack
      // ising different wrapper instances.
      class ScreenUniqueByIdentify(val screen: Screen)

      fun Screen.byIdentity() = ScreenUniqueByIdentify(this)


      var recents by remember { mutableStateOf(listOf(Home().toRecent())) }
      var backstack by remember { mutableStateOf(listOf(Home().byIdentity())) }
      var forwardStack by remember { mutableStateOf(listOf<Screen>()) }
      var drawerVisible by remember { mutableStateOf(false) }

      val goBack = {
        val dropped = backstack.last()
        forwardStack += dropped.screen
        backstack = backstack.dropLast(1)
        val destination = backstack.last().screen
        val showingForRecents = destination.toRecent()
        recents = listOf(showingForRecents) + (recents - showingForRecents)
      }
      // TODO Not super clear this is an ok way to handle this. How does one have a global
      // event trigger a recomposition?
      goBackLazyForKeys = goBack
      val goForward = {
        val destination = forwardStack.last()
        forwardStack = forwardStack.dropLast(1)
        backstack += destination.byIdentity()
        val showingForRecents = destination.toRecent()
        recents = listOf(showingForRecents) + (recents - showingForRecents)
      }
      goForwardForKeys = goForward
      val goTo: (Screen) -> Unit = { destination ->
        backstack += destination.byIdentity()
        forwardStack = emptyList()
        val showingForRecents = destination.toRecent()
        recents = listOf(showingForRecents) + (recents - showingForRecents)
      }
      val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))
      Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
          HeapGraphTopBar(
            backstack = backstack.map { it.screen },
            forwardStack = forwardStack,
            goBack = goBack,
            goForward = goForward,
            toggleDrawer = { drawerVisible = !drawerVisible })
        },
      ) {
        Row {
          HeapGraphDrawer(drawerVisible = drawerVisible, recents = recents, goTo = goTo)
          Backstack(backstack) { wrappedScreen ->
            when (val screen = wrappedScreen.screen) {
              is Home -> HomeScreen(loadedGraph, goTo)
              is HeapObjectTree -> HeapObjectTreeScreen(loadedGraph, pressedKeys, screen, goTo)
            }
          }
        }
      }
    }
  }
}
