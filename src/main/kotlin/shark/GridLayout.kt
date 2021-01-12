package shark

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

@Composable
fun GridLayout(
  modifier: Modifier = Modifier,
  scrollState: ScrollState = rememberScrollState(0f),
  content: @Composable() () -> Unit
) {
  Layout(
    modifier = modifier.verticalScroll(
      state = scrollState,
      enabled = true
    ),
    content = content
  ) { measurables, constraints ->
    if (measurables.isEmpty()) {
      return@Layout layout(0, 0) {}
    }
    val placeables = measurables.map { measurable ->
      val pl = measurable.measure(constraints)
      pl
    }


    val layoutWidth = constraints.maxWidth

    val itemWidth = placeables.maxByOrNull { it.width }!!.width
    val itemHeight = placeables.maxByOrNull { it.height }!!.height

    val minSpacing = 24.dp.toIntPx()

    val columnCount = floor((layoutWidth - minSpacing) / (itemWidth + minSpacing).toFloat()).toInt()

    // Not enough space.
    if (columnCount == 0) {
      return@Layout layout(0, 0) {}
    }

    val rowCount = ceil(placeables.size / columnCount.toFloat()).toInt()
    val horizontalSpacing =
      floor((layoutWidth - (columnCount * itemWidth)) / (columnCount + 1).toFloat()).toInt()
    val remainingPixels = layoutWidth - (horizontalSpacing + columnCount * (itemWidth + horizontalSpacing))
    val evenRemainingPixels = remainingPixels % 2 == 0
    val leftPadding = horizontalSpacing + if (evenRemainingPixels) remainingPixels / 2 else ((remainingPixels - 1) / 2) + 1
    val verticalSpacing = min(horizontalSpacing, 48.dp.toIntPx())

    val topBottomPadding = verticalSpacing

    val layoutHeight =
      2 * topBottomPadding + (rowCount * itemHeight) + (rowCount - 1) * verticalSpacing

    layout(layoutWidth, layoutHeight) {
      placeables.forEachIndexed { index, placeable ->
        val column = index % columnCount
        val row = (index - column) / columnCount

        placeable.placeRelative(
          x = leftPadding + (itemWidth + horizontalSpacing) * column,
          y = topBottomPadding + ((itemHeight + verticalSpacing) * row)
        )
      }
    }
  }
}