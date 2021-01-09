package shark

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.AmbientContentColor
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.gesture.DoubleTapTimeout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.ExperimentalKeyInput
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.platform.AmbientDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.inMilliseconds
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import shark.ItemClickEvent.ROW_CLICK
import shark.ItemClickEvent.TOGGLE_EXPAND
import kotlin.math.max
import kotlin.math.min
import java.time.Duration
import java.time.Instant
import java.util.LinkedList

data class TreeItem<T>(
  val data: T,
  val expandable: Boolean,
  val expended: Boolean = false,
  val name: String,
  // TODO Add type for styling etc.
  val selectable: Boolean
)

data class TreeNode<T>(
  val item: TreeItem<T>,
  val selected: Boolean,
  val level: Int,
  val visible: Boolean,
  val id: Int = idCount++
) {

  companion object {
    private var idCount: Int = 0
  }
}

@OptIn(ExperimentalKeyInput::class)
@Composable
fun <T> TreeView(
  pressedKeys: PressedKeys,
  rootItems: List<TreeItem<T>>,
  expandItem: (T) -> List<TreeItem<T>>,
  onDoubleClick: (List<TreeItem<T>>) -> Unit = {},
  filter: String
) {
  val scope = rememberCoroutineScope()
  var delayedClick by remember { mutableStateOf<Job?>(null) }

  var tree: List<TreeNode<T>> by remember(rootItems) {
    mutableStateOf(rootItems.map { TreeNode(it, false, 0, true) })
  }

  val filteredTree = mutableListOf<TreeNode<T>>()
  var keep = true

  for (node in tree) {
    if (node.level == 0) {
      keep = filter in node.item.name
    }
    if (keep) {
      filteredTree += node
    }
  }

  var lastRowClick by remember { mutableStateOf<Pair<Int, Instant>?>(null) }

  // selected order, ordered from first selected to last selected (in historical order)
  var selectedNodes: List<TreeNode<T>> by remember(rootItems) {
    mutableStateOf(emptyList())
  }

  var selectionViaMeta by remember { mutableStateOf(false) }

  TreeView(filteredTree) { filteredClickedItemIndex, itemClickEvent ->

    val filteredClickedNode = filteredTree[filteredClickedItemIndex]
    val clickedItemIndex = tree.indexOfFirst { it.id == filteredClickedNode.id }
    val node = tree[clickedItemIndex]

    when (itemClickEvent) {
      TOGGLE_EXPAND -> {
        lastRowClick = null
        tree = toggleExpandNode(tree, clickedItemIndex, expandItem)
        scope.launch {
          delay(100)
          tree = tree.map {
            it.copy(visible = true)
          }
        }
      }
      ROW_CLICK -> {
        val clickInstant = Instant.now()
        if (pressedKeys.meta) {
          if (node.item.selectable) {
            tree = tree.toggleSelected(node)
            if (node.selected) {
              selectedNodes = selectedNodes.filter { it.id != node.id }
            } else {
              selectedNodes += node
            }
            selectionViaMeta = true
          }
        } else if (pressedKeys.shift) {
          if (selectedNodes.isEmpty()) {
            tree = tree.mapIndexed { index, treeNode ->
              if (index <= clickedItemIndex && treeNode.item.selectable) treeNode.copy(selected = true) else treeNode
            }
            selectedNodes = tree.take(clickedItemIndex + 1).filter { it.item.selectable }
            // } else if (selectionViaMeta) {

            // TODO This isn't fully implemented.
            // There should be a way to alternate cmd and shift selections to select
            // several blocks in a list. Need to figure out rules.
          } else {
            val startOfRange = selectedNodes.first()
            val startOfRangeIndex = tree.indexOfFirst { it.id == startOfRange.id }
            val rangeMin = min(startOfRangeIndex, clickedItemIndex)
            val rangeMax = max(startOfRangeIndex, clickedItemIndex)
            tree = tree.mapIndexed { index, treeNode ->
              val selected = index in rangeMin..rangeMax && treeNode.item.selectable
              if (treeNode.selected != selected) {
                treeNode.copy(selected = selected)
              } else {
                treeNode
              }
            }
            selectedNodes = tree.filter { it.selected }
            if (clickedItemIndex < startOfRangeIndex) {
              selectedNodes = selectedNodes.reversed()
            }
          }
          selectionViaMeta = false
        } else {
          if (lastRowClick != null && lastRowClick!!.first == clickedItemIndex &&
            Duration.between(
              lastRowClick!!.second,
              clickInstant
            ).toMillis() <= DoubleTapTimeout.inMilliseconds()
          ) {
            // If there are multiple rows selected AND this is a click on a selected row:
            // A single click would mean "select only this row now, unselect other rows"
            // A double click would mean "open all selected rows"
            // For that reason, we must delay handling the single click by the double click timeout
            delayedClick?.cancel()

            if (node.item.selectable) {
              onDoubleClick(tree.filter { it.selected }.map {
                it.item
              })
            }
          } else {
            if (node.item.selectable) {
              if (node.selected) {
                delayedClick = scope.launch {
                  delay(DoubleTapTimeout.inMilliseconds())
                  tree = tree.selectOnly(node)
                  selectedNodes = listOf(node)
                  selectionViaMeta = false
                }
              } else {
                tree = tree.selectOnly(node)
                selectedNodes = listOf(node)
                selectionViaMeta = false
              }
            }
            if (node.item.expandable) {
              tree = toggleExpandNode(tree, clickedItemIndex, expandItem)
              scope.launch {
                delay(100)
                tree = tree.map {
                  it.copy(visible = true)
                }
              }
            }
          }
        }

        // TODO Ctrl + Click = right click
        lastRowClick = clickedItemIndex to clickInstant
      }
    }
  }
}

