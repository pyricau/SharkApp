package shark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AmbientContentColor
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.AmbientDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shark.Screen.HeapObjectTree
import kotlin.math.max

@Composable
fun HeapObjectTreeScreen(
  graph: LoadedGraph,
  pressedKeys: PressedKeys,
  showing: HeapObjectTree,
  goTo: (Screen) -> Unit
) {
  Column {
    var filter by remember { mutableStateOf("") }

    OutlinedTextField(
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      value = filter,
      onValueChange = { filter = it },
      label = {
        Text("Class name filter")
      },
    )

    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
      TreeView(
        pressedKeys = pressedKeys,
        rootItems = showing.initialItems,
        expandItem = { heapItem ->
          heapItem.expand(graph)
        },
        onDoubleClick = { selectedItems ->
          val showTree = HeapObjectTree(
            "Selected items",
            selectedItems.map { if (it.expended) it.copy(expended = false) else it })
          goTo(showTree)
        },
        filter
      ) { treeItem ->
        val active = remember { mutableStateOf(false) }
        val fontSize = 14.sp
        with(AmbientDensity.current) {
          val toggleIconHeight = 24.dp.toIntPx()
          val lineHeightPx = max((fontSize.toDp() * 1.5f).toIntPx(), toggleIconHeight)
          Text(
            text = treeItem.name,
            color = if (active.value) AmbientContentColor.current.copy(alpha = 0.60f) else AmbientContentColor.current,
            modifier = Modifier.layout { measurable, constraints ->
              val placeable = measurable.measure(constraints)
              check(placeable[FirstBaseline] != AlignmentLine.Unspecified)
              val placeableY = (lineHeightPx - placeable[FirstBaseline]) / 2
              layout(placeable.width, lineHeightPx) {
                // Where the composable gets placed
                placeable.placeRelative(0, placeableY)
              }
            }
              .clipToBounds()
              .pointerMoveFilter(
                onEnter = {
                  active.value = true
                  true
                },
                onExit = {
                  active.value = false
                  true
                }
              ),
            softWrap = true,
            fontSize = fontSize,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
          )
          // }
        }
      }
    }
  }
}