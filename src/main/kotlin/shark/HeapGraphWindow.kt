package shark

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.preferredHeight
import androidx.compose.foundation.layout.preferredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.AmbientContentAlpha
import androidx.compose.material.AmbientContentColor
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.ExperimentalKeyInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.plus
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.AmbientDensity
import androidx.compose.ui.platform.Keyboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shark.Showing.ShowTree
import shark.Showing.Start
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT
import kotlin.math.max

@OptIn(
  ExperimentalKeyInput::class,
  ExperimentalFoundationApi::class,
  ExperimentalAnimationApi::class
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

      class ShowingWithUniqueTitle(val showing: Showing) {
        override fun hashCode(): Int {
          return showing.title.hashCode()
        }

        override fun equals(other: Any?): Boolean {
          return other is ShowingWithUniqueTitle && showing.title == other.showing.title
        }
      }

      var recents by remember { mutableStateOf(listOf(ShowingWithUniqueTitle(Start()))) }
      var backstack by remember { mutableStateOf(listOf<Showing>(Start())) }
      var forwardStack by remember { mutableStateOf(listOf<Showing>()) }

      var drawerVisible by remember { mutableStateOf(false) }

      val goBack = {
        val dropped = backstack.last()
        forwardStack += dropped
        backstack = backstack.dropLast(1)
        val destination = backstack.last()
        val showingForRecents = ShowingWithUniqueTitle(destination)
        recents = listOf(showingForRecents) + (recents - showingForRecents)
      }
      // TODO Not super clear this is an ok way to handle this. How does one have a global
      // event trigger a recomposition?
      goBackLazyForKeys = goBack
      val goForward = {
        val destination = forwardStack.last()
        forwardStack = forwardStack.dropLast(1)
        backstack += destination
        val showingForRecents = ShowingWithUniqueTitle(destination)
        recents = listOf(showingForRecents) + (recents - showingForRecents)
      }
      goForwardForKeys = goForward
      val goTo: (Showing) -> Unit = { destination ->
        backstack = (backstack - destination) + destination
        forwardStack = emptyList()
        val showingForRecents = ShowingWithUniqueTitle(destination)
        recents = listOf(showingForRecents) + (recents - showingForRecents)
      }
      val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))
      Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
          TopAppBar(
            title = { Text(backstack.last().title) },
            navigationIcon = {
              IconButton(onClick = { drawerVisible = !drawerVisible }) {
                Icon(Icons.Default.Menu)
              }
            },
            actions = {
              IconButton(onClick = { goBack() }, enabled = backstack.size > 1) {
                Icon(
                  Icons.Default.ArrowBack,
                  tint = if (backstack.size > 1) AmbientContentColor.current else AmbientContentColor.current.copy(
                    alpha = AmbientContentAlpha.current
                  )
                )
              }
              IconButton(onClick = { goForward() }, enabled = forwardStack.isNotEmpty()) {
                Icon(
                  Icons.Default.ArrowForward,
                  tint = if (forwardStack.isNotEmpty()) AmbientContentColor.current else AmbientContentColor.current.copy(
                    alpha = AmbientContentAlpha.current
                  )
                )
              }
            }
          )
        },
      ) {
        Row {
          AnimatedVisibility(visible = drawerVisible) {
            Row {
              Column(modifier = Modifier.width(48.dp * 5)) {
                val start = Start()
                ListItem(
                  modifier = Modifier.clickable {
                    goTo(start)
                  },
                  text = { Text(start.title) }
                )
                if (recents.size > 1) {
                  Divider()
                  ListItem(text = {
                    Text(
                      text = "Recents",
                      style = MaterialTheme.typography.body2.copy(color = MaterialTheme.typography.body2.color.copy(alpha = ContentAlpha.medium))
                    )
                  })
                  Box {
                    val scrollState = rememberLazyListState()
                    LazyColumn(
                      state = scrollState,
                      modifier = Modifier.fillMaxSize(),
                    ) {
                      items(recents.drop(1)) { item ->
                        ListItem(
                          modifier = Modifier.clickable {
                            goTo(item.showing)
                          },
                          text = { Text(item.showing.title) }
                        )
                      }
                    }
                    VerticalScrollbar(
                      rememberScrollbarAdapter(scrollState, recents.size, 48.dp),
                      Modifier.align(Alignment.CenterEnd)
                    )
                  }
                }
              }
              VerticalDivider()
            }
          }
          Backstack(backstack) { screen ->
            HeapGraphScreen(
              loadedGraph,
              pressedKeys,
              screen,
              goTo = goTo
            )
          }
        }
      }
    }
  }
}

@Composable
fun HeapGraphScreen(
  graph: LoadedGraph,
  pressedKeys: PressedKeys,
  showing: Showing,
  goTo: (Showing) -> Unit
) {
  when (showing) {
    is Start -> {
      Column {
        val root = graph.dominating(0)!!
        // TODO Leverage theme instead.
        Surface(color = Color(red = 237, green = 237, blue = 237)) {
          Box {
            val scrollState = rememberScrollState(0f)
            GridLayout {
              GridCard(title = "Summary", subtitle = "Total retained: ${root.retainedSize.toHumanReadableBytes()} (${root.retainedCount} objects)")

              GridCard(title = "All classes", subtitle = "", onOpen = {
                goTo(ShowTree(
                  "All classes",
                  graph.classes.map {
                    it.toTreeItem(graph.instanceCount(it))
                  }
                    .toList()
                ))
              })
              GridCard(title = "All objects", subtitle = null, onOpen = {goTo(
                ShowTree(
                  "All objects",
                  graph.objects.map { it.toTreeItem(graph) }.toList()
                )
              )})
              GridCard(
                title = "Reachable leaking objects",
                subtitle = "Objects that were inspected and determined to be leaking and strongly reachable.",
                onOpen = {
                  goTo(ShowTree(
                    "Reachable leaking objects",
                    graph.leakingObjectIds.map { graph.findObjectById(it).toTreeItem(graph) }
                      .toList()
                  ))
                }
              )
              GridCard(
                title = "Unreachable leaking objects",
                subtitle = "Objects that were inspected and determined to be leaking but aren't strongly reachable.",
                onOpen = {
                  goTo(ShowTree(
                    "Unreachable leaking objects",
                    graph.unreachableLeakingObjectIds.map {
                      graph.findObjectById(it).toTreeItem(graph)
                    }
                      .toList()
                  ))
                }
              )

              GridCard(title = "Dominators", subtitle = null, onOpen = {
                goTo(ShowTree(
                  "Dominators",
                  graph.dominatorsSortedRetained().filter { it != 0L }
                    .map { graph.findObjectById(it).toTreeItem(graph) }
                ))
              })
            }
            VerticalScrollbar(
              rememberScrollbarAdapter(scrollState),
              Modifier.align(Alignment.CenterEnd)
            )
          }
        }

      }
    }
    is ShowTree -> {
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
              val showTree = ShowTree(
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
  }
}

@Composable
fun GridCard(
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String? = null,
  onOpen: (() -> Unit)? = null
) {
  Card(
    modifier = modifier.preferredWidth(288.dp),
    elevation = 8.dp
  ) {
    Column {
      ListItem(
        text = {
          Text(title)
        },
        secondaryText = if (subtitle != null) {{
          Text(subtitle)
        }} else null
      )
      if (onOpen != null) {
        OutlinedButton(modifier = Modifier.padding(8.dp), onClick = { onOpen() }) {
          Text("Open")
        }
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
