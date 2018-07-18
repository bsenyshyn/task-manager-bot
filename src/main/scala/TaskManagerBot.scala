import dao.{Tasks, Users}
import mongo.Helpers
import timing.Timing
import com.typesafe.config.ConfigFactory
import info.mukel.telegrambot4s.api.TelegramBot
import info.mukel.telegrambot4s.Implicits._
import info.mukel.telegrambot4s.api.declarative.{Callbacks, Commands}
import info.mukel.telegrambot4s.api.Polling
import info.mukel.telegrambot4s.methods.{EditMessageReplyMarkup, SendMessage}
import info.mukel.telegrambot4s.models.{ChatId, InlineKeyboardButton, InlineKeyboardMarkup}
import org.bson.types.ObjectId

import scala.io.Source
import scala.io.Source

import mongo.Connection._

object TaskManagerBot extends TelegramBot with Polling with Commands with Callbacks with Timing with Helpers{
  def token = "TOKEN"

  val start = "Hello!\n\nI'm your personal task manager. Manage your tasks" +
    " and make your day more organized.\n\n" +
    "You can create different tasks: simple, temporary, everyday." +
    " When you create a task you have an opportunity to watch it" +
    " and gave it complete status.\nTurn on notifications to get notified!\n\n" +
    "Use /help to see all the commands for the bot!"

  val help = "Examples of adding: \n\n[/add simple task, s]\n - it's simple task\n\n" +
    "[/add everyday task, 00-24, 3]\n - it's an everyday task, where '00-24' is a hours range " +
    "in which you will receive notifications, and '3' is the number of notifications per day\n\n" +
    "[/add temporary task, 12:00]\n - it's a task, where '12:00' is a time when" +
    " you will get the notifications\n\n" +
    "[/add precise temporary task, 01-01-1970/12:00]\n - it's also a temporary task, but with more precise," +
    " specified date.\n\n" +
    "Other commands: \n\n" +
    "/start - starting the bot\n" +
    "/finish - finishing the task\n" +
    "/delete - deleting the task\n" +
    "/notifications - turning on/off notifications\n" +
    "/list - listing tasks\n" +
    "/status - checking whether notifications are turned on/off\n\n" +
    "Enjoy!"


  val notExist = "Use /start to begin working with me!"

  val show = "show"
  val update = "update"
  val delete = "delete"
  val notifications = "notifications"

  def showTag = prefixTag(show) _
  def updateTag = prefixTag(update) _
  def deleteTag = prefixTag(delete) _
  def notificationsTag = prefixTag(notifications) _

  //BOT START AND HELP COMMANDS

  onCommand('start) { implicit msg =>
    users.createUsers(msg)
    reply(start)
  }


  onCommand('help) { implicit msg =>
    if(users.exist(msg.source))
      reply(help)
    else
      reply(notExist)
  }

  //ADD TASK

  onCommand('add) { implicit msg =>
    if(users.exist(msg.source))
      withArgs{ args =>
        val task = args.mkString(" ")
        reply(tasks.check(task, msg))
      }
    else
      reply(notExist)
  }


  //LIST ALL TASKS

  def listTask(chatId: Long) = {
    InlineKeyboardMarkup.singleColumn(Seq(
      InlineKeyboardButton.callbackData(
        "Show all tasks",
        showTag("false")),
      InlineKeyboardButton.callbackData(
        "Show all unfinished task",
        showTag("true")
      )))
  }

  onCommand('list) { implicit msg =>
    if(users.exist(msg.source)) {
      if(tasks.countTasks(msg.source) >= 1)
        reply("Choose one variant",
          replyMarkup = listTask(msg.source))
      else
        reply("No tasks to list")
    }
    else
      reply(notExist)
  }

  onCallbackWithTag(show) { implicit cbq =>
    for {
      data <- cbq.data.get.toBoolean
      msg <- cbq.message
    } {
      request(
        SendMessage(
          msg.source,
          tasks.colToString(tasks.readAll(msg.source,data))
        )
      )
    }
  }

  //UPDATE TASKS STATUS

  def updateStatus(chatId: Long) = {
    InlineKeyboardMarkup.singleColumn(
      tasks.readAll(chatId,false).map{
        t => InlineKeyboardButton(
          s"task with text: ${t.text}",
          updateTag(t._id.toString))
      }
    )
  }

  onCommand('finish) { implicit msg =>
    if(users.exist(msg.source)) {
      if(tasks.countTasks(msg.source) >= 1)
        reply("Choose task which status you want to change",
          replyMarkup = updateStatus(msg.source))
      else
        reply("No tasks to finish, use /add command to add some or /help to watch examples")
    }
    else
      reply(notExist)
  }


  onCallbackWithTag(update) { implicit cbq =>
    ackCallback(s"${cbq.message.get.chat.firstName.get} has finished task!")

    for {
      msg <- cbq.message
    } {
      tasks.finishTask(new ObjectId(cbq.data.get))
      EditMessageReplyMarkup(
        ChatId(msg.source),
        msg.messageId,
        replyMarkup = updateStatus(msg.source)
      )
    }
  }


  //DELETE TASK

  def deleteTask(chatId: Long) = {
    InlineKeyboardMarkup.singleColumn(
      tasks.readAll(chatId,false).map{
        t => InlineKeyboardButton(
          s"task with text: ${t.text}",
          deleteTag(t._id.toString))
      }
    )
  }

  onCommand('delete) { implicit msg =>
    if(users.exist(msg.source)) {
      if(tasks.countTasks(msg.source) >= 1)
        reply("Choose task you want to delete",
          replyMarkup = deleteTask(msg.source))
      else
        reply("No tasks to delete, use /add command to add some or /help to watch examples")
    }
    else
      reply(notExist)
  }

  onCallbackWithTag(delete) { implicit cbq =>
    println(cbq.data.get)
    ackCallback(s"${cbq.from.firstName} has deleted task!")

    for{
      msg <- cbq.message
    } {
      tasks.delete(new ObjectId(cbq.data.get))
      EditMessageReplyMarkup(
        ChatId(msg.source),
        msg.messageId,
        replyMarkup = deleteTask(msg.source)
      )
    }
  }


  //Turning on/off notifications

  def changeUserStatus(chatId: Long) = {
    InlineKeyboardMarkup.singleButton(
      InlineKeyboardButton.callbackData(
        s"${users.getCallbackKeyboard(chatId)}",
        notificationsTag(users.getNotificationStatus(chatId).toString)))
  }

  onCommand('notifications) { implicit msg =>
    if(users.exist(msg.source))
      reply("Press to change your notifications status",
        replyMarkup = changeUserStatus(msg.chat.id))
    else
      reply(notExist)
  }

  onCallbackWithTag(notifications) { implicit cbq =>
    ackCallback(users.getCallbackMessage(cbq.message.get))

    for {
      msg <- cbq.message
    } {
      if(users.getNotificationStatus(msg.source))
        users.update(msg.source, false)
      else
        users.update(msg.source, true)

      request(
        EditMessageReplyMarkup(
          ChatId(msg.source), // msg.chat.id
          msg.messageId,
          replyMarkup = changeUserStatus(msg.source)))
    }
  }

  onCommand('status) { implicit msg =>
    if(users.exist(msg.source)) {
      val status = users.getNotificationStatus(msg.chat.id)
      status match {
        case true => reply("Notifications are turned on")
        case false => reply("Notifications are turned off")
      }
    }
    else
      reply(notExist)
  }
}
