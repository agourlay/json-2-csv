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
publishArtifact in Test := false
pomIncludeRepository := (_ => false)
publishTo := Some(
  if (version.value.trim.endsWith("SNAPSHOT"))
    "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

scalaVersion := "2.13.1"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "UTF-8",
  "-Ywarn-dead-code",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-feature",
  "-Ywarn-unused:imports"
)

fork in Test := true

javaOptions in Test ++= Seq("-Xmx1G")

testFrameworks += new TestFramework("utest.runner.Framework")

ScalariformKeys.preferences :=
  scalariformPreferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(DanglingCloseParenthesis, Preserve)

libraryDependencies ++= {
  val commonsIoV = "2.6"
  val utestV     = "0.7.1"
  val jawnV      = "0.14.2"
  val scalaCsvV  = "1.3.6"
  Seq(
     "org.typelevel"        %% "jawn-ast"   % jawnV
    ,"com.github.tototoshi" %% "scala-csv"  % scalaCsvV
    ,"commons-io"           %  "commons-io" % commonsIoV % Test
    ,"com.lihaoyi"          %% "utest"      % utestV     % Test
  )
}
