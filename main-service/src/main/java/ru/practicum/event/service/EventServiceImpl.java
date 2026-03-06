package ru.practicum.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.client.StatClient;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.*;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.handler.exception.BadRequestException;
import ru.practicum.handler.exception.ConflictException;
import ru.practicum.handler.exception.NotFoundException;
import ru.practicum.request.model.RequestState;
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

        List<Long> eventIds = events.getContent().stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        Map<Long, Long> confirmedMap = getConfirmedRequestsBatch(eventIds);
        Map<Long, Long> viewsMap = getEventsViews(events.getContent());

        return events.getContent().stream()
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

        return eventMapper.toEventFullDto(event, getViews(eventId),
                getConfirmedRequests(eventId));
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

        updateEventFields(event, updateRequest.getAnnotation(), updateRequest.getCategory(),
                updateRequest.getDescription(), updateRequest.getEventDate(), updateRequest.getLocation(),
                updateRequest.getPaid(), updateRequest.getParticipantLimit(),
                updateRequest.getRequestModeration(), updateRequest.getTitle());

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == StateActionUser.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else {
                event.setState(EventState.CANCELED);
            }
        }

        return eventMapper.toEventFullDto(eventRepository.save(event), getViews(eventId),
                getConfirmedRequests(eventId));
    }

    @Override
    public List<EventFullDto> getEventsByAdminFilters(EventParams params) {
        Pageable pageable = PageRequest.of(params.getPageParams().getFrom() / params.getPageParams().getSize(),
                params.getPageParams().getSize());

        Page<Event> events = eventRepository.findEventsByAdminFilters(
                params.getUsers(), params.getStates(), params.getCategories(),
                params.getRangeStart(), params.getRangeEnd(), pageable);

        List<Long> eventIds = events.getContent().stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        Map<Long, Long> confirmedMap = getConfirmedRequestsBatch(eventIds);
        Map<Long, Long> viewsMap = getEventsViews(events.getContent());

        return events.getContent().stream()
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

        updateEventFields(event, updateRequest.getAnnotation(), updateRequest.getCategory(),
                updateRequest.getDescription(), updateRequest.getEventDate(), updateRequest.getLocation(),
                updateRequest.getPaid(), updateRequest.getParticipantLimit(),
                updateRequest.getRequestModeration(), updateRequest.getTitle());

        return eventMapper.toEventFullDto(eventRepository.save(event), getViews(eventId),
                getConfirmedRequests(eventId));
    }

    @Override
    public List<EventShortDto> getEventsByPublicFilters(PublicEventParams params, HttpServletRequest request) {
        LocalDateTime start = params.getRangeStart();
        LocalDateTime end = params.getRangeEnd();

        if (start == null && end == null) {
            start = LocalDateTime.now();
        }

        String text = (params.getText() != null && !params.getText().isBlank())
                ? params.getText() : null;

        int pageNum = params.getPageParams().getFrom() / params.getPageParams().getSize();
        Pageable pageable = PageRequest.of(pageNum, params.getPageParams().getSize(),
                Sort.by(Sort.Direction.ASC, "eventDate"));

        if ("VIEWS".equals(params.getSort())) {
            return getEventsSortedByViews(params, start);
        }

        Page<Event> eventsPage = eventRepository.findEventsByPublicFilters(
                text,
                params.getCategories(),
                params.getPaid(),
                start,
                end,
                pageable);

        List<Event> events = eventsPage.getContent();
        Map<Long, Long> viewsMap = getEventsViews(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsBatch(
                events.stream().map(Event::getId).collect(Collectors.toList())
        );

        return events.stream()
                .map(event -> eventMapper.toEventShortDto(
                        event,
                        viewsMap.getOrDefault(event.getId(), 0L),
                        confirmedMap.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private List<EventShortDto> getEventsSortedByViews(PublicEventParams params, LocalDateTime rangeStart) {
        Pageable allRecords = PageRequest.of(0, Integer.MAX_VALUE);

        Page<Event> eventsPage = eventRepository.findEventsByPublicFilters(
                params.getText(), params.getCategories(), params.getPaid(),
                rangeStart, params.getRangeEnd(), allRecords);

        List<Event> events = eventsPage.getContent();
        Map<Long, Long> viewsMap = getEventsViews(events);
        Map<Long, Long> confirmedMap = getConfirmedRequestsBatch(
                events.stream().map(Event::getId).collect(Collectors.toList())
        );

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

        return eventMapper.toEventFullDto(event, getViews(eventId),
                getConfirmedRequests(eventId));
    }

    @Override
    public void saveStats(HttpServletRequest request) {
        try {
            String uri = request.getRequestURI();
            if (uri.endsWith("/")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            statClient.saveHit(uri, request.getRemoteAddr());
        } catch (RestClientException e) {
            log.error("Ошибка при сохранении статистики: {}", e.getMessage());
        }
    }

    private Long getViews(Long eventId) {
        Event event = new Event();
        event.setId(eventId);
        Map<Long, Long> map = getViewsBatch(List.of(event));
        return map.getOrDefault(eventId, 0L);
    }

    private Map<Long, Long> getViewsBatch(List<Event> events) {
        if (events == null || events.isEmpty()) return Collections.emptyMap();

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
            return result;
        } catch (RestClientException e) {
            log.error("Ошибка при обращении к сервису статистики: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<Long, Long> getEventsViews(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyMap();

        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .collect(Collectors.toList());

        try {
            List<ViewStatsDto> stats = statClient.getStats(
                    LocalDateTime.of(2020, 1, 1, 0, 0),
                    LocalDateTime.now().plusYears(10),
                    uris,
                    true);

            return stats.stream()
                    .filter(s -> s.getUri().startsWith("/events/"))
                    .collect(Collectors.toMap(
                            s -> Long.parseLong(s.getUri().substring("/events/".length())),
                            ViewStatsDto::getHits,
                            (existing, replacement) -> existing
                    ));
        } catch (RestClientException e) {
            log.warn("Не удалось получить статистику просмотров: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<Long, Long> getConfirmedRequestsBatch(List<Long> eventIds) {
        if (eventIds.isEmpty()) return Collections.emptyMap();
        try {
            List<Object[]> counts = requestRepository.countConfirmedRequestsByEventIds(eventIds, RequestState.CONFIRMED);
            Map<Long, Long> result = new HashMap<>();
            for (Object[] row : counts) {
                Long eventId = (Long) row[0];
                Long count = (Long) row[1];
                result.put(eventId, count);
            }
            return result;
        } catch (DataAccessException e) {
            log.error("Ошибка при подсчете подтвержденных заявок: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Long getConfirmedRequests(Long eventId) {
        return getConfirmedRequestsBatch(List.of(eventId)).getOrDefault(eventId, 0L);
    }

    private void updateEventFields(Event event, String annotation, Long categoryId,
                                   String description, LocalDateTime eventDate,
                                   ru.practicum.location.dto.Location location, Boolean paid,
                                   Integer participantLimit, Boolean requestModeration, String title) {

        if (annotation != null && !annotation.isBlank()) event.setAnnotation(annotation);
        if (categoryId != null) event.setCategory(checkCategoryExists(categoryId));
        if (description != null && !description.isBlank()) event.setDescription(description);
        if (eventDate != null) event.setEventDate(eventDate);
        if (location != null)
            event.setLocation(new ru.practicum.location.model.LocationEntity(location.getLat(), location.getLon()));
        if (paid != null) event.setPaid(paid);
        if (participantLimit != null) event.setParticipantLimit(participantLimit);
        if (requestModeration != null) event.setRequestModeration(requestModeration);
        if (title != null && !title.isBlank()) event.setTitle(title);
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