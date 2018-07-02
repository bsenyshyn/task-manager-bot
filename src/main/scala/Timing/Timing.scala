package Timing

import java.text.SimpleDateFormat
import java.util.Date

trait Timing {
  //Formatter for hours, minutes, seconds
  val timeFormatter = new SimpleDateFormat("k:mm:ss")
  //Formatter for full date
  val dateFormatter = new SimpleDateFormat("dd-MM-yyyy/k:mm:ss")

  // Comparing task date and current date
  def compareDate(task: Date, now: Date): Date = {
    //Getting date for the task (add 3 for Ukraine time zone)
    task.setYear(now.getYear)
    task.setMonth(now.getMonth)
    task.setHours(task.getHours + 3)

    //Comparing with current time with task time and setting the right date
    if (task.getTime > now.getTime) {
      task.setDate(now.getDate)
    }
    else task.setDate(now.getDate + 1)

    task
  }

  // Getting date from string
  def getTimeFormat(dateStr: String): Date = {

    //Making length comparison to choose the right formatter
    dateStr match {
      case dateStr: String if dateStr.length <= 8 =>
        val date = compareDate(timeFormatter.parse(dateStr), new Date())
        date
      case dateStr: String if dateStr.length >= 18 =>
        val date: Date = dateFormatter.parse(dateStr)
        date
    }
  }
}
