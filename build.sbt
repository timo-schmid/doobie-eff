
lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:_",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yinline-warnings",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)

lazy val `eff-doobie` = project.in(file("."))
  .settings(
    scalacOptions := commonScalacOptions,
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      "org.atnos"     %%  "eff-scalaz"             %  "1.7.1"     % "test",
      "org.atnos"     %%  "eff-scalaz-concurrent"  %  "1.7.1"     % "test",
      "org.tpolecat"  %%  "doobie-core"            %  "0.3.0-M1"  % "test",
      "org.tpolecat"  %%  "doobie-contrib-h2"      %  "0.3.0-M1"  % "test",
      "io.argonaut"   %%  "argonaut-scalaz"        %  "6.2-M1"    % "test",
      "org.specs2"    %%  "specs2-core"            %  "3.7.3"     % "test"
    )
  )

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")

addCompilerPlugin("com.milessabin" % "si2712fix-plugin_2.11.8" % "1.2.0")

