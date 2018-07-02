val root = Project("task-manager-bot",file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.12.6",
    libraryDependencies ++= Seq(
      "info.mukel" %% "telegrambot4s" % "3.0.15",
      "org.mongodb.scala" %% "mongo-scala-driver" % "2.3.0",
      "com.typesafe" % "config" % "1.3.2"
    )
  )