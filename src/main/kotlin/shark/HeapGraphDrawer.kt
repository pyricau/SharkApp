package shark

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.AmbientContentAlpha
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Providers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import shark.Screen.Home

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun HeapGraphDrawer(
  drawerVisible: Boolean,
  recents: List<RecentScreen>,
  goTo: (Screen) -> Unit,
) {
  AnimatedVisibility(
    visible = drawerVisible,
    enter = expandHorizontally(),
    exit = shrinkHorizontally()
  ) {
    Row {
      Column(modifier = Modifier.width(48.dp * 5)) {
        val start = Home()
        ListItem(
          modifier = Modifier.clickable {
            goTo(start)
          },
          text = { Text(start.title) }
        )
          Divider()
          ListItem(text = {
            Text(
              text = "Recent screens",
              style = MaterialTheme.typography.body2.copy(
                color = MaterialTheme.typography.body2.color.copy(
                  alpha = ContentAlpha.medium
                )
              )
            )
          })
          val typography = MaterialTheme.typography

          Box {
            val scrollState = rememberLazyListState()
            LazyColumn(
              state = scrollState,
              modifier = Modifier.fillMaxSize(),
            ) {
              itemsIndexed(recents) { index, recent ->
                val border = if (index == 0) {
                  BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                } else {
                  null
                }
                Box(modifier = Modifier.padding(8.dp)) {
                  Surface(
                    shape = MaterialTheme.shapes.small,
                    border = border,
                    modifier = Modifier.clickable {
                      goTo(recent.screen)
                    }.fillMaxWidth()
                  ) {
                    Column(Modifier.padding(8.dp)) {
                      Providers(AmbientContentAlpha provides ContentAlpha.high) {
                        Text(
                          style = typography.subtitle1,
                          text = recent.screen.title
                        )
                      }
                      Providers(AmbientContentAlpha provides ContentAlpha.medium) {
                        Text(
                          modifier = Modifier.padding(top = 8.dp),
                          style = typography.body2,
                          text = recent.timeAgo()
                        )
                      }
                    }
                  }
                }
              }
            }
            VerticalScrollbar(
              rememberScrollbarAdapter(scrollState, recents.size, 48.dp),
              Modifier.align(Alignment.CenterEnd)
            )
          }
      }
      VerticalDivider()
    }
  }
}