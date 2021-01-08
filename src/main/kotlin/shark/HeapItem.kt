package shark

import shark.HeapItem.BoringItems
import shark.HeapItem.HeapClassItem
import shark.HeapItem.HeapInstanceItem
import shark.HeapItem.NotExpandable
import shark.HeapObject.HeapClass
import shark.HeapObject.HeapInstance
import shark.HeapObject.HeapObjectArray
import shark.HeapObject.HeapPrimitiveArray
import shark.LeakTraceObject.LeakingStatus
import shark.LeakTraceObject.LeakingStatus.LEAKING
import shark.LeakTraceObject.LeakingStatus.NOT_LEAKING
import shark.LeakTraceObject.LeakingStatus.UNKNOWN
import shark.ValueHolder.BooleanHolder
import shark.ValueHolder.ByteHolder
import shark.ValueHolder.CharHolder
import shark.ValueHolder.DoubleHolder
import shark.ValueHolder.FloatHolder
import shark.ValueHolder.IntHolder
import shark.ValueHolder.LongHolder
import shark.ValueHolder.ReferenceHolder
import shark.ValueHolder.ShortHolder
import kotlin.math.ln
import kotlin.math.pow

sealed class HeapItem {

  open fun expand(
    graph: LoadedGraph
  ): List<TreeItem<HeapItem>> = emptyList()

  class HeapClassItem(private val objectId: Long) : HeapItem() {
    override fun expand(graph: LoadedGraph): List<TreeItem<HeapItem>> {
      val heapClass = graph.findObjectById(objectId) as HeapClass
      val superclass = heapClass.superclass
      val items = mutableListOf<TreeItem<HeapItem>>()

      if (superclass != null) {
        items += superclass.toTreeItem(
          graph.instanceCount(superclass),
          prefix = "Parent "
        )
      }
      val subclassCount = heapClass.subclasses.count()
      items += if (subclassCount > 0) {
        sectionHeader("Subclasses ($subclassCount subclasses)", HeapSubclassesItem(objectId))
      } else {
        boringItem("No subclass")
      }

      graph.dominating(objectId)?.let { dominating ->
        val retainedSize = dominating.retainedSize.toHumanReadableBytes()
        if (dominating.retainedCount > 1) {
          items += boringItem("Retaining $retainedSize in ${dominating.retainedCount} objects")
        }
        if (dominating.dominatedObjectIds.isNotEmpty()) {
          items += sectionHeader("Dominating", HeapDominatingItem(objectId))
        }
      }

      val staticFieldCount = heapClass.readStaticFields().count()

      items += if (staticFieldCount > 0) {
        sectionHeader("Static fields ($staticFieldCount fields)", HeapStaticFieldsItem(objectId))
      } else {
        boringItem("No static field")
      }

      val instanceCount = graph.instanceCount(heapClass)

      items += if (instanceCount > 0) {
        sectionHeader("Instances ($instanceCount instances)", HeapClassInstancesItem(objectId))
      } else {
        boringItem("No instance")
      }

      return items
    }
  }

  class HeapSubclassesItem(private val objectId: Long) : HeapItem() {
    override fun expand(graph: LoadedGraph): List<TreeItem<HeapItem>> {
      val (heapClass, count) = graph.findClassById(objectId)
      return heapClass.subclasses.map { it.toTreeItem(count) }
        .toList()
    }
  }

  class HeapStaticFieldsItem(private val objectId: Long) : HeapItem() {
    override fun expand(graph: LoadedGraph): List<TreeItem<HeapItem>> {
      val (heapClass, _) = graph.findClassById(objectId)

      return heapClass.readStaticFields().map { field ->
        field.toTreeItem(graph, "static ")
      }.toList()
    }
  }

  class HeapClassInstancesItem(private val objectId: Long) : HeapItem() {
    override fun expand(graph: LoadedGraph): List<TreeItem<HeapItem>> {
      val heapObject = graph.findObjectById(objectId) as HeapClass
      return heapObject.directInstances.map { instance ->
        instance.toTreeItem()
      }.toList()
    }
  }

