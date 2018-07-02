import DAO.{Tasks, Users}
import Mongo.Helpers
import Timing.Timing
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

object TaskManagerBot extends TelegramBot with Polling with Commands with Callbacks with Timing with Helpers{
  val config = ConfigFactory.parseResources("application.conf")
  def token = config.getString("tg.token")

  val start = "start"
  val help = "help"
  val notExist = "notExist"
  val show = "SHOW_TAG"

  def showTag = prefixTag(show) _

  //BOT START AND HELP COMMANDS

  onCommand('start) { implicit msg =>
    Users.createUsers(msg)
    reply(start)
  }


  onCommand('help) { implicit msg =>
    if(Users.exist(msg.source))
      reply(help)
    else
      reply(notExist)
  }

  //ADD TASK

  onCommand('add) { implicit msg =>
    if(Users.exist(msg.source))
      withArgs{ args =>
        val task = args.toString()
        reply(Tasks.check(task))
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
    if(Users.exist(msg.source)) {
      if(Tasks.countTasks(msg.source) >= 1)
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
          Tasks.colToString(Tasks.readAll(msg.source,data))
        )
      )
    }
  }

}
