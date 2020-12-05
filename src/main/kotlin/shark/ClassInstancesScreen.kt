package shark

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.maxLinesHeight
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import shark.HeapObject.HeapClass
import shark.ValueHolder.BooleanHolder
import shark.ValueHolder.ByteHolder
import shark.ValueHolder.CharHolder
import shark.ValueHolder.DoubleHolder
import shark.ValueHolder.FloatHolder
import shark.ValueHolder.IntHolder
import shark.ValueHolder.LongHolder
import shark.ValueHolder.ReferenceHolder
import shark.ValueHolder.ShortHolder
import java.util.concurrent.ExecutorService

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClassInstancesScreen(heapClass: HeapClass) {
  Box(
    modifier = Modifier.fillMaxSize()
      .background(color = Color(180, 180, 180))
      .padding(10.dp)
  ) {
    val state = rememberLazyListState()

    val classes = remember { heapClass.instances.toList() }

    LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state) {
      items(classes) { heapInstance ->
        Box(
          modifier = Modifier.fillMaxWidth()//.wrapContentHeight()
            .background(color = Color(0, 0, 0, 20))
            .padding(start = 10.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          val fields = heapInstance.readFields().toList().joinToString("\n") { field ->
            val value = when (val holder = field.value.holder) {
              is ReferenceHolder -> {
                if (holder.isNull) {
                  "null"
                } else {
                  field.value.asObject.toString()
                }
              }
              is BooleanHolder -> "${holder.value}"
              is CharHolder -> "${holder.value}"
              is FloatHolder -> "${holder.value}"
              is DoubleHolder -> "${holder.value}"
              is ByteHolder -> "${holder.value}"
              is ShortHolder -> "${holder.value}"
              is IntHolder -> "${holder.value}"
              is LongHolder -> "${holder.value}"
            }
            "  ${field.name} = $value"
          }
          Text(text = "$heapInstance\n\n$fields")
        }
        Spacer(modifier = Modifier.height(20.dp))
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
