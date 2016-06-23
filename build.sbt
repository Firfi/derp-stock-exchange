enablePlugins(JavaAppPackaging)

name := """bid-reporter"""
organization := "com.loskutoff"
version := "1.0"
scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)

resolvers += Resolver.bintrayRepo("hseeberger", "maven")
resolvers += Resolver.bintrayRepo("ethereum", "maven")
resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies ++= {
  val akkaV       = "2.4.3"
  val scalaTestV  = "2.2.6"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaV,
    "de.heikoseeberger" %% "akka-http-circe" % "1.7.0",
    "com.pubnub"        %  "pubnub"          % "3.7.10",
    "com.github.adridadou"      %  "eth-contract-api"  %  "eth-contract-api-0.4",
    "org.scalatest"     %% "scalatest" % scalaTestV % "test"
  )
}

Revolver.settings
