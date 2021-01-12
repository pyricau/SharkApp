package shark

sealed class Screen {

  abstract val title: String

  class Home : Screen() {
    override val title = "Home"
  }

  class HeapObjectTree(override val title: String, val initialItems: List<TreeItem<HeapItem>>) :
    Screen()
}