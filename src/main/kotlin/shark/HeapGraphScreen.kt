package shark

import androidx.compose.desktop.Window
import androidx.compose.desktop.WindowEvents
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.concurrent.ExecutorService

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeapGraphScreen(graph: HeapGraph, ioExecutor: ExecutorService) {
  Box(
    modifier = Modifier.fillMaxSize()
      .background(color = Color(180, 180, 180))
      .padding(10.dp)
  ) {
    val state = rememberLazyListState()

    val classes = remember { graph.classes.toList().sortedBy { it.name } }

    LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state) {
      items(classes) { heapClass ->
        TextBox("Class ${heapClass.name} (${heapClass.instances.count()} instances)") {
          Window(title = "Class ${heapClass.name} - SharkApp") {
            ClassInstancesScreen(heapClass)
          }
        }
        Spacer(modifier = Modifier.height(5.dp))
      }
    }
    VerticalScrollbar(
      modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
      adapter = rememberScrollbarAdapter(
        scrollState = state,
        itemCount = classes.size,
        averageItemSize = 37.dp // TextBox height + Spacer height
      )
    )
  }
}

@Composable
fun TextBox(text: String = "Item", onClick: () -> Unit) {
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