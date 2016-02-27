import scalariform.formatter.preferences._

name := "json-2-csv-stream"

organization := "com.github.agourlay"

version := "0.2.SNAPSHOT"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalaVersion := "2.11.7"

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

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(AlignParameters, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)
  .setPreference(RewriteArrowSymbols, true)

libraryDependencies ++= {
  val commonsIoV = "2.4"
  val scalaTestV = "2.2.6"
  val jawnV      = "0.8.4"
  val scalaCsvV  = "1.3.0"
  Seq(
     "org.spire-math"       %% "jawn-ast"   % jawnV
    ,"com.github.tototoshi" %% "scala-csv"  % scalaCsvV
    ,"commons-io"           %  "commons-io" % commonsIoV % "test"
    ,"org.scalatest"        %% "scalatest"  % scalaTestV % "test"
  )
}
