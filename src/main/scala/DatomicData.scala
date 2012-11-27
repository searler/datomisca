package reactivedatomic

import scala.util.parsing.input.Positional

case class Namespace(name: String) {
  override def toString = name

  def /(name: String) = Keyword(name, Some(this))
}

object Namespace {
  val DB = new Namespace("db") {
    val PART = Namespace("db.part")
    val TYPE = Namespace("db.type")
    val CARDINALITY = Namespace("db.cardinality")
    val INSTALL = Namespace("db.install")
    val UNIQUE = Namespace("db.unique")
    val FN = Namespace("db.fn")
  } 
}

trait Nativeable {
  def toNative: java.lang.Object
}

trait Namespaceable extends Nativeable {
  def name: String
  def ns: Option[Namespace] = None

  override def toString = ":" + ( if(ns.isDefined) {ns.get + "/"} else "" ) + name

  def toNative: java.lang.Object = clojure.lang.Keyword.intern(( if(ns.isDefined) {ns.get + "/"} else "" ) + name )
}

/* DATOMIC TYPES */
sealed trait DatomicData extends Nativeable

case class DString(value: String) extends DatomicData {
  override def toString = "\""+ value + "\""
  def toNative: java.lang.Object = value: java.lang.String 
}

case class DBoolean(value: Boolean) extends DatomicData {
  override def toString = value.toString
  def toNative: java.lang.Object = value: java.lang.Boolean
}

case class DInt(value: Int) extends DatomicData {
  override def toString = value.toString
  def toNative: java.lang.Object = value: java.lang.Integer
}

case class DLong(value: Long) extends DatomicData {
  override def toString = value.toString
  def toNative: java.lang.Object = value: java.lang.Long
}

case class DFloat(value: Float) extends DatomicData {
  override def toString = value.toString
  def toNative: java.lang.Object = value: java.lang.Float
}

case class DDouble(value: Double) extends DatomicData {
  override def toString = value.toString
  def toNative: java.lang.Object = value: java.lang.Double
}

case class DBigDec(value: BigDecimal) extends DatomicData {
  override def toString = value.toString
  def toNative: java.lang.Object = value
}

case class DInstant(value: java.util.Date) extends DatomicData {
  override def toString = value.toString
  def toNative: java.lang.Object = value
}

case class DUuid(value: java.util.UUID) extends DatomicData {
  override def toString = value.toString
  def toNative: java.lang.Object = value
}

case class DUri(value: java.net.URI) extends DatomicData {
  override def toString = value.toString
  def toNative: java.lang.Object = value
}

case class DBytes(value: Array[Byte]) extends DatomicData {
  override def toString = value.toString
  def toNative: java.lang.Object = value: java.lang.Object
}

case class DRef(value: Either[Keyword, DId]) extends DatomicData {
  override def toString = value match {
    case Left(kw) => kw.toString
    case Right(id) => id.toString
  }
  def toNative: java.lang.Object = value match {
    case Left(kw) => kw.toNative
    case Right(id) => id.toNative
  }
}

object DRef {
  def apply(kw: Keyword) = new DRef(Left(kw))
  def apply(id: DId) = new DRef(Right(id))
}

case class DDatabase(value: datomic.Database) extends DatomicData {
  def entity(e: DLong) = value.entity(e.value)

  //def entid(e: DLong): DId = DId(value.entid(e.value).asInstanceOf[datomic.db.DbId])

  override def toString = value.toString
  def toNative: java.lang.Object = value
}

trait DId extends DatomicData

case class FinalId(value: Long) extends DId {
  //def toNative: java.lang.Object = value
  override lazy val toNative: java.lang.Object = value: java.lang.Long

  override def toString = toNative.toString
}

case class TempId(partition: Partition, id: Option[Long] = None, dbId: java.lang.Object) extends DId {
  //def toNative: java.lang.Object = value
  override lazy val toNative: java.lang.Object = dbId

  override def toString = toNative.toString
}

object DId {
  def tempid(partition: Partition, id: Option[Long] = None) = id match {
    case None => datomic.Peer.tempid(partition.toString)
    case Some(id) => datomic.Peer.tempid(partition.toString, id)
  }

  def apply(partition: Partition, id: Long) = new TempId(partition, Some(id), DId.tempid(partition, Some(id)))
  def apply(partition: Partition) = new TempId(partition, None, DId.tempid(partition))
  def apply(id: Long) = new FinalId(id)
  def apply(id: DLong) = new FinalId(id.value)
  //def apply(partition: Partition = Partition.USER) = new DId(datomic.Peer.tempid(partition.toString).asInstanceOf[datomic.db.DbId])
  //def apply(partition: Partition, id: Long) = new DId(datomic.Peer.tempid(partition.toString, id).asInstanceOf[datomic.db.DbId])

  //def from(dl: DLong)(implicit dd: DDatabase) = dd.entid(dl)
}

