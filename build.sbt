import scalariform.formatter.preferences._

name := "json2CsvStream"

jarName in assembly := "jsonToCsv.jar"

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

javaOptions in Test ++= Seq("-Xmx2G", "-XX:-UseGCOverheadLimit")

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
  val scalaTestV = "2.2.2"
  val jawnV      = "0.7.1"
  val scalaCsvV  = "1.1.2"
  val logbackV   = "1.1.2"
  Seq(
     "commons-io"           % "commons-io"       % commonsIoV
    ,"org.spire-math"       %% "jawn-ast"        % jawnV
    ,"com.github.tototoshi" %% "scala-csv"       % scalaCsvV
    ,"ch.qos.logback"       %  "logback-classic" % logbackV
    ,"org.scalatest"        %% "scalatest"       % scalaTestV % "test"
  )
}
