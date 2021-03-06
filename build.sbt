lazy val doobieVersion = "0.4.4"

lazy val baseSettings = Seq(
  scalaVersion := "2.12.4",
  organization := "com.devim",
  resolvers ++= Seq(
    "Sonatype Nexus" at "https://nexus.devim.team/repository/maven-public/",
    "Central Proxy " at "https://nexus.devim.team/repository/maven-central/",
    "Releases" at "https://nexus.devim.team/repository/maven-releases/",
    "Snapshots" at "https://nexus.devim.team/repository/maven-snapshots/"
  )
)

lazy val publishSettings = Seq(
  publishArtifact in Test := false,
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://nexus.devim.team/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "repository/maven-snapshots/")
    else
      Some("releases" at nexus + "repository/maven-releases/")
  },
  credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
)

lazy val doobiePostgresProto = (project in file("."))
  .settings(baseSettings)
  .settings(publishSettings)
  .settings(
    name := "doobie-postgres-proto",
    libraryDependencies ++= Seq(
      "com.devim" %% "proto-utils" % "1.1.7",
      //doobie (database)
      "org.tpolecat" %% "doobie-core-cats" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres-cats" % doobieVersion,
      //test
      "com.dimafeng" %% "testcontainers-scala" % "0.7.0" % "test",
      "org.testcontainers" % "postgresql" % "1.4.3" % "test",
      "org.scalatest" %% "scalatest" % "3.0.4" % "test",
      "org.tpolecat" %% "doobie-scalatest-cats" % doobieVersion % "test"
    )
  )
