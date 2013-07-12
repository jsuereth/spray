/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package spray.osgi

import org.osgi.framework._
import akka.actor.{ Props, ActorSystem }
import akka.io.IO
import spray.can.Http
import akka.osgi.ActorSystemActivator
import com.typesafe.config.{ ConfigFactory, Config }

/**
 * Abstract bundle activator implementation to bootstrap and configure Spray in an
 * OSGi environment.  It also provides a convenience method to register Spray in
 * the OSGi Service Registry for sharing it with other OSGi bundles.
 *
 */
abstract class SprayActivator extends ActorSystemActivator {
  var props: Option[Props] = None
  var system: Option[ActorSystem] = None

  def configure(context: BundleContext, system: ActorSystem) {
    this.system = Some(system)
  }

  override def start(context: BundleContext) {
    super.start(context)
    val actorSRef = context.getServiceReference(classOf[ActorSystem].getName)
    implicit val system: ActorSystem = context.getService(actorSRef).asInstanceOf[ActorSystem]
    val handler = system.actorOf(props.get, name = "handler")

    IO(Http) ! Http.Bind(handler, interface = "localhost", port = 8080)
  }

  override def stop(context: BundleContext) {
    super.stop(context)
  }

  override def getActorSystemConfiguration(context: BundleContext): Config =
    ConfigFactory.load(context.getBundle.getClass.getClassLoader)
}
