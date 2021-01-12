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
import shark.HeapItem.HeapClassItem
import shark.HeapItem.HeapInstanceItem
import shark.HeapItem.HeapObjectArrayItem
import shark.HeapItem.HeapPrimitiveArrayItem
import shark.HeapObject.HeapClass
import shark.HeapObject.HeapInstance
import shark.HeapObject.HeapObjectArray
import shark.HeapObject.HeapPrimitiveArray
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
          val title = if (selectedItems.size == 1) {
            val selectedItem = selectedItems.first()
            when (val data = selectedItem.data) {
              is HeapClassItem -> "Class ${(graph.findObjectById(data.objectId) as HeapClass).simpleName}"
              is HeapInstanceItem -> {
                val instance = graph.findObjectById(data.objectId) as HeapInstance
                "${instance.instanceClassSimpleName}@${instance.objectId}"
              }
              is HeapPrimitiveArrayItem -> {
                val array = graph.findObjectById(data.objectId) as HeapPrimitiveArray
                "${array.primitiveType.name.toLowerCase()}[${array.recordSize}]@${array.objectId}"
              }
              is HeapObjectArrayItem -> {
                val array = graph.findObjectById(data.objectId) as HeapObjectArray
                "${array.arrayClassSimpleName.substringBeforeLast("[]")}[${array.recordSize}]@${array.objectId}"
              }
              else -> error("${selectedItem.data::class.java} not expected to be selectable")
            }
          } else {
            val itemTypes = selectedItems.map { it.data::class.java }.toSet()
            if (itemTypes.size == 1) {
              val itemType = itemTypes.first()
              when {
                itemType == HeapClassItem::class.java -> "${selectedItems.size} classes"
                itemType == HeapInstanceItem::class.java -> {
                  val instanceClasses =
                    selectedItems.map { graph.findObjectById((it.data as HeapInstanceItem).objectId).asInstance!!.instanceClassId }
                      .toSet()
                  if (instanceClasses.size == 1) {
                    val className =
                      graph.findObjectById(instanceClasses.first()).asClass!!.simpleName
                    "${selectedItems.size} $className instances"
                  } else {
                    "${selectedItems.size} instances"
                  }
                }
                itemType == HeapPrimitiveArrayItem::class.java -> {
                  val types =
                    selectedItems.map { graph.findObjectById((it.data as HeapPrimitiveArrayItem).objectId).asPrimitiveArray!!.primitiveType }
                      .toSet()
                  if (types.size == 1) {
                    "${selectedItems.size} ${types.first().name.toLowerCase()} arrays"
                  } else {
                    "${selectedItems.size} primitive arrays"
                  }
                }
                itemType == HeapObjectArrayItem::class.java -> {
                  val classes =
                    selectedItems.map { graph.findObjectById((it.data as HeapObjectArrayItem).objectId).asObjectArray!!.arrayClass.objectId }
                      .toSet()
                  if (classes.size == 1) {
                    val className =
                      graph.findObjectById(classes.first()).asClass!!.simpleName
                    "${selectedItems.size} $className arrays"
                  } else {
                    "${selectedItems.size} object arrays"
                  }
                }
                else -> error("$itemType not expected to be selectable")
              }
            } else {
              "${selectedItems.size} objects"
            }
          }

          val showTree = HeapObjectTree(
            title,
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
        }
      }
    }
  }
}