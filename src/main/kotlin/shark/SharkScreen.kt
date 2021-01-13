package shark

sealed class SharkScreen {

  abstract val title: String

  class Home : SharkScreen() {
    override val title = "Home"
  }

  class HeapObjectTree(override val title: String, val initialItems: List<TreeItem<HeapItem>>) :
    SharkScreen()
}