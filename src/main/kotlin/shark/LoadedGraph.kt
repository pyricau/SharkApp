@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package shark

import kotlinx.coroutines.suspendCancellableCoroutine
import shark.HeapObject.HeapClass
import shark.HeapObject.HeapInstance
import shark.HeapObject.HeapObjectArray
import shark.HeapObject.HeapPrimitiveArray
import shark.HprofHeapGraph.Companion.openHeapGraph
import shark.internal.AndroidNativeSizeMapper
import shark.internal.ObjectDominators.DominatorNode
import shark.internal.PathFinder
import shark.internal.ReferencePathNode.ChildNode
import shark.internal.ReferencePathNode.RootNode
import shark.internal.ShallowSizeCalculator
import shark.internal.hppc.LongLongScatterMap
import shark.internal.hppc.LongLongScatterMap.ForEachCallback
import java.io.File

class LoadedGraph private constructor(
  private val graph: CloseableHeapGraph,
  private val classesWithInstanceCounts: Map<Long, Int>,
  private val dominatingTree: Map<Long, DominatorNode>,
  val leakingObjectIds: Set<Long>,
  val unreachableLeakingObjectIds: Set<Long>,
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

  fun shortestPathFromGcRoots(objectId: Long): ShortestPath? {
    val pathFinder = PathFinder(
      graph,
      OnAnalysisProgressListener.NO_OP, AndroidReferenceMatchers.appDefaults
    )
    val result = pathFinder.findPathsFromGcRoots(setOf(objectId), false).pathsToLeakingObjects
    if (result.isEmpty()) {
      return null
    }
    val leaf = result.first()
    val path = mutableListOf<PathNode>()
    var leakNode = leaf
    while (leakNode is ChildNode) {
      path.add(
        0,
        PathNode(
          leakNode.objectId,
          NodeRef(
            leakNode.refFromParentType,
            leakNode.refFromParentName,
            leakNode.owningClassId
          )
        )
      )
      leakNode = leakNode.parent
    }
    val rootNode = leakNode as RootNode
    return ShortestPath(rootNode.gcRoot, rootNode.objectId, path)
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

      // TODO  Use path finder instead, removing duplicates AND unreachable objects.
      // Maybe separately track unreachable leaking objects.
      val leakingObjectIds = leakingObjectFinder.findLeakingObjectIds(graph)

      val objectInspectors = AndroidObjectInspectors.appDefaults

      val ignoredRefs =
        AndroidReferenceMatchers.appDefaults.filterIsInstance(IgnoredReferenceMatcher::class.java)

      val nativeSizeMapper = AndroidNativeSizeMapper(graph)
      val nativeSizes = nativeSizeMapper.mapNativeSizes()
      val shallowSizeCalculator = ShallowSizeCalculator(graph)

      val pathFinder = PathFinder(
        graph,
        OnAnalysisProgressListener.NO_OP, ignoredRefs
      )
      val result = pathFinder.findPathsFromGcRoots(leakingObjectIds, true)
      val dominatorTree = result.dominatorTree!!
      val pathsToLeakingObjects = result.pathsToLeakingObjects
      val foundLeakingObjectIds = pathsToLeakingObjects.map { it.objectId }.toSet()
      val unreachableLeakingObjectIds = leakingObjectIds - foundLeakingObjectIds

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
      val dominatingTree = dominatorTree.buildFullDominatorTree { objectId ->
        val nativeSize = nativeSizes[objectId] ?: 0
        val shallowSize = shallowSizeCalculator.computeShallowSize(objectId)
        nativeSize + shallowSize
      }

      // Note: shallowSizeCalculator inflates some shallow size to fix retained size.
      val computeShallowSize: (Long) -> Int = { objectId ->
        when (val heapObject = graph.findObjectById(objectId)) {
          is HeapInstance -> heapObject.byteSize
          is HeapObjectArray -> heapObject.readByteSize()
          is HeapPrimitiveArray -> heapObject.readByteSize()
          // This is probably way off but is a cheap approximation.
          is HeapClass -> heapObject.recordSize
        }
      }

      return LoadedGraph(
        graph,
        classesWithInstanceCounts,
        dominatingTree,
        foundLeakingObjectIds,
        unreachableLeakingObjectIds,
        objectInspectors,
        computeShallowSize,
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

class ShortestPath(
  val gcRoot: GcRoot,
  val rootHeldObjectId: Long,
  val path: List<PathNode>
)

class PathNode(
  val objectId: Long,
  val ref: NodeRef
)

class NodeRef(
  val refFromParentType: LeakTraceReference.ReferenceType,
  val refFromParentName: String,
  val owningClassId: Long
)