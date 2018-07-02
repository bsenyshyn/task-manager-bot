package Models

import java.util.Date

import org.mongodb.scala.bson.ObjectId

/*
Structure
- id
- chat id
- text of task
- its status
- time range for task (if needed)
- repeats for the task (if needed)
*/

case class Task(_id: ObjectId,
                chatId: Long,
                text: String,
                status: Boolean,
                date: Option[Date] = None,
                timeSpan: Option[String] = None,
                iterations: Option[String] = None)
