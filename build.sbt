import scalariform.formatter.preferences._

name := "json2CsvStream"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.4"

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
  val scalaTestV = "2.2.3"
  val jawnV      = "0.7.1"
  val scalaCsvV  = "1.1.2"
  val logbackV   = "1.1.2"
  Seq(
     "org.spire-math"       %% "jawn-ast"        % jawnV
    ,"com.github.tototoshi" %% "scala-csv"       % scalaCsvV
    ,"ch.qos.logback"       %  "logback-classic" % logbackV
    ,"commons-io"           %  "commons-io"      % commonsIoV % "test"
    ,"org.scalatest"        %% "scalatest"       % scalaTestV % "test"
  )
}
