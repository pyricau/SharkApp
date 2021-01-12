package shark

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion
import androidx.compose.ui.unit.dp
import shark.Screen.HeapObjectTree

@Composable
fun HomeScreen(
  graph: LoadedGraph,
  goTo: (Screen) -> Unit
) {
  Column {
    val root = graph.dominating(0)!!
    // TODO Leverage theme instead?
    Surface(color = Color(red = 237, green = 237, blue = 237)) {
      Box {
        val scrollState = rememberScrollState(0f)
        GridLayout(itemWidth = 288.dp, scrollState = scrollState) {
          GridCard(
            title = { Text("Summary") },
            description = { Text("""
              Strongly reachable memory: ${root.retainedSize.toHumanReadableBytes()}
              Strongly reachable objects: ${root.retainedCount}
              Total objects: ${graph.objectCount}
              Total classes: ${graph.classCount}
              Total instances: ${graph.instanceCount}
              Total object arrays: ${graph.objectArrayCount}
              Total primitive arrays: ${graph.primitiveArrayCount}
            """.trimIndent())}
          )

          GridCard(
            title = { Text("All classes") },
            onOpen = {
              goTo(HeapObjectTree(
                "All classes",
                graph.classes.map {
                  it.toTreeItem(graph.instanceCount(it))
                }
                  .toList()
              ))
            }
          )
          GridCard(
            title = { Text("All objects") },
            onOpen = {
              goTo(
                HeapObjectTree(
                  "All objects",
                  graph.objects.map { it.toTreeItem(graph) }.toList()
                )
              )
            }
          )
          GridCard(
            title = { Text("Reachable leaking objects") },
            description = {
              Text(buildAnnotatedString {
                append("Objects that were inspected and determined to be ")
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append("leaking")
                pop()
                append(" and ")
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append("strongly reachable")
                pop()
                append(".")
              })
            },
            onOpen = {
              goTo(HeapObjectTree(
                "Reachable leaking objects",
                graph.leakingObjectIds.map { graph.findObjectById(it).toTreeItem(graph) }
                  .toList()
              ))
            }
          )
          GridCard(
            title = { Text("Unreachable leaking objects") },
            description = {
              Text(buildAnnotatedString {
                append("Objects that were inspected and determined to be ")
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append("leaking")
                pop()
                append(" but ")
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append("aren't strongly reachable")
                pop()
                append(".")
              })
            },
            onOpen = {
              goTo(HeapObjectTree(
                "Unreachable leaking objects",
                graph.unreachableLeakingObjectIds.map {
                  graph.findObjectById(it).toTreeItem(graph)
                }
                  .toList()
              ))
            }
          )

          GridCard(
            title = { Text("Dominators") },
            description = {
              Text(
                "Objects that dominate other objects, sorted by highest retained memory." +
                  "An object A dominates an object B if every path from the GC roots to B must go" +
                  " through A."
              )
            },
            onOpen = {
              goTo(HeapObjectTree(
                "Dominators",
                graph.dominatorsSortedRetained().filter { it != 0L }
                  .map { graph.findObjectById(it).toTreeItem(graph) }
              ))
            }
          )
        }
        VerticalScrollbar(
          rememberScrollbarAdapter(scrollState),
          Modifier.align(Alignment.CenterEnd)
        )
      }
    }

  }
}