import java.util.Date

import dao.Tasks
import models.Task
import timing.Timing
import akka.actor.{Actor, ActorSystem, Cancellable, Props}
import info.mukel.telegrambot4s.methods.SendMessage
import org.mongodb.scala.bson.ObjectId
import TaskManagerBot.request
import java.time.Duration

import scala.concurrent.duration._

import mongo.Connection._

object Notifications extends Timing {

  //Actor system for notifications
  val system = ActorSystem("NotificationSystem")
  val notificationActor = system.actorOf(Props[NotificationActor], "NotificationActor")
  val receiver = system.actorOf(Props[Receiver], "Receiver")
  implicit val ec = system.dispatcher

  class NotificationActor extends Actor {

    //Map for scheduled tasks (yes, mutable :( )
    val scheduledTasks = collection.mutable.Map[ObjectId, Cancellable]()

    def receive = {

      //If actor receives task, it schedules it
      case task: Task if task.date.isDefined =>
        scheduledTasks += task._id -> scheduleTask(task)
        println("New task added to schedule ")

      case task: Task if task.timeSpan.isDefined && task.iterations.isDefined => for {
          time <- getScheduleTime(task.timeSpan.get, task.iterations.get.toInt)
        } {
        scheduledTasks += task._id -> scheduleEverydayTask(task,time)
      }

      //If actor receives id, then it finds the task and cancels it
      case id: ObjectId => scheduledTasks.filter(_._1 == id).map(_._2.cancel())
    }

    //Checks if the specific task is cancelled
    def status(id: ObjectId): Boolean = scheduledTasks.filter(_._1 == id).map(_._2.isCancelled).head

  }

  //Sends notification to user
  class Receiver extends Actor {
    def receive = {
      case task: Task =>
        println(task)
        request(SendMessage(task.chatId,
          getTaskNotification(task._id)))
    }
  }

  //Helper methods
  def getTaskNotification(taskId: ObjectId): String = {
    val task = tasks.read(taskId)
    new Date().getHours match {
      case hours: Int if hours < 12  => s"Good morning! Time to do this: ${task.text}!"
      case hours: Int if hours < 20 && hours >= 12  => s"Good afternoon! Time to do this: ${task.text}!"
      case hours: Int if hours >= 20 && hours < 24 => s"Good evening! Time to do this: ${task.text}!"
    }
  }

  def getScheduleTime(str: String, iterations: Int): Array[Date] = {
    val range = str.split("-").map(_.toInt)
    (range(0) to range(1)).toArray.filter(i => i % iterations == 0).map{ t => getTimeFormat(s"$t:00")}
  }

  def getDelay(date: Date): FiniteDuration = {
    Duration.between(new Date().toInstant, date.toInstant).getSeconds.seconds
  }

  def scheduleTask(task: Task): Cancellable = {
    println(getDelay(task.date.get))
    system.scheduler.scheduleOnce(getDelay(task.date.get), receiver,task)
  }

  def scheduleEverydayTask(task: Task, time: Date): Cancellable =
    system.scheduler.schedule(getDelay(time), 24.hours, receiver, task)
}