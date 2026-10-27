package cucumber.dcbstore.swesdcbstore

import cucumber.dcbstore.DataSuper
import dcbstore.swesdcbstore.{EventType, SwEsDcbStore}
import org.sliceworkz.eventstore.stream.EventStream

import scala.concurrent.ExecutionContext

type JavaDataSuper = TestEvent

// A Test SwEsDcbStore that is aligned with the domain of the tests for DcbStore.
trait TestSwEsDcbStore
  extends SwEsDcbStore[DataSuper, JavaDataSuper]:

  override val javaDataSuperClass: Class[JavaDataSuper] = classOf[JavaDataSuper]

  override def mapScalaToJava(eventType: EventType)(data: DataSuper): JavaDataSuper =
    eventType match
      case "StudentCreated" => TestEvent.StudentCreated(data)
      case "CourseCreated" => TestEvent.CourseCreated(data)
      case "StudentSubscribed" => TestEvent.StudentSubscribed(data)
      case "StudentUnsubscribed" => TestEvent.StudentUnsubscribed(data)
      case "StudentNameChanged" => TestEvent.StudentNameChanged(data)
      case _ => throw new IllegalStateException("should not happen")

  override def mapJavaToScala(data: JavaDataSuper): DataSuper =
    data match
      case event: TestEvent.StudentCreated => event.name
      case event: TestEvent.CourseCreated => event.name
      case event: TestEvent.StudentSubscribed => event.name
      case event: TestEvent.StudentUnsubscribed => event.name
      case event: TestEvent.StudentNameChanged => event.name


object TestSwEsDcbStore:

  def apply(_eventStream: EventStream[JavaDataSuper])(using ExecutionContext): TestSwEsDcbStore =
    new TestSwEsDcbStore:

      override val eventStream: EventStream[JavaDataSuper] = _eventStream

    end new
