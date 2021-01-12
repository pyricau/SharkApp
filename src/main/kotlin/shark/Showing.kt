package shark

sealed class Showing {

  abstract val title: String

  class Start : Showing() {
    override val title = "Home"
  }

  class ShowTree(override val title: String, val initialItems: List<TreeItem<HeapItem>>) : Showing()
}