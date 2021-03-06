package dao

import java.util.Date

import models.Task
import mongo.{Counter, Helpers}
import timing.Timing
import info.mukel.telegrambot4s.models.{CallbackQuery, Message}
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.{MongoClient, MongoCollection}
import com.typesafe.config.ConfigFactory

/*
  Creating object for setting everything for MongoDb
  and creating CRUD operations.
 */

class Tasks(mongoClient: MongoClient, db: String) extends Helpers with Timing {

  //Applying current case class to mongo BsonDocument
  def apply(chatId: Long,
            text: String,
            status: Boolean,
            date: Option[Date] = None,
            timeSpan: Option[String] = None,
            iterations: Option[String] = None): Task =
    Task(new ObjectId(),
      chatId,
      text,
      status,
      date,
      timeSpan,
      iterations)

  private val taskCodecRegistry: CodecRegistry = fromRegistries(fromProviders(classOf[Task]), DEFAULT_CODEC_REGISTRY)
  private val database = mongoClient.getDatabase(db).withCodecRegistry(taskCodecRegistry)
  private val taskCollection: MongoCollection[Task] = database.getCollection("tasks")


  //CRUD OPERATIONS

  //Creating new task
  def create(task: Task): String = {
    if (taskCollection.insertOne(task).results() != Nil)
      "New task was added!"
    else
      "Error while adding task."
  }

  //Get (un)finished tasks
  def readAll(chatId: Long, finished: Boolean): Seq[Task] = {
    if (finished)
      taskCollection.find(equal("chatId", chatId)).filter(equal("status", false)).results()
    else
      taskCollection.find(equal("chatId", chatId)).results()
  }

  //Get one
  def read(_id: ObjectId): Task = taskCollection.find(equal("_id", _id)).results().head

  //Update task by id
  def update(oldValue: ObjectId, valueToUpdate: String, newValue: Any) =
    taskCollection.updateOne(equal("_id", oldValue), set(valueToUpdate, newValue))

  //Delete task
  def delete(_id: ObjectId) = taskCollection.deleteOne(equal("_id", _id)).printHeadResult("Result of deleting: ")

  //Delete all tasks
  def deleteAll(_id: ObjectId) = taskCollection.deleteMany(equal("_id", _id))

  //Finish task
  def finishTask(_id: ObjectId) =
    taskCollection.updateOne(equal("_id", _id), set("status", true)).printHeadResult("Finished task: ")

  //Deleting tasks which completed (further purposes)
  def deleteWhichCompleted = taskCollection.deleteMany(equal("status", true)).printResults()

  //Counting tasks in specific chat
  def countTasks(chatId: Long) = taskCollection.find(equal("chatId", chatId)).results().size


  //METHODS FOR FORMATTING COLLECTION AND DATE INTO STRING


  //Formatting date
  def readDate(date: Date): String =
    s"${date.getDate}-${date.getMonth + 1}-${date.getYear + 1900}/${date.getHours}:${date.getMinutes}"

  //Format collection to string
  def colToString(collection: Seq[Task]): String = {
    //Instance of counter from Helpers
    val counter = new Counter

    //Formatting different occasions
    if (collection.isEmpty) "No tasks found!"
    else {
      val tasks = collection.toList.map {
        case t if t.date.isDefined =>
          counter.add
          s"№${counter.get}\nText: ${t.text}\nDate: ${readDate(t.date.get)}\nStatus: ${readStatus(t.status)}\n\n"

        case t if t.timeSpan.isDefined =>
          counter.add
          s"№${counter.get}\nText: ${t.text}\nTime Span: ${t.timeSpan.get}\nIterates: " +
            s"${t.iterations.get}\nStatus: ${readStatus(t.status)}\n\n"


        case t if t.timeSpan.isEmpty && t.date.isEmpty =>
          counter.add
          s"№${counter.get}\nText: ${t.text}\nStatus: ${readStatus(t.status)}\n\n"
      }
      tasks.mkString
    }
  }


  //CALLBACKS

  def readStatus(state: Boolean): String =
    if (state) "finished"
    else "unfinished"

  def getDeleteCallback(cbq: CallbackQuery): String = s"${cbq.from.firstName} has deleted task with id: ${cbq.data.get}"

  def getFinishedCallback(msg: Message): String = s"${msg.chat.firstName.get} has finished task"

  /*
  CREATING DIFFERENT TASKS
  - Normal task - task without date, just a simple one
  - Time task - task with a deadline
  - Everyday task - task for every day with its time span and repeats
   */

  def createNormalTask(text: Array[String], msg: Message): String = {
    if (!text.forall(t => empty(t, t.indexOf(t)))) {
      val task = Task(new ObjectId(), msg.from.get.id, text(0), false)
      create(task)
    }
    else "Error while adding task."
  }

  def createTimeTask(text: Array[String], msg: Message) = {
    if (!text.forall(t => empty(t, t.indexOf(t)))) {
      val date = getTimeFormat(text(1))
      val task = Task(new ObjectId(), msg.from.get.id, text(0), false, Some(date))
      create(task)
    }
    else "Error while adding task."
  }

  def createEverydayTask(text: Array[String], msg: Message) = {
    if (!text.forall(t => empty(t, t.indexOf(t)))) {
      val task = Task(new ObjectId(), msg.from.get.id, text(0), false,
        None, Some(text(1)), Some(text(2)))
      create(task)
    }
    else "Error while adding task."
  }

  def empty(str: String, index: Int): Boolean =
    if (str.equals("") || str.equals(" ")) true
    else if (index == 1 && !str.contains("^[0-9]*$")) true
    else if (index == 2 && !str.contains("^[0-9]*$")) true
    else false

  def check(str: String, message: Message): String = {
    val arr = str.split(", ").drop(1)
    val text = str.split(", ")
    if (!arr.isEmpty){
      val first = arr(0)
      val len = arr.length
      if (len == 1){
        if(first.length > 10){
          if (first(10) == '/') createTimeTask(text, message)
          else "False!"
        }
        else if (first.length == 5){
          if (first(2) == ':') createTimeTask(text, message)
          else "False!"
        }
        else if (first == "s") createNormalTask(text, message)
        else "False!"
      }
      else if (len == 2) {
        if (first.length == 5) {
          val second = arr(1)
          val isDigit = second forall Character.isDigit

          if(first(2) == '-' && isDigit) {
            val range = first.split('-')
            val range1 = range(0).toInt
            val range2 = range(1).toInt
            if(range2 > range1) createEverydayTask(text, message)
            else "False!"
          }
          else "False!"
        }
        else "False!"
      }
      else "False!"
    }
    else "False!"
  }
}
