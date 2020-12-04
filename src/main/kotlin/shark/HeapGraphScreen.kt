package shark

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import java.util.concurrent.ExecutorService

@Composable
fun HeapGraphScreen(graph: HeapGraph, ioExecutor: ExecutorService) {
  Text("Loaded ${graph.instanceCount}")
}