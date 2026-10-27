package cucumber.dcbstore.swesdcbstore

import cucumber.dcbstore.DcbStoreStepDefinitions
import org.sliceworkz.eventstore.stream.EventStream

import scala.concurrent.ExecutionContext

abstract class SwEsDcbStoreStepDefinitions(eventStream: EventStream[JavaDataSuper])
  extends DcbStoreStepDefinitions(TestSwEsDcbStore(eventStream)(using ExecutionContext.global))
