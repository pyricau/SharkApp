@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package shark

import shark.HeapObject.HeapClass
import shark.HprofHeapGraph.Companion.openHeapGraph
import shark.internal.AndroidNativeSizeMapper
import shark.internal.ObjectDominators.DominatorNode
import shark.internal.PathFinder
import shark.internal.ShallowSizeCalculator
import shark.internal.hppc.LongLongScatterMap
import shark.internal.hppc.LongLongScatterMap.ForEachCallback
import java.io.File

class LoadedGraph private constructor(
  private val graph: CloseableHeapGraph,
  private val classesWithInstanceCounts: Map<Long, Int>,
  private val dominatingTree: Map<Long, DominatorNode>,
  val leakingObjectIds: Set<Long>,
  val objectInspectors: List<ObjectInspector>,
  val computeSize: (Long) -> Int,
  private val dominated: LongLongScatterMap
) : CloseableHeapGraph by graph {

  fun findClassById(classObjectId: Long): Pair<HeapClass, Int> {
    val heapClass = graph.findObjectById(classObjectId) as HeapClass
    return heapClass to instanceCount(heapClass)
  }

  fun instanceCount(heapClass: HeapClass): Int {
    return classesWithInstanceCounts.getValue(heapClass.objectId)
  }

  fun instanceCount(classObjectId: Long): Int {
    return classesWithInstanceCounts.getValue(classObjectId)
  }

  fun dominatorsSortedRetained(): List<Long> {
    return dominatingTree.entries.sortedBy { -it.value.retainedSize }.map { it.key }
  }

  fun dominating(dominatorObjectId: Long): Dominating? {
    return dominatingTree[dominatorObjectId]?.let {
      Dominating(
        shallowSize = it.shallowSize,
        retainedSize = it.retainedSize,
        retainedCount = it.retainedCount,
        dominatedObjectIds = it.dominatedObjectIds
      )
    }
  }

  fun dominator(objectId: Long): Long {
    val slot = dominated.getSlot(objectId)
    if (slot == -1) {
      return ValueHolder.NULL_REFERENCE
    }
    return dominated.getSlotValue(slot)
  }

  companion object {
    fun load(file: File): LoadedGraph {
      val fileSourceProvider = FileSourceProvider(file)
      val wrapper = object : DualSourceProvider by fileSourceProvider {
        override fun openRandomAccessSource(): RandomAccessSource {
          val realSource = fileSourceProvider.openRandomAccessSource()
          return object : RandomAccessSource by realSource {
            override fun read(sink: okio.Buffer, position: Long, byteCount: Long): Long {
              // println("IO from thread ${Thread.currentThread().name}")
              return realSource.read(sink, position, byteCount)
            }
          }
        }
      }
      val graph = wrapper.openHeapGraph()
      val classesWithInstanceCounts = mutableMapOf<Long, Int>()
      classesWithInstanceCounts.putAll(graph.classes.map { it.objectId to 0 })
      graph.instances.forEach { instance ->
        classesWithInstanceCounts[instance.instanceClassId] =
          classesWithInstanceCounts[instance.instanceClassId]!! + 1
      }

      val leakingObjectFinder = FilteringLeakingObjectFinder(
        AndroidObjectInspectors.appLeakingObjectFilters
      )

      val leakingObjectIds = leakingObjectFinder.findLeakingObjectIds(graph)

      val objectInspectors = AndroidObjectInspectors.appDefaults

      val ignoredRefs =
        AndroidReferenceMatchers.appDefaults.filterIsInstance(IgnoredReferenceMatcher::class.java)

      val nativeSizeMapper = AndroidNativeSizeMapper(graph)
      val nativeSizes = nativeSizeMapper.mapNativeSizes()
      val shallowSizeCalculator = ShallowSizeCalculator(graph)

      val computeSize: (Long) -> Int = { objectId ->
        val nativeSize = nativeSizes[objectId] ?: 0
        val shallowSize = shallowSizeCalculator.computeShallowSize(objectId)
        nativeSize + shallowSize
      }

      val pathFinder = PathFinder(
        graph,
        OnAnalysisProgressListener.NO_OP, ignoredRefs
      )
      val result = pathFinder.findPathsFromGcRoots(setOf(), true)
      val dominatorTree = result.dominatorTree!!

      val dominated: LongLongScatterMap =
        dominatorTree::class.java.getDeclaredField("dominated").apply { isAccessible = true }
          .get(dominatorTree) as LongLongScatterMap

      val dominatedCopy = LongLongScatterMap(dominated.size)
      dominated.forEach(object : ForEachCallback {
        override fun onEntry(
          key: Long,
          value: Long
        ) {
          dominatedCopy.set(key, value)
        }
      })

      // destructive operation
      val dominatingTree = dominatorTree.buildFullDominatorTree(computeSize)

      return LoadedGraph(
        graph,
        classesWithInstanceCounts,
        dominatingTree,
        leakingObjectIds,
        objectInspectors,
        computeSize,
        dominatedCopy
      )
    }
  }
}

class Dominating(
  val shallowSize: Int,
  val retainedSize: Int,
  val retainedCount: Int,
  val dominatedObjectIds: List<Long>
)