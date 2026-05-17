package cucumber.dcbstore.swesdcbstore.inmem

import cucumber.dcbstore.swesdcbstore.inmem.SwEsInMemDcbStoreStepDefinitions.newEventStream
import cucumber.dcbstore.swesdcbstore.{JavaDataSuper, SwEsDcbStoreStepDefinitions}
import org.sliceworkz.eventstore.EventStore
import org.sliceworkz.eventstore.infra.inmem.InMemoryEventStorage
import org.sliceworkz.eventstore.stream.{EventStream, EventStreamId}

class SwEsInMemDcbStoreStepDefinitions
  extends SwEsDcbStoreStepDefinitions(newEventStream())


object SwEsInMemDcbStoreStepDefinitions:

  // Calling InMemoryEventStorage.newBuilder() actually gets a new instance of an event _storage_ Builder,
  // which, when calling .build(), creates a new _storage_ each time.
  // Here we call instead the _storage_ Builder.buildStore() method, which is a convenience method that calls .build()
  // for a new _storage_ and then builds a new _store_ from it.
  // As the storage/store is fresh each time, the EventStreamId can be the same each time.
  // The alternative would be to create a singleton storage/store and reuse it
  // and differentiate streams inside the store by stream ID.
  // But that would be more complex and less (memory) efficient for testing.
  def newEventStream(): EventStream[JavaDataSuper] =
    // build a storage and from it a store
    val eventStore: EventStore = InMemoryEventStorage.newBuilder().buildStore()
    val eventStreamId = EventStreamId.forContext("dcb-store-test") // with default purpose
    eventStore.getEventStream(eventStreamId, classOf[JavaDataSuper])
