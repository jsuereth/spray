package spray.examples

import akka.actor.{ Props, ActorSystem }
import org.osgi.framework.BundleContext
import akka.event.{ LogSource, Logging }
import spray.osgi.SprayActivator

class Activator extends SprayActivator {
  import Activator._

  override def configure(context: BundleContext, system: ActorSystem) {
    props = Some(Props[DemoServiceActor])
    super.configure(context, system)

    val log = Logging(system, this)
    log.info("Core bundle configured")
    system.actorOf(props.get)
    registerService(context, system)
    //    registerHakkersService(context, system)
    log.info("Example service registered")
  }
}

object Activator {
  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }
}