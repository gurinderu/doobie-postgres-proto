package doobie.postgres.proto

import cats.Show
import com.devim.protobuf.relay.{Cursor, Page}
import doobie.imports._
import shapeless._
import shapeless.ops.hlist.Selector

object imports {

  private final val defaultPage = Page()

  implicit class RelayPageExtractor[L <: HList](val list: List[Long :: Long :: L])
      extends AnyVal {
    def extract[T]()(implicit s: Show[T], selector: Selector[L, T]): Page = {
      (list.lastOption, list.headOption) match {
        case (Some(total :: lastNum :: lastTail),
              Some(_ :: firstNum :: firstTail)) =>
          Page(lastNum < total,
               firstNum > 0,
               Some(s.show(selector(firstTail))),
               Some(s.show(selector(lastTail))))
        case _ => defaultPage
      }

    }
  }

  implicit class RelayFragment(val fr: Fragment) extends AnyVal {
    def queryWithCursor[B: Composite](cursorColumn: String,
                                      cursor: Option[Cursor],
                                      alias: String = "data",
                                      cursorColumnType: String = "UUID")(
        implicit h: LogHandler = LogHandler.nop): Query0[B] = {
      val wrapped = wrapQuery(alias)
      sliding(wrapped, cursor, alias, cursorColumn, cursorColumnType).query[B]
    }

    private def wrapQuery(alias: String): Fragment = {
      Fragment.const(
        s"with $alias as (select row_number() over() as rnum,t.* from (") ++
        fr ++
        Fragment.const(") t) select max(rnum) over(),t.* from data t")
    }

    private def sliding(query: Fragment,
                        maybeCursor: Option[Cursor],
                        alias: String,
                        slidingColumn: String,
                        cursorColumnType: String): Fragment = {
      val columnFr = Fragment.const(slidingColumn)
      val aliasFr = Fragment.const(alias)

      maybeCursor
        .map {
          case Cursor(beforeMaybe, afterMaybe, None, Some(last)) =>
            fr"select * from (" ++
              query ++
              Fragments.whereAndOpt(
                afterMaybe.map(after =>
                  afterPredicate(aliasFr, cursorColumnType, columnFr, after)),
                beforeMaybe.map(before =>
                  beforePredicate(aliasFr, cursorColumnType, columnFr, before))
              ) ++
              fr"order by rnum desc" ++
              Fragment.const(s"limit $last ) t order by t.rnum asc")
          case Cursor(beforeMaybe, afterMaybe, first, _) =>
            query ++
              Fragments.whereAndOpt(
                afterMaybe.map(after =>
                  afterPredicate(aliasFr, cursorColumnType, columnFr, after)),
                beforeMaybe.map(before =>
                  beforePredicate(aliasFr, cursorColumnType, columnFr, before))
              ) ++ first
              .map(limit => Fragment.const(s"limit $limit"))
              .getOrElse(Fragment.empty)
        }
        .getOrElse(query)

    }

    private def afterPredicate(alias: Fragment,
                               cursorColumnType: String,
                               columnFr: Fragment,
                               after: String): Fragment = {
      fr"rnum>=(select min(rnum) from" ++
        alias ++
        fr"where" ++
        columnFr ++
        fr"=$after::" ++
        Fragment.const(cursorColumnType) ++
        fr")"
    }

    private def beforePredicate(alias: Fragment,
                                cursorColumnType: String,
                                columnFr: Fragment,
                                before: String): Fragment = {
      fr"rnum<=(select max(rnum) from" ++
        alias ++
        fr"where" ++
        columnFr ++
        fr"=$before::" ++
        Fragment.const(cursorColumnType) ++
        fr")"
    }
  }

}
