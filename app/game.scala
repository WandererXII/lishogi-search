package lishogi.search
package game

import org.joda.time.DateTime

import com.sksamuel.elastic4s.ElasticDsl.{ RichFuture => _, _ }
import scala.concurrent.duration._

object Fields {
  val status        = "s"
  val turns         = "t"
  val rated         = "r"
  val perf          = "p"
  val uids          = "u"
  val winner        = "w"
  val loser         = "o"
  val winnerColor   = "c"
  val averageRating = "a"
  val ai            = "i"
  val date          = "d"
  val duration      = "l"
  val clockInit     = "ct"
  val clockInc      = "ci"
  val clockByo      = "cb"
  val analysed      = "n"
  val senteUser     = "su"
  val goteUser      = "gu"
  val source        = "so"
}

object Mapping {
  import Fields._
  def fields =
    Seq( // only keep docValues for sortable fields
      keywordField(status) docValues false,
      shortField(turns) docValues true,
      booleanField(rated) docValues false,
      keywordField(perf) docValues false,
      keywordField(uids) docValues false,
      keywordField(winner) docValues false,
      keywordField(loser) docValues false,
      keywordField(winnerColor) docValues false,
      shortField(averageRating) docValues true,
      shortField(ai) docValues false,
      dateField(date) format Date.format docValues true,
      intField(duration) docValues false,
      intField(clockInit) docValues false,
      shortField(clockInc) docValues false,
      shortField(clockByo) docValues false,
      booleanField(analysed) docValues false,
      keywordField(senteUser) docValues false,
      keywordField(goteUser) docValues false,
      keywordField(source) docValues false
    )
}

case class Query(
    user1: Option[String] = None,
    user2: Option[String] = None,
    winner: Option[String] = None,
    loser: Option[String] = None,
    winnerColor: Option[Int] = None,
    perf: Option[Int] = None,
    source: Option[Int] = None,
    status: Option[Int] = None,
    turns: Range[Int] = Range.none,
    averageRating: Range[Int] = Range.none,
    hasAi: Option[Boolean] = None,
    aiLevel: Range[Int] = Range.none,
    rated: Option[Boolean] = None,
    date: Range[DateTime] = Range.none,
    duration: Range[Int] = Range.none,
    clock: Clocking = Clocking(),
    sorting: Sorting = Sorting.default,
    analysed: Option[Boolean] = None,
    senteUser: Option[String] = None,
    goteUser: Option[String] = None
) extends lishogi.search.Query {

  val timeout = 5 seconds

  def searchDef(from: From, size: Size) =
    index =>
      search(
        index.name
      ) query makeQuery sortBy sorting.definition start from.value size size.value timeout timeout

  def countDef = index => search(index.name) query makeQuery size 0 timeout timeout

  private lazy val makeQuery = List(
    usernames map { termQuery(Fields.uids, _) },
    toQueries(winner, Fields.winner),
    toQueries(loser, Fields.loser),
    toQueries(winnerColor, Fields.winnerColor),
    turns queries Fields.turns,
    averageRating queries Fields.averageRating,
    duration queries Fields.duration,
    clock.init queries Fields.clockInit,
    clock.inc queries Fields.clockInc,
    clock.byo queries Fields.clockByo,
    date map Date.formatter.print queries Fields.date,
    hasAiQueries,
    (hasAi | true).fold(aiLevel queries Fields.ai, Nil),
    toQueries(perf, Fields.perf),
    toQueries(source, Fields.source),
    toQueries(rated, Fields.rated),
    toQueries(status, Fields.status),
    toQueries(analysed, Fields.analysed),
    toQueries(senteUser, Fields.senteUser),
    toQueries(goteUser, Fields.goteUser)
  ).flatten match {
    case Nil     => matchAllQuery()
    case queries => boolQuery().must(queries)
  }

  def usernames = List(user1, user2).flatten

  private def hasAiQueries =
    hasAi.toList map { a =>
      a.fold(existsQuery(Fields.ai), not(existsQuery(Fields.ai)))
    }

  private def toQueries(query: Option[_], name: String) =
    query.toList map {
      case s: String => termQuery(name, s.toLowerCase)
      case x         => termQuery(name, x)
    }
}

object Query {

  import play.api.libs.json._
  import play.api.libs.json.JodaReads._

  implicit private val sortingJsonReader                   = Json.reads[Sorting]
  implicit private val clockingJsonReader                  = Json.reads[Clocking]
  implicit val dateTimeOrdering: Ordering[DateTime]        = Ordering.fromLessThan(_ isBefore _)
  implicit val dateRangeJsonReader: Reads[Range[DateTime]] = Range.rangeJsonReader[DateTime]
  implicit val jsonReader                                  = Json.reads[Query]
}

case class Sorting(f: String, order: String) {
  import com.sksamuel.elastic4s.requests.searches.sort.SortOrder
  def definition =
    fieldSort {
      (Sorting.fieldKeys contains f).fold(f, Sorting.default.f)
    } order (order.toLowerCase == "asc").fold(SortOrder.ASC, SortOrder.DESC)
}
object Sorting {

  val default = Sorting(Fields.date, "desc")

  val fieldKeys = List(Fields.date, Fields.turns, Fields.averageRating)
}

case class Clocking(
    initMin: Option[Int] = None,
    initMax: Option[Int] = None,
    incMin: Option[Int] = None,
    incMax: Option[Int] = None,
    byoMin: Option[Int] = None,
    byoMax: Option[Int] = None,
) {

  def init = Range(initMin, initMax)
  def inc  = Range(incMin, incMax)
  def byo  = Range(byoMin, byoMax)
}
