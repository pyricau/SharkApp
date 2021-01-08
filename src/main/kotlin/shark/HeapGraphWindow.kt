package shark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import shark.Showing.ShowTree
import shark.Showing.Start

sealed class Showing {

  class Start : Showing() {
    override fun toString(): String {
      return "Home"
    }
  }

  class ShowTree(val title: String, val initialItems: List<TreeItem<HeapItem>>) : Showing() {
    override fun toString() = title
  }
}

@Composable
fun HeapGraphWindow(loadingState: HeapDumpLoadingState, pressedKeys: PressedKeys) {
  MaterialTheme {
    val loadedGraph = loadingState.loadedGraph.value
    if (loadedGraph == null) {
      LoadingScreen(loadingState.file.name)
    } else {

      // TODO Backstack doesn't want to have duplicate entries (ie that are equal) but recents
      // allow that to happen. Need to see what's what.

      var recents by remember { mutableStateOf(listOf<Showing>(Start())) }
      var backstack by remember { mutableStateOf(listOf<Showing>(Start())) }

      Backstack(backstack) { screen ->
        HeapGraphScreen(
          loadedGraph,
          pressedKeys,
          screen,
          canGoBack = backstack.size > 1,
          recents = recents,
          goBack = {
            val showing = backstack.last()
            backstack = backstack.dropLast(1)
            recents = listOf(showing) + (recents - showing)
          },
          goTo = { destination ->
            backstack += destination
            recents = listOf(destination) + (recents - destination)
          }
        )
      }
    }
  }
}

@Composable
fun HeapGraphScreen(
  graph: LoadedGraph,
  pressedKeys: PressedKeys,
  showing: Showing,
  canGoBack: Boolean,
  recents: List<Showing>,
  goBack: () -> Unit,
  goTo: (Showing) -> Unit
) {
  when (showing) {
    is Start -> {
      Column {
        if (canGoBack) {
          WrapTextBox("Back") {
            goBack()
          }
        }
        Text(text = "Home")
        WrapTextBox("Tree", onClick = {
          val showTree =
            ShowTree(
              "List of classes",
              graph.classes.map {
                it.toTreeItem(graph.instanceCount(it))
              }
                .toList()
            )
          goTo(showTree)
        })
        WrapTextBox("Leaks", onClick = {
          goTo(ShowTree(
            "Leaking objects",
            graph.leakingObjectIds.map { graph.findObjectById(it).toTreeItem(graph) }.toList()
          ))
        })
        WrapTextBox("All objects", onClick = {
          goTo(ShowTree(
            "All objects",
            graph.objects.map { it.toTreeItem(graph) }.toList()
          ))
        })
        WrapTextBox("Dominators", onClick = {
          goTo(ShowTree(
            "Dominators",
            graph.dominatorsSortedRetained().filter { it != 0L }.map { graph.findObjectById(it).toTreeItem(graph) }
          ))
        })
        Text(text = "Recents")
        for (item in recents.drop(1)) {
          TextBox("$item", onClick = {
            goTo(item)
          })
        }
      }
    }
    is ShowTree -> {
      Column {
        Row {
          WrapTextBox("Home") {
            goTo(Start())
          }
          if (canGoBack) {
            WrapTextBox("Back") {
              goBack()
            }
          }
        }
        Text(text = "$showing")

        TreeView(
          pressedKeys = pressedKeys,
          rootItems = showing.initialItems,
          expandItem = { heapItem ->
            heapItem.expand(graph)
          },
          onDoubleClick = { selectedItems ->
            val showTree = ShowTree(
              "Selected items",
              selectedItems.map { if (it.expended) it.copy(expended = false) else it })
            goTo(showTree)
          }
        )
      }
    }
  }
}

@Composable
fun TextBox(text: String, onClick: () -> Unit) {
  Button(onClick = onClick) {
    Box(
      modifier = Modifier.height(32.dp)
        .fillMaxWidth()
        .background(color = Color(0, 0, 0, 20))
        .padding(start = 10.dp),
      contentAlignment = Alignment.CenterStart
    ) {
      Text(text = text)
    }
  }
}

@Composable
fun WrapTextBox(text: String, onClick: () -> Unit) {
  Button(onClick = onClick) {
    Box(
      modifier = Modifier.height(32.dp)
        .background(color = Color(0, 0, 0, 20))
        .padding(start = 10.dp),
      contentAlignment = Alignment.CenterStart
    ) {
      Text(text = text)
    }
  }
}