package models

import org.mongodb.scala.bson.ObjectId

/*
Structure
- id
- chat id
- on/off notifications
 */

case class User(_id: ObjectId,
                chatId: Long,
                notifications: Boolean)