/** DSet is a Set but in order to be able to have several tempids in it, this is a seq */
case class DSet(elements: Set[DatomicData]) extends DatomicData {
  def toNative: java.lang.Object = {
    import scala.collection.JavaConverters._
    ( elements.map( _.toNative ) ).toList.asJava
  }

  override def toString = elements.mkString("[", ", ", "]")
}

object DSet {
  def apply(dd: DatomicData) = new DSet(Set(dd))
  def apply(dd: DatomicData, dds: DatomicData *) = new DSet(Set(dd) ++ dds)
}

trait DDReader[-DD <: DatomicData, +A] {
  def read(dd: DD): A
}

object DDReader{
  def apply[DD <: DatomicData, A](f: DD => A) = new DDReader[DD, A]{
    def read(dd: DD): A = f(dd)
  }
}

trait DDWriter[+DD <: DatomicData, -A] {
  def write(a: A): DD
}

object DDWriter{
  def apply[DD <: DatomicData, A](f: A => DD) = new DDWriter[DD, A] {
    def write(a: A) = f(a)
  }
}

trait DWrapper extends NotNull
private[reactivedatomic] case class DWrapperImpl(value: DatomicData) extends DWrapper


trait DatomicDataImplicits {
  implicit val DString2String = DDReader{ s: DString => s.value }
  implicit val DLong2Long = DDReader{ s: DLong => s.value }
  implicit val DInt2Int = DDReader{ s: DInt => s.value }

  implicit val DStringWrites = DDWriter[DString, String]( (s: String) => DString(s) )
  implicit val DIntWrites = DDWriter[DInt, Int]( (i: Int) => DInt(i) )
  implicit val DLongWrites = DDWriter[DLong, Long]( (l: Long) => DLong(l) )
  implicit val DBooleanWrites = DDWriter[DBoolean, Boolean]( (b: Boolean) => DBoolean(b) )
  implicit val DFloatWrites = DDWriter[DFloat, Float]( (b: Float) => DFloat(b) )
  implicit val DDoubleWrites = DDWriter[DDouble, Double]( (b: Double) => DDouble(b) )
  implicit val DReferenceable = DDWriter[DRef, Referenceable]( (referenceable: Referenceable) => referenceable.ident )
  implicit def DDatomicData[DD <: DatomicData] = DDWriter[DD, DD]( dd => dd )
  implicit def DRefWrites = DDWriter[DRef, Ref[_]]( (ref: Ref[_]) => DRef(ref.id) )
  implicit def DSetWrites[A](implicit ddw: DDWriter[DatomicData, A]) = 
    DDWriter[DSet, Traversable[A]]{ (l: Traversable[A]) => DSet(l.map{ a => Datomic.toDatomic(a)(ddw) }.toSet) }

}

object DatomicData {

  def toDatomicData(v: Any): DatomicData = v match {
    case s: String => DString(s)
    case b: Boolean => DBoolean(b)
    case i: Int => DInt(i)
    case l: Long => DLong(l)
    case f: Float => DFloat(f)
    case d: Double => DDouble(d)
    case bd: BigDecimal => DBigDec(bd)
    case d: java.util.Date => DInstant(d)
    case u: java.util.UUID => DUuid(u)
    case u: java.net.URI => DUri(u)
    // REF???
    case v => throw new RuntimeException("Unknown Datomic Value "+v.getClass)
  }

  import scala.collection.JavaConverters._
  def toDatomicNative(d: DatomicData): java.lang.Object = {
    d match {
      case DString(s) => s
      case DBoolean(b) => new java.lang.Boolean(b)
      case DInt(i) => new java.lang.Long(i)
      case DLong(l) => new java.lang.Long(l)
      case DFloat(f) => new java.lang.Float(f)
      case DDouble(d) => new java.lang.Double(d)
      case DDatabase(db) => db
      //case DBigDec(bd) => new java.lang.BigDecimal(bd)
      //case d: java.util.Date => DInstant(d)
      //case u: java.util.UUID => DUuid(u)
      //case u: java.net.URI => DUri(u)
      // REF???
      case v => throw new RuntimeException("Can't convert Datomic Data "+ v.toString + " to Native")
    }
  }

}


/* DATOMIC TERMS */
sealed trait Term

case class Var(name: String) extends Term {
  override def toString = "?" + name
}

case class Keyword(override val name: String, override val ns: Option[Namespace] = None) extends Term with Namespaceable with Positional

object Keyword {
  def apply(name: String, ns: Namespace) = new Keyword(name, Some(ns))
  def apply(ns: Namespace, name: String) = new Keyword(name, Some(ns))
}

case class Const(value: DatomicData) extends Term {
  override def toString = value.toString
}

case object Empty extends Term {
  override def toString = "_"
}

trait DataSource extends Term {
  def name: String

  override def toString = "$" + name
}

case class ExternalDS(override val name: String) extends DataSource
case object ImplicitDS extends DataSource {
  def name = ""
}