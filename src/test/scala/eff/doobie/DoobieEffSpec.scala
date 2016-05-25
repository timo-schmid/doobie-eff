package eff.doobie

import org.specs2.Specification
import scalaz._
import scalaz.Scalaz._
import scalaz.concurrent.Task
import org.atnos.eff._
import org.atnos.eff.all._
import org.atnos.eff.task._
import org.atnos.eff.syntax.all._
import org.atnos.eff.syntax.task._
import doobie.imports._
import doobie.contrib.h2.h2transactor._
import scala.concurrent.duration._

class DoobieEffSpec extends Specification { def is = s2"""

  Tasks produced by doobie can be added to a stack of effects:
    - with a transaction for each query                       $effStackTest
    - with a single transaction for all queries               $effTxStackTest
    - with a query that crashes                               $effCrash
    - with a full application stack                           $effFullStack
    - report validation errors with a full application stack  $effFullStackValidationError

"""

  // arguments for the programs
  case class Args(dbName: String, topic: String)

  // our datatype for the database

  case class Todo(id: Int, topic: String, done: Boolean)

  // the db operations

  private def createTransactor(dbName: String): Task[Transactor[Task]] =
    H2Transactor[Task](s"jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1", "sa", "")

  private def createTable(): ConnectionIO[Int] =
    sql"""
      CREATE TABLE
        todo (
          id    INT PRIMARY KEY AUTO_INCREMENT,
          topic VARCHAR(255),
          done  BOOLEAN
        )
    """.update.run

  private def createTodo(topic: String): ConnectionIO[Int] =
    sql"""
      INSERT INTO
        todo (topic, done)
      VALUES
        ($topic, FALSE)
    """.update.withUniqueGeneratedKeys[Int]("id")

  private def findTodo(id: Int): ConnectionIO[Option[Todo]] =
    sql"""
      SELECT
        id, topic, done
      FROM
        todo
      WHERE
        id = $id
    """.query[Todo].option

  private def markTodoDone(id: Int): ConnectionIO[Int] =
    sql"""
      UPDATE
        todo
      SET
        done = TRUE
      WHERE
        id = $id
    """.update.run

  // to run all the operations in one transaction,
  // we can compose them together into one ConnectionIO

  private def singleTransaction(topic: String): ConnectionIO[Option[Todo]] =
    for {
      _    <- createTable()
      id   <- createTodo(topic)
      _    <- markTodoDone(id)
      todo <- findTodo(id)
    } yield todo

  // Not valid sql, to test crashes

  private def queryCrash(): ConnectionIO[Int] =
    sql"""
      THIS IS CLEARLY NOT VALID SQL
    """.update.run

  // runs each query in it's own transaction

  def effStackTest = {

    type R[A] = Reader[Args, A]
    type S = R |: Task |: NoEffect

    val doobieProgram: Eff[S, Option[Todo]] = for {
      args <- ask[S, Args]
      xa   <- doTask[S, Transactor[Task]] (createTransactor(args.dbName))
      _    <- doTask[S, Int]              (createTable().transact(xa))
      id   <- doTask[S, Int]              (createTodo(args.topic).transact(xa))
      _    <- doTask[S, Int]              (markTodoDone(id).transact(xa))
      todo <- doTask[S, Option[Todo]]     (findTodo(id).transact(xa))
    } yield todo

    val args = Args(
      "eff_many_tx",
      "Use EFF to run one transaction for each query"
    )

    doobieProgram
      .runReader(args)
      .attemptTask(1.second)
      .run ==== \/.right(Some(Todo(1, args.topic, true)))

  }

  // runs all queries together in one megatransaction

  def effTxStackTest = {

    type R[A] = Reader[Args, A]
    type S = R |: Task |: NoEffect

    val doobieProgram: Eff[S, Option[Todo]] = for {
      args <- ask[S, Args]
      xa   <- doTask[S, Transactor[Task]](createTransactor(args.dbName))
      todo <- doTask[S, Option[Todo]](singleTransaction(args.topic).transact(xa))
    } yield todo

    val args = Args(
      "eff_single_tx",
      "Use EFF to run one single transaction for all queries"
    )

    doobieProgram
      .runReader(args)
      .attemptTask(1.second)
      .run ==== \/.right(Some(Todo(1, args.topic, true)))

  }

  // runs a query which blows up

  def effCrash = {

    type R[A] = Reader[Args, A]
    type S = R |: Task |: NoEffect

    val doobieProgram: Eff[S, Int] = for {
      args <- ask[S, Args]
      xa   <- doTask[S, Transactor[Task]](createTransactor(args.dbName))
      todo <- doTask[S, Int](queryCrash().transact(xa))
    } yield todo

    val args = Args(
      "eff_crash",
      "Use EFF to run query which crashes, to demonstrate error handling"
    )

    doobieProgram
      .runReader(args)
      .attemptTask(1.second)
      .run.isLeft ==== true

  }

  object FullStack {

    type Stack = Reader[Args, ?] |: Validate[String, ?] |: Task |: NoEffect
    type S = Stack // shorthand

    private val doobieProgram: Eff[S, Option[Todo]] = for {
      args   <- ask[S, Args]
      _      <- ValidateEffect.validateCheck[S, String](args.dbName.nonEmpty, "db name must not be empty")
      _      <- ValidateEffect.validateCheck[S, String](args.topic.nonEmpty, "topic must not be empty")
      xa     <- doTask[S, Transactor[Task]](createTransactor(args.dbName))
      todo   <- doTask[S, Option[Todo]](singleTransaction(args.topic).transact(xa))
    } yield todo

    def runStack(args: Args) =
      FullStack.doobieProgram
        .runReader(args)
        .runNel
        .attemptTask(1.second)
        .run

  }

  def effFullStack = {

    val args = Args(
      "eff_validate",
      "Use EFF to run one single transaction for all queries"
    )

    FullStack.runStack(args) ==== \/.right(\/.right(Some(Todo(1, args.topic, true))))

  }

  def effFullStackValidationError = {

    val args = Args(
      "",
      ""
    )

    FullStack.runStack(args) ==== \/.right(\/.left(NonEmptyList("db name must not be empty", "topic must not be empty")))
      
  }


}

