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

// Publishing
releasePublishArtifactsAction := PgpKeys.publishSigned.value
publishMavenStyle := true
Test / publishArtifact := false
pomIncludeRepository := (_ => false)
publishTo := Some(
  if (version.value.trim.endsWith("SNAPSHOT"))
    "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

scalaVersion := "3.4.1"
crossScalaVersions := Seq(scalaVersion.value, "2.13.13")

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "UTF-8",
  "-Ywarn-dead-code",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-feature",
  "-Ywarn-unused:imports",
  "-Wnonunit-statement",
  "-Wperformance"
)

Test / fork := true
Test / javaOptions  ++= Seq("-Xmx1G")

ScalariformKeys.preferences :=
  scalariformPreferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(DanglingCloseParenthesis, Preserve)

libraryDependencies ++= {
  val commonsIoV = "2.15.1"
  val munitV     = "1.0.0-M11"
  val jawnV      = "1.5.1"
  val scalaCsvV  = "1.3.10"
  Seq(
     "org.typelevel"        %% "jawn-ast"   % jawnV
    ,"com.github.tototoshi" %% "scala-csv"  % scalaCsvV
    ,"commons-io"           %  "commons-io" % commonsIoV % Test
    ,"org.scalameta"        %% "munit"      % munitV     % Test
  )
}
