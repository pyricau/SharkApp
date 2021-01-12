package shark

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import shark.Screen.Home

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun HeapGraphDrawer(
  drawerVisible: Boolean,
  recents: List<ShowingWithUniqueTitle>,
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
        if (recents.size > 1) {
          Divider()
          ListItem(text = {
            Text(
              text = "Recents",
              style = MaterialTheme.typography.body2.copy(
                color = MaterialTheme.typography.body2.color.copy(
                  alpha = ContentAlpha.medium
                )
              )
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
                    goTo(item.screen)
                  },
                  text = { Text(item.screen.title) }
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
}