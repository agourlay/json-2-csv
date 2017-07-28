import com.typesafe.sbt.SbtScalariform

import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

name := "json-2-csv"

organization := "com.github.agourlay"
homepage := Some(url("https://github.com/agourlay/json-2-csv"))
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
organizationHomepage := Some(url("https://github.com/agourlay/json-2-csv"))
developers := Developer("agourlay", "Arnaud Gourlay", "", url("https://github.com/agourlay")) :: Nil
scmInfo := Some(ScmInfo(
  browseUrl = url("https://github.com/agourlay/json-2-csv.git"),
  connection = "scm:git:git@github.com:agourlay/json-2-csv.git"
))

releasePublishArtifactsAction := PgpKeys.publishSigned.value
publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := (_ => false)
publishTo := Some(
  if (version.value.trim.endsWith("SNAPSHOT"))
    "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

scalaVersion := "2.12.3"
crossScalaVersions := Seq("2.11.11", "2.12.3")

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "UTF-8",
  "-Ywarn-dead-code",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-feature",
  "-Ywarn-unused-import"
)

fork in Test := true

javaOptions in Test ++= Seq("-Xmx1G")

SbtScalariform.scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(AlignParameters, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(RewriteArrowSymbols, true)

libraryDependencies ++= {
  val commonsIoV = "2.5"
  val scalaTestV = "3.0.3"
  val jawnV      = "0.10.4"
  val scalaCsvV  = "1.3.4"
  Seq(
     "org.spire-math"       %% "jawn-ast"   % jawnV
    ,"com.github.tototoshi" %% "scala-csv"  % scalaCsvV
    ,"commons-io"           %  "commons-io" % commonsIoV % "test"
    ,"org.scalatest"        %% "scalatest"  % scalaTestV % "test"
  )
}
