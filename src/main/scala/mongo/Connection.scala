package mongo

import com.typesafe.config.ConfigFactory
import dao.{Tasks, Users}
import org.mongodb.scala.MongoClient

object Connection {
  //Setting up client and connection to MongoDB
  private val config =  ConfigFactory.parseResources("application.conf")
  private val driver = config.getString("mongodb.driver")
  private val username = config.getString("mongodb.username")
  private val password = config.getString("mongodb.password")
  private val url = config.getString("mongodb.url")
  private val port = config.getString("mongodb.port")
  private val db = config.getString("mongodb.database")

  private val mongoClient = MongoClient(s"$driver://$username:$password@$url:$port/$db")
  val tasks = new Tasks(mongoClient, db)
  val users = new Users(mongoClient, db)
}
