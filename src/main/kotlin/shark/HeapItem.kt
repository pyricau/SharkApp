package shark

import shark.HeapItem.HeapClassItem
import shark.HeapItem.HeapInstanceItem
import shark.HeapItem.NotExpandable
import shark.HeapObject.HeapClass
import shark.HeapObject.HeapInstance
import shark.HeapObject.HeapObjectArray
import shark.HeapObject.HeapPrimitiveArray
import shark.ValueHolder.BooleanHolder
import shark.ValueHolder.ByteHolder
import shark.ValueHolder.CharHolder
import shark.ValueHolder.DoubleHolder
import shark.ValueHolder.FloatHolder
import shark.ValueHolder.IntHolder
import shark.ValueHolder.LongHolder
import shark.ValueHolder.ReferenceHolder
import shark.ValueHolder.ShortHolder

sealed class HeapItem {

  open fun expand(
    graph: HeapGraph,
    classesWithInstanceCounts: Map<Long, Int>
  ): List<TreeItem<HeapItem>> = emptyList()

  class HeapClassItem(val objectId: Long) : HeapItem() {
    override fun expand(
      graph: HeapGraph,
      classesWithInstanceCounts: Map<Long, Int>
    ): List<TreeItem<HeapItem>> {
      val heapClass = graph.findObjectById(objectId) as HeapClass
      val superclass = heapClass.superclass
      val items = mutableListOf<TreeItem<HeapItem>>()

      if (superclass != null) {
        items += superclass.toTreeItem(
          classesWithInstanceCounts.getValue(superclass.objectId),
          prefix = "Parent "
        )
      }
      val subclassCount = heapClass.subclasses.count()
      items += if (subclassCount > 0) {
        sectionHeader("Subclasses ($subclassCount subclasses)", HeapSubclassesItem(objectId))
      } else {
        boringItem("No subclass")
      }

      val staticFieldCount = heapClass.readStaticFields().count()

      items += if (staticFieldCount > 0) {
        sectionHeader("Static fields ($staticFieldCount fields)", HeapStaticFieldsItem(objectId))
      } else {
        boringItem("No static field")
      }

      val instanceCount = classesWithInstanceCounts.getValue(objectId)

      items += if (instanceCount > 0) {
        sectionHeader("Instances ($instanceCount instances)", HeapClassInstancesItem(objectId))
      } else {
        boringItem("No instance")
      }

      return items
    }
  }

  class HeapSubclassesItem(val objectId: Long) : HeapItem() {
    override fun expand(
      graph: HeapGraph,
      classesWithInstanceCounts: Map<Long, Int>
    ): List<TreeItem<HeapItem>> {
      val heapObject = graph.findObjectById(objectId) as HeapClass
      return heapObject.subclasses.map { it.toTreeItem(classesWithInstanceCounts.getValue(it.objectId)) }
        .toList()
    }
  }

  class HeapStaticFieldsItem(val objectId: Long) : HeapItem() {
    override fun expand(
      graph: HeapGraph,
      classesWithInstanceCounts: Map<Long, Int>
    ): List<TreeItem<HeapItem>> {
      val heapClass = graph.findObjectById(objectId) as HeapClass

      return heapClass.readStaticFields().map { field ->
        field.toTreeItem(classesWithInstanceCounts, "static ")
      }.toList()
    }
  }

  class HeapClassInstancesItem(val objectId: Long) : HeapItem() {
    override fun expand(
      graph: HeapGraph,
      classesWithInstanceCounts: Map<Long, Int>
    ): List<TreeItem<HeapItem>> {
      val heapObject = graph.findObjectById(objectId) as HeapClass
      return heapObject.directInstances.map { instance ->
        instance.toTreeItem()
      }.toList()
    }
  }

  class HeapInstanceItem(val objectId: Long) : HeapItem() {
    override fun expand(
      graph: HeapGraph,
      classesWithInstanceCounts: Map<Long, Int>
    ): List<TreeItem<HeapItem>> {
      val heapInstance = graph.findObjectById(objectId) as HeapInstance

      val items = mutableListOf<TreeItem<HeapItem>>()

      val instanceClass = heapInstance.instanceClass
      items += instanceClass.toTreeItem(classesWithInstanceCounts.getValue(instanceClass.objectId))

      val fieldCount = heapInstance.readFields().count()

      // TODO Move fields to each be part of its class in subsections.
      // Class Foo fields
      // Should be root sections
      items += if (fieldCount > 0) {
        sectionHeader("Fields ($fieldCount fields)", HeapMemberFieldsItem(objectId))
      } else {
        boringItem("No field")
      }

      return items
    }
  }

  class HeapMemberFieldsItem(val objectId: Long) : HeapItem() {
    override fun expand(
      graph: HeapGraph,
      classesWithInstanceCounts: Map<Long, Int>
    ): List<TreeItem<HeapItem>> {
      val heapInstance = graph.findObjectById(objectId) as HeapInstance
      return heapInstance.readFields().map { field ->
        field.toTreeItem(classesWithInstanceCounts)
      }.toList()
    }
  }

  object NotExpandable : HeapItem() {}
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

fun HeapField.toTreeItem(classesWithInstanceCounts: Map<Long, Int>, prefix: String = ""): TreeItem<HeapItem> {
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
        when (val referencedObject = declaringClass.graph.findObjectById(holder.value)) {
          is HeapClass -> referencedObject.toTreeItem(
            classesWithInstanceCounts.getValue(referencedObject.objectId),
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

