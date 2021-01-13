package shark

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class ScreenNavigator<T>(firstScreen: T, val recentsEquals: (T, T) -> Boolean) {
  var recents by mutableStateOf(listOf(firstScreen.toRecent()))
    private set

  var backstackUnique by mutableStateOf(listOf(firstScreen.byIdentity()))
    private set

  var forwardStack by mutableStateOf(listOf<T>())
    private set

  val currentScreen: T
    get() = backstackUnique.last().screen

  val canGoBack: Boolean
    get() = backstackUnique.size > 1

  val canGoForward: Boolean
    get() = forwardStack.isNotEmpty()

  fun goBack() {
    if (!canGoBack) {
      return
    }
    val dropped = backstackUnique.last()
    forwardStack += dropped.screen
    backstackUnique = backstackUnique.dropLast(1)
    val destination = backstackUnique.last().screen
    updateRecents(destination)
  }

  fun goForward() {
    if (!canGoForward) {
      return
    }
    val destination = forwardStack.last()
    forwardStack = forwardStack.dropLast(1)
    backstackUnique += destination.byIdentity()
    updateRecents(destination)
  }

  fun goTo(destination: T) {
    backstackUnique += destination.byIdentity()
    forwardStack = emptyList()
    updateRecents(destination)
  }

  private fun updateRecents(screen: T) {
    recents =  listOf(screen.toRecent()) + recents.filter { !recentsEquals(screen, it.screen) }
  }
}

// Wraps screen so that the same screen can be at different parts of the backstack
// using different wrapper instances.
class ScreenUniqueByIdentity<T>(val screen: T)

private fun <T> T.byIdentity() = ScreenUniqueByIdentity(this)

private fun <T> T.toRecent() = RecentScreen(this)