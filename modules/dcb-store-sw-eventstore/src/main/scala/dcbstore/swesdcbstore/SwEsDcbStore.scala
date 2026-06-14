package dcbstore.swesdcbstore

import dcbstore.*
import org.sliceworkz.eventstore.events.{EphemeralEvent, EventReference, Event as EsEvent, EventType as EsEventType, Tag as EsTag, Tags as EsTags}
import org.sliceworkz.eventstore.query.EventQuery.Direction
import org.sliceworkz.eventstore.query.{EventFilter, EventFilterItem, EventTypesFilter, Limit, EventQuery as EsEventQuery}
import org.sliceworkz.eventstore.stream.{AppendCriteria, EventStream, OptimisticLockingException}

import java.util as ju
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.{IteratorHasAsScala, SeqHasAsJava, SetHasAsJava, SetHasAsScala}
import scala.util.Using

// The EventType of this DcbStore implementation is fixed to String,
// as this is the type of org.sliceworkz.eventstore.events.EventType.name.
type EventType = String

trait SwEsDcbStore[DataSuper, JavaDataSuper]
  extends DcbStore[EventType, DataSuper]:

  /**
   * An [[EventStream]] registered with the [[JavaDataSuper]] class from which all "Event Type"s to be used could be
   * derived.
   */
  def eventStream: EventStream[JavaDataSuper]

  /**
   * Must be a Java class or interface, as the EventStore API uses Java classes. If it is a Java interface, then it must
   * be a _Java-sealed_ interface, as a requirement from how EventStore works to autodetect subclasses/records of it.
   * It needs to have one subclass/record per value of the "Event Type" that shall be appended to the event store.
   * This is because the sliceworkz-eventstore auto-detects from this [[JavaDataSuper]] type all subclasses/records,
   * generates their simple class names, and builds from them the correspsonding "Event Type" values. It will later only
   * accept appending events with "Event Type" values, which previously were registered.
   * That is: the sliceworkz-eventstore API does _not_ treat the "Event Data" to be opaque to the event store, but
   * rather hard-codes that the type of the "Event Data" determines the value of the corresponding "Event Type".
   */
  def javaDataSuperClass: Class[JavaDataSuper]

  /**
   * Maps Scala events to Java events.
   * For the [[DcbStore]] API the [[DataSuper]] type is opaque and not directly related to the [[EventType]] values.
   * But for the sliceworkz-eventstore store underlying this adapter the [[JavaDataSuper]] determines the allowed
   * [[EventType]] values. Thus, this mapping offers the [[EventType]] value as a hint to which Java class/record the
   * opaque Scala "Event Data" shall be mapped.
   *
   * @return The Java class/record for the "Event Data".
   */
  def mapScalaToJava(eventType: EventType)(data: DataSuper): JavaDataSuper

  def mapJavaToScala(data: JavaDataSuper): DataSuper

  given ExecutionContext = compiletime.deferred

  override opaque type SequencePosition = EventReference

  override type AppendConditionFailed = OptimisticLockingException

  // For the DcbStore API, the DataSuper type is opaque and not directly related to the EventType values.
  // But for the sliceworkz-eventstore store underlying this adapter, the JavaDataSuper determines the allowed
  // EventType values. Thus, the EventType value in a given event must match the possible values derived from
  // JavaDataSuper during store setup, else appending will fail.
  override def append[Data <: DataSuper](
                                          events: Events[EventType, Data],
                                          appendConditionO: Option[AppendCondition[EventType, SequencePosition]],
                                        ): Future[Unit] = Future:
    val _ = eventStream.append(esAppendCriteria(appendConditionO), esEphemeralEvents(events))

  override def read(query: Query[EventType])
  : Future[IterableOnce[SequencedEvent[EventType, DataSuper, SequencePosition]]] = Future:
    val eventQuery = esEventQuery(query)
    Using.resource(eventStream.query(eventQuery)): eventsJavaStream =>
      eventsJavaStream
        .iterator
        .asScala
        .map(sequencedEvent)
        // Terminates the iterator before closing the Java stream.
        // Unfortunately, we cannot translate the laziness of the Java stream into a Scala lazy IterableOnce, e.g.
        // LazyList, as we need to close the Java stream before returning the Scala IterableOnce.
        .toSeq

  private def esAppendCriteria(appendConditionO: Option[AppendCondition[EventType, SequencePosition]]): AppendCriteria =
    appendConditionO match
      case None =>
        AppendCriteria.none()
      case Some(AppendCondition(failIfEventsMatch, afterO)) =>
        AppendCriteria.of(
          esEventFilter(failIfEventsMatch),
          esEventReference(afterO),
        )

  private def esEventFilter(query: Query[EventType]): EventFilter =
    if query.queryItems.isEmpty
    then
      // For EventFilter, matchAll() matches all events, while EventFilter from an empty list matches no event!
      EventFilter.matchAll()
    else
      EventFilter(
        esEventFilterItems(query.queryItems),
        EventReference.none,
      )

  private def esEventFilterItems(queryItems: Set[QueryItem[EventType]]): ju.List[EventFilterItem] =
    queryItems.map(esEventFilterItem).toList.asJava

  private def esEventFilterItem(queryItem: QueryItem[EventType]): EventFilterItem =
    EventFilterItem(
      esEventTypesFilter(queryItem.eventTypes),
      esTags(queryItem.tags),
    )

  private def esEventTypesFilter(eventTypes: Set[EventType]): EventTypesFilter =
    EventTypesFilter(esEventTypes(eventTypes))

  private def esEventTypes(eventTypes: Set[EventType]): ju.Set[EsEventType] =
    eventTypes.map(esEsEventType).asJava

  private def esEsEventType(name: EventType): EsEventType =
    EsEventType(name)

  private def esEventReference(sequencePositionO: Option[SequencePosition]): EventReference =
    sequencePositionO match
      case None => EventReference.none
      case Some(sequencePosition) => sequencePosition

  private def esEphemeralEvents[Data <: DataSuper](events: Events[EventType, Data])
  : ju.List[EphemeralEvent[? <: JavaDataSuper]] =
    events.map(esEphemeralEvent).asJava

  private def esEphemeralEvent[Data <: DataSuper](event: Event[EventType, Data]): EphemeralEvent[JavaDataSuper] =
    EphemeralEvent[JavaDataSuper](
      esEsEventType(event.eventType),
      mapScalaToJava(event.eventType)(event.data),
      esTags(event.tags),
      null.asInstanceOf[String], // idempotency key as imposed by slizeworkz-eventstore API
    )

  private def esTags(tags: Tags): EsTags =
    EsTags(tags.map(esEsTag).asJava)

  private def esEsTag(tag: Tag): EsTag =
    EsTag(tag.key, tag.value)

  private def esEventQuery(query: Query[EventType]): EsEventQuery =
    EsEventQuery(
      esEventFilter(query),
      Direction.FORWARD,
      Limit.none(),
    )

  private def sequencedEvent(esEvent: EsEvent[JavaDataSuper]): SequencedEvent[EventType, DataSuper, SequencePosition] =
    SequencedEvent(
      event(esEvent),
      esEvent.reference,
    )

  private def event(esEvent: EsEvent[JavaDataSuper]): Event[EventType, DataSuper] =
    Event(
      esEvent.`type`.name,
      mapJavaToScala(esEvent.data),
      tags(esEvent.tags),
    )

  private def tags(esTags: EsTags): Tags =
    Set.from(esTags.tags.asScala).map(tag) // ju.Set.asScala yields a mutable Set, we need an immutable Set

  private def tag(esTag: EsTag): Tag =
    Tag(esTag.key, esTag.value)
