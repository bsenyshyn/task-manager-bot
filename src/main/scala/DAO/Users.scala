package DAO

import java.util.Date

import Models.User
import Mongo.Helpers
import Timing.Timing
import com.typesafe.config.ConfigFactory
import info.mukel.telegrambot4s.models.Message
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.bson.{BsonDocument, ObjectId}
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.{MongoClient, MongoCollection}

/*
  Creating object for setting everything for MongoDb
  and creating CRUD operations.
 */

object Users extends App with Helpers with Timing{

  //Applying current case class to mongo BsonDocument
  def apply(chatId: Long,
            notifications: Boolean): User =
    User(new ObjectId(),
      chatId,
      notifications)

  //Setting up client and connection to MongoDB
  val config =  ConfigFactory.parseResources("application.conf")
  val driver = config.getString("mongodb.driver")
  val username = config.getString("mongodb.username")
  val password = config.getString("mongodb.password")
  val url = config.getString("mongodb.url")
  val port = config.getString("mongodb.port")
  val db = config.getString("mongodb.database")


  val mongoClient = MongoClient(s"$driver://$username:$password@$url:$port/$db")
  val userCodecRegistry: CodecRegistry = fromRegistries(fromProviders(classOf[User]), DEFAULT_CODEC_REGISTRY )
  val database = mongoClient.getDatabase(s"$db").withCodecRegistry(userCodecRegistry)
  val userCollection: MongoCollection[User] = database.getCollection("users")


  //CRUD OPERATIONS


  //Getting all users by id and limit
  def readAll(chatId: Long, limit: Int = 0): Seq[User] = {
    if (limit == 0)
      userCollection.find(equal("chatId", chatId)).results()

    else
      userCollection.find(equal("chatId", chatId)).limit(limit).results()
  }

  //Update notifications for user
  def update(oldValue: Boolean, newValue: Boolean) =
      userCollection.updateOne(equal("notifications", oldValue), set("notifications", newValue)).printHeadResult()

  //Check if user exists
  def exist(chatId: Long): Boolean = {
    if(userCollection.find(BsonDocument("chatId" -> chatId)).limit(1).results() != Nil) true
    else false
  }

  //Check which users has to be notified
  def userToNotify: Seq[User] = userCollection.find(equal("notifications",true)).results()

  //Check status for notifications
  def getNotificationStatus(chatId: Long): Boolean = readAll(chatId,1).map(t => t.notifications)
    .mkString("").toBoolean


  //CALLBACKS FOR NOTIFICATION STATUS

  //Change status text
  def getCallbackKeyboard(chatId: Long): String = {
    val status = getNotificationStatus(chatId)
    if (status) "Turn off"
    else "Turn on"
  }

  //Say that notifications are on
  def getCallbackMessage(msg: Message): String ={
    val status = getNotificationStatus(msg.source)
    if (status) s"${msg.chat.firstName.get} has switched off notifications"
    else s"${msg.chat.firstName.get} has switched on notifications"
  }

  //Status data
  def getCallbackData(chatId: Long): Boolean = {
    val status = getNotificationStatus(chatId)
    if (status) false
    else true
  }

  //Creating user (before checking if someone already exists)
  def createUsers(msg: Message): Unit = {
    if (exist(msg.chat.id).equals(true)) println(s"User with chat_id: ${msg.from.get.id} already exists")
    else {
      userCollection.insertOne(User(new ObjectId(), msg.from.get.id, false)).printHeadResult()
      println(s"User with chat id: ${msg.from.get.id} and username: ${msg.from.get.username.get} was created")
    }
  }
}
