package starfruit.ui

import javafx.beans.InvalidationListener
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import scala.jdk.CollectionConverters.*

object FxCollectionsExtra {

  class ObservableView[T, U](val underlying: ObservableList[T])(view: T => U) extends ObservableList[U] {
    private class RefEqual[R](val ref: R) {
      override def equals(o: Any) = o match {
        case e: RefEqual[r] => hashCode == e.hashCode
        case _ => false
      }
      override def hashCode = System.identityHashCode(ref)
      override def toString = s"Ref($ref): $hashCode"
    }
    private val index = collection.mutable.Map[T, RefEqual[U]]()
    private val reverseIndex = collection.mutable.Map[RefEqual[U], T]()
    private def getMapped(t: T): U = {
      index.get(t).fold {
        val res = view(t)
        val ref = new RefEqual(res)
        reverseIndex(ref) = t
        index(t) = ref
        res
      } ( _.ref )
    }
    underlying.addListener({ evt => 
        evt.next()
        if (evt.getRemovedSize > 0) evt.getRemoved.asScala.foreach {t => 
          index.remove(t) foreach reverseIndex.-=
        }}: ListChangeListener[T])
    
    def add(index: Int, e: U): Unit = ???
    def add(e: U): Boolean = ???
    def addAll(index: Int, elems: java.util.Collection[? <: U]): Boolean = ???
    def addAll(elems: java.util.Collection[? <: U]): Boolean = ???
    def addAll(x$0: Array[? <: U]): Boolean = ???
    def removeAll(x$0: Array[? <: U]): Boolean = ???
    def retainAll(x$0: Array[? <: U]): Boolean = ???
    def setAll(x$0: Array[? <: U]): Boolean = ???
    def clear(): Unit = ???
    def contains(e: Any): Boolean = ???
    def containsAll(elemes: java.util.Collection[?]): Boolean = ???
    def get(index: Int): U = getMapped(underlying.get(index))
    def indexOf(e: Any): Int = reverseIndex.get(new RefEqual(e.asInstanceOf[U])).fold(-1)(underlying.indexOf)
    def isEmpty(): Boolean = underlying.isEmpty
    def iterator(): java.util.Iterator[U] = new java.util.Iterator[U] {
      val i = underlying.iterator
      override def hasNext = i.hasNext
      override def next = getMapped(i.next)
    }
    def lastIndexOf(elem: Any): Int = ???
    def listIterator(index: Int): java.util.ListIterator[U] = ???
    def listIterator(): java.util.ListIterator[U] = ???
    def remove(indx: Int): U = ???
    def remove(e: Any): Boolean = ???
    def removeAll(elems: java.util.Collection[?]): Boolean = ???
    def retainAll(elems: java.util.Collection[?]): Boolean = ???
    def set(index: Int, e: U): U = ???
    def size(): Int = underlying.size
    def subList(from: Int, to: Int): java.util.List[U] = underlying.subList(from, to).asScala.map(getMapped).asJava
    def toArray[T2](res: Array[T2 & Object]): Array[T2 & Object] = {
      underlying.asScala.map(getMapped).zipWithIndex foreach (e => res(e._2) = e._1.asInstanceOf[T2 & Object])
      res
    }
    def toArray(): Array[Object] = underlying.asScala.map(getMapped).asJava.toArray()
  
    class MappedInvalidationListener(val underlying: InvalidationListener) extends InvalidationListener {
      def invalidated(obs: javafx.beans.Observable) = if (obs == ObservableView.this.underlying) underlying.invalidated(ObservableView.this)
    }
    val mappedInvalidationListeners = collection.mutable.Buffer[MappedInvalidationListener]()
    // Members declared in javafx.beans.Observable
    def addListener(listener: InvalidationListener): Unit = {
      val mapped = new MappedInvalidationListener(listener)
      mappedInvalidationListeners += mapped
      underlying.addListener(mapped)
    }
    def removeListener(listener: InvalidationListener): Unit = mappedInvalidationListeners.find(_.underlying == listener) foreach underlying.removeListener
  
    // Members declared in javafx.collections.ObservableList
    def remove(from: Int, to: Int): Unit = ???
    
    class MappedListChangeListener(val underlying: ListChangeListener[? >: U]) extends ListChangeListener[T] {
      def onChanged(evt: ListChangeListener.Change[? <: T]): Unit = underlying `onChanged` new ListChangeListener.Change[U](ObservableView.this) {
        def getFrom(): Int = evt.getFrom
        protected def getPermutation(): Array[Int] = {
          val m = evt.getClass.getDeclaredMethod("getPermutation")
          m.setAccessible(true)
          m.invoke(evt).asInstanceOf[Array[Int]]
        }
        def getRemoved(): java.util.List[U] = evt.getRemoved().asScala.map(view).asJava
        def getTo(): Int = evt.getTo
        def next(): Boolean = evt.next()
        def reset(): Unit = evt.reset()
      }
    }
    val mappedListChangeListeners = collection.mutable.Buffer[MappedListChangeListener]()
    def addListener(listener: ListChangeListener[? >: U]): Unit = {
      val mapped = new MappedListChangeListener(listener)
      mappedListChangeListeners += mapped
      underlying.addListener(mapped)
    }
    def removeListener(listener: ListChangeListener[? >: U]): Unit = mappedListChangeListeners.find(_.underlying == listener) foreach underlying.removeListener
    
    def setAll(elems: java.util.Collection[? <: U]): Boolean = ???
  }
}
