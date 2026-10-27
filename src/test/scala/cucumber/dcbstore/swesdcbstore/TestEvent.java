package cucumber.dcbstore.swesdcbstore;

// We need one record per value of the "Event Type" used in the DcbStore.feature specification.
// This is because the sliceworkz-eventstore registers each record under its name as a valid EventType name and later
// checks during appending if the events match the registered event types.
public sealed interface TestEvent {

    record StudentCreated(String name) implements TestEvent {
    }

    record CourseCreated(String name) implements TestEvent {
    }

    record StudentSubscribed(String name) implements TestEvent {
    }

    record StudentUnsubscribed(String name) implements TestEvent {
    }

    record StudentNameChanged(String name) implements TestEvent {
    }

}