  class HeapInstanceItem(private val objectId: Long) : HeapItem() {
    override fun expand(graph: LoadedGraph): List<TreeItem<HeapItem>> {
      val heapInstance = graph.findObjectById(objectId) as HeapInstance

      val items = mutableListOf<TreeItem<HeapItem>>()

      val instanceClass = heapInstance.instanceClass
      items += instanceClass.toTreeItem(graph.instanceCount(instanceClass))

      val reporter = ObjectReporter(heapInstance)
      graph.objectInspectors.forEach {
        it.inspect(reporter)
      }

      val (status, reason) = reporter.resolveStatus()

      if (status != UNKNOWN) {
        when (status) {
          UNKNOWN -> Unit
          NOT_LEAKING -> items += boringItem("Leaking: NO ($reason)")
          LEAKING -> items += boringItem("Leaking: YES ($reason)")
        }
      }

      if (reporter.labels.isNotEmpty()) {
        items += boringSection("Inspections", reporter.labels.toList())
      }

      val dominatorId = graph.dominator(objectId)
      if (dominatorId != ValueHolder.NULL_REFERENCE) {
        items += graph.findObjectById(dominatorId).toTreeItem(graph, "Dominator: ")
      }

      items += boringItem("Shallow size: ${graph.computeSize(objectId).toHumanReadableBytes()}")

      graph.dominating(objectId)?.let { dominating ->
        val retainedSize = dominating.retainedSize.toHumanReadableBytes()
        if (dominating.retainedCount > 1) {
          items += boringItem("Retaining $retainedSize in ${dominating.retainedCount} objects")
        }
        if (dominating.dominatedObjectIds.isNotEmpty()) {
          items += sectionHeader("Dominating", HeapDominatingItem(objectId))
        }
      }

      var currentClass = heapInstance.instanceClass
      var currentClassFieldCount = 0
      heapInstance.readFields().forEach { field ->
        if (field.declaringClass.objectId == currentClass.objectId) {
          currentClassFieldCount++
        } else {
          items += sectionHeader(
            "Fields from ${currentClass.name} ($currentClassFieldCount fields)",
            HeapMemberFieldsItem(currentClass.objectId, objectId)
          )
          currentClass = field.declaringClass
          currentClassFieldCount = 1
        }
      }
      if (currentClassFieldCount > 0) {
        items += sectionHeader(
          "Fields from ${currentClass.name} ($currentClassFieldCount fields)",
          HeapMemberFieldsItem(currentClass.objectId, objectId)
        )
      } else if (currentClass === heapInstance.instanceClass) {
        items += boringItem("No field")
      }

      return items
    }
  }

  class HeapDominatingItem(private val objectId: Long) : HeapItem() {
    override fun expand(graph: LoadedGraph): List<TreeItem<HeapItem>> {
      val dominating = graph.dominating(objectId)
      return dominating?.let {
        it.dominatedObjectIds.map { dominatedObjectId ->
          val dominatedObject = graph.findObjectById(dominatedObjectId)
          dominatedObject.toTreeItem(graph)
        }
      } ?: emptyList()
    }
  }

  class HeapMemberFieldsItem(private val classObjectId: Long, private val objectId: Long) :
    HeapItem() {
    override fun expand(graph: LoadedGraph): List<TreeItem<HeapItem>> {
      val heapInstance = graph.findObjectById(objectId) as HeapInstance
      return heapInstance.readFields()
        .filter { field -> field.declaringClass.objectId == classObjectId }.map { field ->
          field.toTreeItem(graph)
        }.toList()
    }
  }

  object NotExpandable : HeapItem()

  class BoringItems(private val names: List<String>) : HeapItem() {
    override fun expand(graph: LoadedGraph): List<TreeItem<HeapItem>> {
      return names.map { boringItem(it) }
    }
  }
}

fun boringItem(name: String): TreeItem<HeapItem> {
  return TreeItem(
    NotExpandable,
    expandable = false,
    expended = false,
    name = name,
    selectable = false
  )
}

