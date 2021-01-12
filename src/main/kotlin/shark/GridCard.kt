package shark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AmbientContentAlpha
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Providers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GridCard(
  title: @Composable () -> Unit,
  description: @Composable (() -> Unit)? = null,
  onOpen: (() -> Unit)? = null
) {
  val typography = MaterialTheme.typography

  val styledTitle = applyTextStyle(typography.subtitle1, ContentAlpha.high, title)!!

  val styledDescription = applyTextStyle(
    typography.body2,
    ContentAlpha.medium,
    description
  )

  val cardModifier = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier

  Card(
    modifier = cardModifier,
    elevation = 8.dp
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Box {
        styledTitle()
      }
      if (styledDescription != null) {
        Box(modifier = Modifier.padding(top = 8.dp)) {
          styledDescription()
        }
      }
      if (onOpen != null) {
        Box(modifier = Modifier.fillMaxHeight()) {
          Surface(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = Color.Transparent,
            contentColor = MaterialTheme.colors.primary,
          ) {
            ProvideTextStyle(value = MaterialTheme.typography.button) {
              Text(modifier = Modifier.padding(top = 16.dp), text = "Open")
            }
          }
        }
      }
    }
  }
}

fun applyTextStyle(
  textStyle: TextStyle,
  contentAlpha: Float,
  text: @Composable (() -> Unit)?
): @Composable (() -> Unit)? {
  if (text == null) return null
  return {
    Providers(AmbientContentAlpha provides contentAlpha) {
      ProvideTextStyle(textStyle, text)
    }
  }
}
