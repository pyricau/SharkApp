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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import shark.HeapObject.HeapClass
import shark.Showing.ShowTree
import shark.Showing.Start

sealed class Showing {
  object Start : Showing() {
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
    val graph = loadingState.loadedGraph.value
    if (graph == null) {
      LoadingScreen(loadingState.file.name)
    } else {
      // TODO move
      val classesWithInstanceCounts = mutableMapOf<Long, Int>()
      classesWithInstanceCounts.putAll(graph.classes.map { it.objectId to 0 })
      graph.instances.forEach { instance ->
        classesWithInstanceCounts[instance.instanceClassId] =
          classesWithInstanceCounts[instance.instanceClassId]!! + 1
      }

      var recents by remember { mutableStateOf(listOf<Showing>(Start)) }
      var backstack by remember { mutableStateOf(listOf<Showing>()) }
      var frontstack by remember { mutableStateOf(listOf<Showing>()) }
      var moveForward by remember { mutableStateOf(true) }

      recents.take(2).forEachIndexed { index, showing ->
        key(showing) {
          ScreenTransition(visible = index == 0, moveForward) {
            HeapGraphScreen(
              graph,
              classesWithInstanceCounts,
              pressedKeys,
              showing,
              backstack.isNotEmpty(),
              recents = recents,
              goBack = {
                val showing = backstack.first()
                backstack = backstack.drop(1)
                recents = listOf(showing) + (recents - showing)
                moveForward = false
              },
              goTo = { destination ->
                backstack = listOf(recents.first()) + backstack
                recents = listOf(destination) + (recents - destination)
                moveForward = true
              }
            )
          }
        }
      }
    }
  }
}

@Composable
fun HeapGraphScreen(
  graph: HeapGraph,
  classesWithInstanceCounts: Map<Long, Int>,
  pressedKeys: PressedKeys,
  showing: Showing,
  canGoBack: Boolean,
  recents: List<Showing>,
  goBack: () -> Unit,
  goTo: (Showing) -> Unit
) {
  when (showing) {
    Start -> {
      Column {
        if (canGoBack) {
          WrapTextBox("Back") {
            goBack()
          }
        }
        Text(text = "Home")
        // TODO Don't use trailing lambda syntax
        WrapTextBox("Tree", onClick = {
          val showTree =
            ShowTree(
              "List of classes",
              graph.classes.map {
                val instanceCount = classesWithInstanceCounts[it.objectId]!!
                it.toTreeItem(instanceCount)
              }
                .toList()
            )
          goTo(showTree)
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
            goTo(Start)
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
            heapItem.expand(graph, classesWithInstanceCounts)
          },
          onDoubleClick = { selectedItems ->
            val showTree = ShowTree("Selected items", selectedItems)
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