private fun <T> toggleExpandNode(
  tree: List<TreeNode<T>>,
  index: Int,
  expandItem: (T) -> List<TreeItem<T>>,
): List<TreeNode<T>> {
  val node = tree[index]
  return if (node.item.expended) {
    val updatedNode = node.copy(item = node.item.copy(expended = false))
    // TODO consider trying using kotlinx immutable library instead.
    // https://github.com/Kotlin/kotlinx.collections.immutable
    val preList = tree.take(index)
    val afterNode = tree.drop(index + 1)
    val postList = afterNode.dropWhile { it.level > node.level }
    preList + updatedNode + postList
  } else {
    expandNode(tree, index, expandItem)
  }
}

private fun <T> expandNode(
  tree: List<TreeNode<T>>,
  index: Int,
  expandItem: (T) -> List<TreeItem<T>>,
): List<TreeNode<T>> {
  val node = tree[index]
  val toggledItem = node.item
  val updatedNode = node.copy(item = toggledItem.copy(expended = true))
  val preList = tree.take(index)
  val postList = tree.drop(index + 1)
  val expandedList = mutableListOf<TreeNode<T>>()
  val expandingNodes = LinkedList<TreeNode<T>>()
  expandingNodes += updatedNode
  while (expandingNodes.isNotEmpty()) {
    val expandingNode = expandingNodes.poll()
    expandedList += expandingNode
    if (expandingNode.item.expandable && expandingNode.item.expended) {
      expandingNodes.addAll(0, expandItem(expandingNode.item.data).map {
        TreeNode(
          it,
          selected = false,
          level = expandingNode.level + 1,
          visible = false
        )
      })
    }
  }

  return preList + expandedList + postList
}

private fun <T> List<TreeNode<T>>.selectOnly(node: TreeNode<T>) = map {
  when {
    it.id == node.id -> {
      it.copy(selected = true)
    }
    it.selected -> {
      it.copy(selected = false)
    }
    else -> {
      it
    }
  }
}

private fun <T> List<TreeNode<T>>.toggleSelected(node: TreeNode<T>) = map {
  if (it.id == node.id) {
    it.copy(selected = !it.selected)
  } else it
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun <T> TreeView(
  tree: List<TreeNode<T>>,
  onItemClickEvent: (Int, ItemClickEvent) -> Unit
) {
  Surface(
    modifier = Modifier.fillMaxSize()
  ) {
    with(AmbientDensity.current) {
      Box {
        val scrollState = rememberLazyListState()
        val fontSize = 14.sp
        val lineHeight = fontSize.toDp() * 1.5f

        LazyColumn(
          state = scrollState,
          modifier = Modifier.fillMaxSize(),
        ) {
          itemsIndexed(tree) { index, node ->
            key(node.id) {
              AnimatedVisibility(
                node.visible,
                enter = expandVertically(),
                exit = shrinkVertically()
              ) {
                TreeItemView(fontSize, lineHeight, node, index) {
                  onItemClickEvent(index, it)
                }
              }
            }
          }
        }

        VerticalScrollbar(
          rememberScrollbarAdapter(scrollState, tree.size, lineHeight),
          Modifier.align(Alignment.CenterEnd)
        )
      }
    }
  }
}

enum class ItemClickEvent {
  TOGGLE_EXPAND,
  ROW_CLICK,
}

@Composable
private fun <T> TreeItemView(
  fontSize: TextUnit,
  height: Dp,
  node: TreeNode<T>,
  index: Int,
  onItemClickEvent: (ItemClickEvent) -> Unit
) {
  val defaultColor = MaterialTheme.colors.surface
  val grey = Color(red = 240, green = 240, blue = 240)

  Surface(color = if (node.selected) MaterialTheme.colors.secondary else if (index % 2 == 0) defaultColor else grey) {
    Row(
      modifier = Modifier
        .wrapContentHeight()
        .clickable(
          onClick = {
            onItemClickEvent(ROW_CLICK)
          },
        )
        .padding(start = 24.dp * node.level)
        .height(height)
        .fillMaxWidth()
    ) {
      val active = remember { mutableStateOf(false) }

      TreeItemIcon(Modifier.align(Alignment.CenterVertically), node, onItemClickEvent)
      Text(
        text = node.item.name,
        color = if (active.value) AmbientContentColor.current.copy(alpha = 0.60f) else AmbientContentColor.current,
        modifier = Modifier
          .align(Alignment.CenterVertically)
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

@Composable
private fun <T> TreeItemIcon(
  modifier: Modifier,
  node: TreeNode<T>,
  onItemClickEvent: (ItemClickEvent) -> Unit
) {
  val clickableIfExpandable = if (node.item.expandable) {
    Modifier.clickable {
      onItemClickEvent(TOGGLE_EXPAND)
    }
  } else Modifier
  Box(
    modifier
      .size(24.dp)
      .then(clickableIfExpandable)
      .padding(4.dp)
  ) {
    if (node.item.expandable) {
      if (node.item.expended) {
        Icon(Icons.Default.KeyboardArrowDown, tint = AmbientContentColor.current)
      } else {
        Icon(Icons.Default.KeyboardArrowRight, tint = AmbientContentColor.current)
      }
    }
  }
}
