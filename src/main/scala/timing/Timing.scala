package timing

import java.text.SimpleDateFormat
import java.util.Date

trait Timing {
  //Formatter for hours, minutes, seconds
  val timeFormatter = new SimpleDateFormat("k:mm")
  //Formatter for full date
  val dateFormatter = new SimpleDateFormat("dd-MM-yyyy/k:mm")

  // Comparing task date and current date
  def compareDate(task: Date, now: Date): Date = {
    //Getting date for the task (add 3 for Ukraine time zone)
    task.setYear(now.getYear)
    task.setMonth(now.getMonth)
    task.setHours(task.getHours)

    //Comparing with current time with task time and setting the right date
    if (task.getTime > now.getTime) {
      task.setDate(now.getDate)
    }
    else task.setDate(now.getDate)

    task
  }

  // Getting date from string
  def getTimeFormat(dateStr: String): Date = {

    //Making length comparison to choose the right formatter
    dateStr match {
      case dateStr: String if dateStr.length <= 5 =>
        val date = compareDate(timeFormatter.parse(dateStr), new Date())
        date
      case dateStr: String if dateStr.length >= 15 =>
        val date: Date = dateFormatter.parse(dateStr)
        date
    }
  }
}