fun sectionHeader(name: String, item: HeapItem): TreeItem<HeapItem> {
  return TreeItem(
    item,
    expandable = true,
    expended = false,
    name = name,
    selectable = false
  )
}

fun boringSection(name: String, boringItems: List<String>): TreeItem<HeapItem> {
  return TreeItem(
    BoringItems(boringItems),
    expandable = true,
    expended = false,
    name = name,
    selectable = false
  )
}

fun HeapObject.toTreeItem(graph: LoadedGraph, prefix: String = ""): TreeItem<HeapItem> {
  return when (this) {
    is HeapClass -> toTreeItem(graph.instanceCount(objectId), prefix)
    is HeapInstance -> toTreeItem(prefix)
    else -> boringItem("$this not supported yet")
  }
}

fun HeapClass.toTreeItem(
  instanceCount: Int,
  prefix: String = ""
): TreeItem<HeapItem> {
  return TreeItem(
    HeapClassItem(objectId),
    expandable = true,
    expended = false,
    name = "${prefix}Class $name ($instanceCount instances)",
    selectable = true
  )
}

fun HeapInstance.toTreeItem(prefix: String = ""): TreeItem<HeapItem> {
  return TreeItem(
    HeapInstanceItem(objectId),
    expandable = true,
    expended = false,
    name = "${prefix}Instance ${instanceClassName}@${objectId}",
    selectable = true
  )
}

fun HeapField.toTreeItem(graph: LoadedGraph, prefix: String = ""): TreeItem<HeapItem> {
  return when (val holder = value.holder) {
    is BooleanHolder -> boringItem("${prefix}boolean $name = ${holder.value}")
    is CharHolder -> boringItem("${prefix}char $name = ${holder.value}")
    is FloatHolder -> boringItem("${prefix}float $name = ${holder.value}")
    is DoubleHolder -> boringItem("${prefix}double $name = ${holder.value}")
    is ByteHolder -> boringItem("${prefix}byte $name = ${holder.value}")
    is ShortHolder -> boringItem("${prefix}short $name = ${holder.value}")
    is IntHolder -> boringItem("${prefix}int $name = ${holder.value}")
    is LongHolder -> boringItem("${prefix}long $name = ${holder.value}")
    is ReferenceHolder -> {
      if (value.isNullReference) {
        boringItem("${prefix}$name = null")
      } else {
        when (val referencedObject = graph.findObjectById(holder.value)) {
          is HeapClass -> referencedObject.toTreeItem(
            graph.instanceCount(referencedObject),
            "${prefix}$name = "
          )
          is HeapInstance -> referencedObject.toTreeItem("${prefix}$name = ")
          is HeapObjectArray -> boringItem("object array")
          is HeapPrimitiveArray -> boringItem("primitive array")
        }
      }
    }
  }
}

private fun ObjectReporter.resolveStatus(
  leakingWins: Boolean = false
): Pair<LeakingStatus, String> {
  var status = UNKNOWN
  var reason = ""
  if (notLeakingReasons.isNotEmpty()) {
    status = NOT_LEAKING
    reason = notLeakingReasons.joinToString(" and ")
  }
  val leakingReasons = leakingReasons
  if (leakingReasons.isNotEmpty()) {
    val winReasons = leakingReasons.joinToString(" and ")
    // Conflict
    if (status == NOT_LEAKING) {
      if (leakingWins) {
        status = LEAKING
        reason = "$winReasons. Conflicts with $reason"
      } else {
        reason += ". Conflicts with $winReasons"
      }
    } else {
      status = LEAKING
      reason = winReasons
    }
  }
  return status to reason
}

private fun Int.toHumanReadableBytes() = toLong().toHumanReadableBytes()

// https://stackoverflow.com/a/3758880
private fun Long.toHumanReadableBytes(): String {
  val unit = 1000
  if (this < unit) return "$this B"
  val exp = (ln(this.toDouble()) / ln(unit.toDouble())).toInt()
  val pre = "kMGTPE"[exp - 1]
  return String.format("%.1f %sB", this / unit.toDouble().pow(exp.toDouble()), pre)
}