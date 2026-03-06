package ru.practicum.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.client.StatClient;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.model.StateActionAdmin;
import ru.practicum.event.model.StateActionUser;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.handler.exception.BadRequestException;
import ru.practicum.handler.exception.ConflictException;
import ru.practicum.handler.exception.NotFoundException;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final EventMapper eventMapper;
    private final StatClient statClient;

    @Override
    public List<EventShortDto> getEvents(Long userId, Pageable pageable) {
        checkUserExists(userId);
        Page<Event> events = eventRepository.findAllByInitiatorId(userId, pageable);
        List<Event> eventList = events.getContent();

        if (eventList.isEmpty()) {
            return Collections.emptyList();
        }

        // Пакетное получение всех данных
        Map<Long, Long> viewsMap = getEventsViews(eventList);
        Map<Long, Long> confirmedMap = getConfirmedRequestsBatch(eventList);

        return eventList.stream()
                .map(event -> eventMapper.toEventShortDto(
                        event,
                        viewsMap.getOrDefault(event.getId(), 0L),
                        confirmedMap.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto postEvent(Long userId, NewEventDto newEventDto) {
        validateEventDate(newEventDto.getEventDate(), 2);
        User user = checkUserExists(userId);
        Category category = checkCategoryExists(newEventDto.getCategory());
        Event event = eventMapper.toEvent(newEventDto, category, user);
        Event savedEvent = eventRepository.save(event);
        return eventMapper.toEventFullDto(savedEvent, 0L, 0L);
    }

    @Override
    public EventFullDto getEvent(Long userId, Long eventId) {
        checkUserExists(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found or not accessible"));

        // Получаем все данные одним запросом для этого события
        return enrichEventWithStats(event);
    }

    @Override
    @Transactional
    public EventFullDto patchEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        checkUserExists(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        if (updateRequest.getEventDate() != null) {
            validateEventDate(updateRequest.getEventDate(), 2);
        }

        updateEventFields(event, updateRequest);

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == StateActionUser.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else {
                event.setState(EventState.CANCELED);
            }
        }

        Event savedEvent = eventRepository.save(event);
        return enrichEventWithStats(savedEvent);
    }

    @Override
    public List<EventFullDto> getEventsByAdminFilters(EventParams params) {
        Pageable pageable = PageRequest.of(
                params.getPageParams().getFrom() / params.getPageParams().getSize(),
                params.getPageParams().getSize()
        );

        Page<Event> eventsPage = eventRepository.findEventsByAdminFilters(
                params.getUsers(),
                params.getStates(),
                params.getCategories(),
                params.getRangeStart(),
                params.getRangeEnd(),
                pageable
        );

        List<Event> events = eventsPage.getContent();
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        // Пакетное получение всех данных
        Map<Long, Long> viewsMap = getEventsViews(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsBatch(events);

        return events.stream()
                .map(event -> eventMapper.toEventFullDto(
                        event,
                        viewsMap.getOrDefault(event.getId(), 0L),
                        confirmedMap.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto patchEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = checkEventExists(eventId);

        if (updateRequest.getEventDate() != null) {
            validateEventDate(updateRequest.getEventDate(), 1);
        }

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == StateActionAdmin.PUBLISH_EVENT) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish event because it's not in PENDING state");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject event because it's already published");
                }
                event.setState(EventState.CANCELED);
            }
        }

        updateEventFields(event, updateRequest);
        Event savedEvent = eventRepository.save(event);
        return enrichEventWithStats(savedEvent);
    }

    @Override
    public List<EventShortDto> getEventsByPublicFilters(PublicEventParams params, HttpServletRequest request) {
        LocalDateTime start = params.getRangeStart();
        LocalDateTime end = params.getRangeEnd();

        if (start == null && end == null) {
            start = LocalDateTime.now();
        }

        String text = (params.getText() != null && !params.getText().isBlank()) ? params.getText() : null;
        int pageNum = params.getPageParams().getFrom() / params.getPageParams().getSize();
        Pageable pageable = PageRequest.of(pageNum, params.getPageParams().getSize(), Sort.by(Sort.Direction.ASC, "eventDate"));

        if ("VIEWS".equals(params.getSort())) {
            return getEventsSortedByViews(params, start);
        }

        try {
            Page<Event> eventsPage = eventRepository.findEventsByPublicFilters(
                    text,
                    params.getCategories(),
                    params.getPaid(),
                    start,
                    end,
                    pageable
            );

            List<Event> events = eventsPage.getContent();
            if (events.isEmpty()) {
                return Collections.emptyList();
            }

            // Пакетное получение всех данных
            Map<Long, Long> viewsMap = getEventsViews(events);
            Map<Long, Long> confirmedMap = getConfirmedRequestsBatch(events);

            return events.stream()
                    .map(event -> eventMapper.toEventShortDto(
                            event,
                            viewsMap.getOrDefault(event.getId(), 0L),
                            confirmedMap.getOrDefault(event.getId(), 0L)))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in public filters: {}", e.getMessage());
            throw e;
        }
    }

    private List<EventShortDto> getEventsSortedByViews(PublicEventParams params, LocalDateTime rangeStart) {
        Pageable allRecords = PageRequest.of(0, Integer.MAX_VALUE);
        Page<Event> eventsPage = eventRepository.findEventsByPublicFilters(
                params.getText(),
                params.getCategories(),
                params.getPaid(),
                rangeStart,
                params.getRangeEnd(),
                allRecords
        );

        List<Event> events = eventsPage.getContent();
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        // Пакетное получение всех данных
        Map<Long, Long> viewsMap = getEventsViews(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsBatch(events);

        return events.stream()
                .map(event -> eventMapper.toEventShortDto(
                        event,
                        viewsMap.getOrDefault(event.getId(), 0L),
                        confirmedMap.getOrDefault(event.getId(), 0L)))
                .sorted(Comparator.comparing(EventShortDto::getViews).reversed())
                .skip(params.getPageParams().getFrom())
                .limit(params.getPageParams().getSize())
                .collect(Collectors.toList());
    }

    @Override
    public EventFullDto getEventById(Long eventId, HttpServletRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event must be published");
        }

        return enrichEventWithStats(event);
    }

    @Override
    public void saveStats(HttpServletRequest request) {
        try {
            String uri = request.getRequestURI();
            if (uri.endsWith("/")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            statClient.saveHit(uri, request.getRemoteAddr());
        } catch (Exception e) {
            log.error("Ошибка при сохранении статистики: {}", e.getMessage());
        }
    }

    private Map<Long, Long> getConfirmedRequestsBatch(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<Long> eventIds = events.stream()
                    .map(Event::getId)
                    .collect(Collectors.toList());

            List<Object[]> results = requestRepository.countConfirmedRequestsByEventIds(eventIds);

            return results.stream()
                    .collect(Collectors.toMap(
                            result -> (Long) result[0],
                            result -> (Long) result[1]
                    ));
        } catch (Exception e) {
            log.error("Ошибка при пакетном подсчете заявок: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<Long, Long> getEventsViews(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        LocalDateTime start = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.now().plusYears(10);

        try {
            List<ViewStatsDto> stats = statClient.getStats(start, end, uris, true);
            Map<Long, Long> result = new HashMap<>();

            if (stats != null) {
                for (ViewStatsDto dto : stats) {
                    String uri = dto.getUri();
                    try {
                        if (uri.startsWith("/events/")) {
                            String idStr = uri.substring("/events/".length());
                            if (idStr.contains("/")) {
                                idStr = idStr.substring(0, idStr.indexOf("/"));
                            }
                            Long id = Long.parseLong(idStr);
                            result.put(id, dto.getHits());
                        }
                    } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                        log.warn("Не удалось извлечь ID события из URI: {}", uri);
                    }
                }
            }

            // Убеждаемся, что для всех событий есть значение (даже 0)
            for (Event event : events) {
                result.putIfAbsent(event.getId(), 0L);
            }

            return result;
        } catch (Exception e) {
            log.error("Ошибка при обращении к сервису статистики: {}", e.getMessage());

            // Возвращаем нули для всех событий в случае ошибки
            return events.stream()
                    .collect(Collectors.toMap(
                            Event::getId,
                            event -> 0L
                    ));
        }
    }

    private EventFullDto enrichEventWithStats(Event event) {
        List<Event> singleEventList = Collections.singletonList(event);
        Map<Long, Long> viewsMap = getEventsViews(singleEventList);
        Map<Long, Long> confirmedMap = getConfirmedRequestsBatch(singleEventList);

        return eventMapper.toEventFullDto(
                event,
                viewsMap.getOrDefault(event.getId(), 0L),
                confirmedMap.getOrDefault(event.getId(), 0L)
        );
    }

    private void updateEventFields(Event event, UpdateEventUserRequest updateRequest) {
        if (updateRequest.getAnnotation() != null && !updateRequest.getAnnotation().isBlank()) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            event.setCategory(checkCategoryExists(updateRequest.getCategory()));
        }
        if (updateRequest.getDescription() != null && !updateRequest.getDescription().isBlank()) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLocation(new ru.practicum.location.model.LocationEntity(
                    updateRequest.getLocation().getLat(),
                    updateRequest.getLocation().getLon()
            ));
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    private void updateEventFields(Event event, UpdateEventAdminRequest updateRequest) {
        if (updateRequest.getAnnotation() != null && !updateRequest.getAnnotation().isBlank()) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            event.setCategory(checkCategoryExists(updateRequest.getCategory()));
        }
        if (updateRequest.getDescription() != null && !updateRequest.getDescription().isBlank()) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLocation(new ru.practicum.location.model.LocationEntity(
                    updateRequest.getLocation().getLat(),
                    updateRequest.getLocation().getLon()
            ));
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    private User checkUserExists(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }

    private Category checkCategoryExists(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category " + catId + " not found"));
    }

    private Event checkEventExists(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event " + eventId + " not found"));
    }

    private void validateEventDate(LocalDateTime eventDate, int hours) {
        if (eventDate != null && eventDate.isBefore(LocalDateTime.now().plusHours(hours))) {
            throw new BadRequestException("Event date too early");
        }
    }
}