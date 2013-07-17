import com.typesafe.sbt.osgi.OsgiKeys
import sbt._
import Keys._
import java.io.PrintWriter
import scala._

object Build extends Build {
  import BuildSettings._
  import Dependencies._

  // configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Root Project
  // -------------------------------------------------------------------------------------------------------------------

  lazy val root = Project("root",file("."))
    .aggregate(docs, examples, sprayCaching, sprayCan, sprayCanTests, sprayClient, sprayHttp, sprayHttpx,
      sprayIO, sprayIOTests, sprayOsgi, sprayRouting, sprayRoutingTests, sprayServlet, sprayTestKit, sprayUtil)
    .settings(basicSettings: _*)
    .settings(noPublishing: _*)


  // -------------------------------------------------------------------------------------------------------------------
  // Modules
  // -------------------------------------------------------------------------------------------------------------------

  lazy val sprayCaching = Project("spray-caching", file("spray-caching"))
    .dependsOn(sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      compile(clHashMap) ++
      test(specs2)
    )


  lazy val sprayCan = Project("spray-can", file("spray-can"))
    .dependsOn(sprayIO, sprayHttp, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaTestKit, specs2)
    )


  lazy val sprayCanTests = Project("spray-can-tests", file("spray-can-tests"))
    .dependsOn(sprayCan, sprayHttp, sprayHttpx, sprayIO, sprayTestKit, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(noPublishing: _*)
    .settings(libraryDependencies ++= test(akkaActor, akkaTestKit, specs2))


  lazy val sprayClient = Project("spray-client", file("spray-client"))
    .dependsOn(sprayCan, sprayHttp, sprayHttpx, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      test(akkaTestKit, specs2)
    )


  lazy val sprayHttp = Project("spray-http", file("spray-http"))
    .settings(sprayModuleSettings: _*)
    .settings(libraryDependencies ++=
      compile(parboiled) ++
      test(specs2)
    )


  lazy val sprayHttpx = Project("spray-httpx", file("spray-httpx"))
    .dependsOn(sprayHttp, sprayUtil,
      sprayIO) // for access to akka.io.Tcp, can go away after upgrade to Akka 2.2
    .settings(sprayModuleSettings: _*)
    .settings(libraryDependencies ++=
      compile(mimepull) ++
      provided(akkaActor, sprayJson, twirlApi, liftJson, json4sNative, json4sJackson) ++
      test(specs2)
    )


  lazy val sprayIO = Project("spray-io", file("spray-io"))
    .dependsOn(sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(libraryDependencies ++= provided(akkaActor, scalaReflect))


  lazy val sprayIOTests = Project("spray-io-tests", file("spray-io-tests"))
    .dependsOn(sprayIO, sprayTestKit, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(noPublishing: _*)
    .settings(libraryDependencies ++= test(akkaActor, akkaTestKit, specs2, scalatest))

  lazy val osgiBundledProjects: Seq[Project] = Seq(sprayCaching, sprayCan, sprayClient, sprayHttp, sprayHttpx, sprayIO, sprayRouting, sprayServlet, sprayTestKit, sprayUtil)
  
  lazy val sprayOsgi = Project("spray-osgi", file("spray-osgi"))
    .dependsOn(osgiBundledProjects.map(_.project: ClasspathDep[ProjectReference]):_*)
    .settings(sprayModuleSettings: _*)
    .settings(
      ActorOsgiConfigurationReference <<= ActorOsgiConfigurationReferenceAction(osgiBundledProjects),
      ActorMakeOsgiConfiguration <<= (ActorOsgiConfigurationReference, resourceManaged in Compile, streams) map makeOsgiConfigurationFiles,
      resourceGenerators in Compile <+= ActorMakeOsgiConfiguration
    )
    .settings(osgiSettings(exports = Seq("akka.spray", "spray.caching", "spray.can", "spray.client", "spray.http", "spray.httpx", "spray.io", "spray.osgi", "spray.routing", "spray.servlet", "spray.testkit", "spray.util")): _*)
    .settings(libraryDependencies ++= compile(osgiCore, akkaSlf4j, akkaOsgi, tsConfig))

  lazy val sprayRouting = Project("spray-routing", file("spray-routing"))
    .dependsOn(
      sprayCaching % "provided", // for the CachingDirectives trait
      sprayCan % "provided",  // for the SimpleRoutingApp trait
      sprayHttp, sprayHttpx, sprayUtil,
      sprayIO) // for access to akka.io.Tcp, can go away after upgrade to Akka 2.2
    .settings(sprayModuleSettings: _*)
    .settings(spray.boilerplate.BoilerplatePlugin.Boilerplate.settings: _*)
    .settings(libraryDependencies ++=
      compile(shapeless) ++
      provided(akkaActor)
    )


  lazy val sprayRoutingTests = Project("spray-routing-tests", file("spray-routing-tests"))
    .dependsOn(sprayCaching, sprayHttp, sprayHttpx, sprayRouting, sprayTestKit, sprayUtil)
    .settings(sprayModuleSettings: _*)
    .settings(noPublishing: _*)
    .settings(libraryDependencies ++= test(akkaActor, akkaTestKit, specs2, shapeless, sprayJson))


  lazy val sprayServlet = Project("spray-servlet", file("spray-servlet"))
    .dependsOn(sprayHttp, sprayUtil,
      sprayIO) // for access to akka.io.Tcp, can go away after upgrade to Akka 2.2
    .settings(sprayModuleSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor, servlet30) ++
      test(specs2)
    )


  lazy val sprayTestKit = Project("spray-testkit", file("spray-testkit"))
    .dependsOn(
      sprayHttp % "provided",
      sprayHttpx % "provided",
      sprayIO % "provided",
      sprayRouting % "provided",
      sprayUtil
    )
    .settings(sprayModuleSettings: _*)
    .settings(libraryDependencies ++= provided(akkaActor, akkaTestKit, scalatest, specs2))


  lazy val sprayUtil = Project("spray-util", file("spray-util"))
    .settings(sprayModuleSettings: _*)
    .settings(sprayVersionConfGeneration: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor, scalaReflect) ++
      test(akkaTestKit, specs2)
    )


  // -------------------------------------------------------------------------------------------------------------------
  // Site Project
  // -------------------------------------------------------------------------------------------------------------------

  lazy val docs = Project("docs", file("docs"))
    .dependsOn(sprayCaching, sprayCan, sprayClient, sprayHttp, sprayHttpx, sprayIO, sprayRouting,
               sprayServlet, sprayTestKit, sprayUtil)
    .settings(SphinxSupport.settings: _*)
    .settings(docsSettings: _*)
    .settings(libraryDependencies ++= test(akkaActor, akkaTestKit, sprayJson, specs2, json4sNative))


  // -------------------------------------------------------------------------------------------------------------------
  // Example Projects
  // -------------------------------------------------------------------------------------------------------------------

  lazy val examples = Project("examples", file("examples"))
    .aggregate(sprayCanExamples, sprayClientExamples, sprayIOExamples, sprayRoutingExamples, sprayServletExamples)
    .settings(exampleSettings: _*)

  lazy val sprayCanExamples = Project("spray-can-examples", file("examples/spray-can"))
    .aggregate(serverBenchmark, simpleHttpClient, simpleHttpServer)
    .settings(exampleSettings: _*)

  lazy val serverBenchmark = Project("server-benchmark", file("examples/spray-can/server-benchmark"))
    .dependsOn(sprayCan, sprayHttp)
    .settings(benchmarkSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor, sprayJson) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val simpleHttpClient = Project("simple-http-client", file("examples/spray-can/simple-http-client"))
    .dependsOn(sprayCan, sprayHttp)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val simpleHttpServer = Project("simple-http-server", file("examples/spray-can/simple-http-server"))
    .dependsOn(sprayCan, sprayHttp)
    .settings(standaloneServerExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val sprayClientExamples = Project("spray-client-examples", file("examples/spray-client"))
    .aggregate(simpleSprayClient)
    .settings(exampleSettings: _*)

  lazy val simpleSprayClient = Project("simple-spray-client", file("examples/spray-client/simple-spray-client"))
    .dependsOn(sprayClient)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor, sprayJson) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val sprayIOExamples = Project("spray-io-examples", file("examples/spray-io"))
    .aggregate(echoServerExample)
    .settings(exampleSettings: _*)

  lazy val echoServerExample = Project("echo-server", file("examples/spray-io/echo-server"))
    .dependsOn(sprayIO)
    .settings(standaloneServerExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val sprayRoutingExamples = Project("spray-routing-examples", file("examples/spray-routing"))
    .aggregate(onJetty, onSprayCan, onKaraf, simpleRoutingApp)
    .settings(exampleSettings: _*)

  lazy val onJetty = Project("on-jetty", file("examples/spray-routing/on-jetty"))
    .dependsOn(sprayCaching, sprayServlet, sprayRouting, sprayTestKit % "test")
    .settings(jettyExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      test(specs2) ++
      runtime(akkaSlf4j, logback) ++
      container(jettyWebApp, servlet30)
    )

  lazy val onKaraf = Project("on-karaf", file("examples/spray-routing/on-karaf"))
      .dependsOn(sprayOsgi, sprayCaching, sprayServlet, sprayRouting, sprayTestKit % "test")
      .settings(sprayModuleSettings ++ Seq(OsgiKeys.bundleActivator := Option("spray.examples.Activator")) ++ Seq(OsgiKeys.exportPackage := Seq("spray.examples")): _*)
      .settings(libraryDependencies ++=
          compile(akkaActor) ++
            test(specs2) ++
            runtime(akkaSlf4j, logback)
        )

  lazy val onSprayCan = Project("on-spray-can", file("examples/spray-routing/on-spray-can"))
    .dependsOn(sprayCaching, sprayCan, sprayRouting, sprayTestKit % "test")
    .settings(standaloneServerExampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      test(specs2) ++
      runtime(akkaSlf4j, logback)
    )

  lazy val simpleRoutingApp = Project("simple-routing-app", file("examples/spray-routing/simple-routing-app"))
    .dependsOn(sprayCan, sprayRouting)
    .settings(standaloneServerExampleSettings: _*)
    .settings(libraryDependencies ++= compile(akkaActor))

  lazy val sprayServletExamples = Project("spray-servlet-examples", file("examples/spray-servlet"))
    .aggregate(simpleSprayServletServer)
    .settings(exampleSettings: _*)

  lazy val simpleSprayServletServer = Project("simple-spray-servlet-server",
                                              file("examples/spray-servlet/simple-spray-servlet-server"))
    .dependsOn(sprayHttp, sprayServlet,
      sprayIO) // for access to akka.io.Tcp, can go away after upgrade to Akka 2.2
    .settings(jettyExampleSettings: _*)
    .settings(exampleSettings: _*)
    .settings(libraryDependencies ++=
      compile(akkaActor) ++
      runtime(akkaSlf4j, logback) ++
      container(jettyWebApp, servlet30)
    )


  // -------------------------------------------------------------------------------------------------------------------
  // Configuration copy tasks
  // -------------------------------------------------------------------------------------------------------------------

  lazy val ActorMakeOsgiConfiguration = TaskKey[Seq[File]]("actor-make-osgi-configuration", "Copy reference.conf from akka modules for akka-osgi")
  lazy val ActorOsgiConfigurationReference = TaskKey[Seq[(File, String)]]("actor-osgi-configuration-reference", "The list of all configuration files to be bundled in an osgi bundle, as well as project name.")

  import Project.Initialize
  /** This method uses a bit of advanced sbt initailizers to grab the normalized names and resource directories
   * from a set of projects, and then use this to create a mapping of (reference.conf to project name).
   */
  def ActorOsgiConfigurationReferenceAction(projects: Seq[Project]): Initialize[Task[Seq[(File, String)]]] = {
    val directories: Initialize[Seq[File]] = projects.map(resourceDirectory in Compile in _).join
    val names: Initialize[Seq[String]] = projects.map(normalizedName in _).join
    directories zip names map { case (dirs, ns) =>
        for {
          (dir, project) <- dirs zip ns
          val conf = dir / "reference.conf"
          if conf.exists
        } yield conf -> project
      }
  }
  /** This method is repsonsible for genreating a new typeasafe config reference.conf file for OSGi.
   * it copies all the files in the `includes` parameter, using the associated project name.  Then
   * it generates a new reference.conf file which includes these files.
   *
   * @param target  The location where we write the new files
   * @param includes A sequnece of (<reference.conf>, <project name>) pairs.
   */
  def makeOsgiConfigurationFiles(includes: Seq[(File, String)], target: File, streams: TaskStreams): Seq[File] = {
    // First we copy all the files to their destination
    val toCopy =
      for {
        (file, project) <- includes
        val toFile = target / (project + ".conf")
      } yield file -> toFile
    IO.copy(toCopy)
    val copiedResourceFileLocations = toCopy.map(_._2)
    streams.log.debug("Copied OSGi resources: " + copiedResourceFileLocations.mkString("\n\t", "\n\t", "\n"))
    // Now we generate the new including conf file
    val newConf = target / "reference.conf"
    val confIncludes =
      for {
        (file, project) <- includes
      } yield "include \""+ project +".conf\""
    val writer = new PrintWriter(newConf)
    try writer.write(confIncludes mkString "\n")
    finally writer.close()
    streams.log.info("Copied OSGi resources.")
    newConf +: copiedResourceFileLocations
  }
}
