package ru.practicum.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.client.StatClient;
import ru.practicum.comments.repository.CommentRepository;
import ru.practicum.dto.*;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.*;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.handler.exception.*;
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
    private final CommentRepository commentRepository; // 👈 ДОБАВИЛИ
    private final EventMapper eventMapper;
    private final StatClient statClient;

    // ===================== USER =====================

    @Override
    public List<EventShortDto> getEvents(Long userId, Pageable pageable) {
        checkUserExists(userId);

        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable).getContent();
        if (events.isEmpty()) return List.of();

        Map<Long, Long> views = getEventViews(events);
        Map<Long, Long> requests = getConfirmedRequestsBatch(events);
        Map<Long, Long> comments = getCommentsBatch(events);

        return events.stream()
                .map(e -> eventMapper.toEventShortDto(
                        e,
                        views.getOrDefault(e.getId(), 0L),
                        requests.getOrDefault(e.getId(), 0L),
                        comments.getOrDefault(e.getId(), 0L)
                ))
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto postEvent(Long userId, NewEventDto dto) {
        validateEventDate(dto.getEventDate(), 2);

        User user = checkUserExists(userId);
        Category category = checkCategoryExists(dto.getCategory());

        Event event = eventMapper.toEvent(dto, category, user);
        Event saved = eventRepository.save(event);

        return eventMapper.toEventFullDto(saved, 0L, 0L, 0L);
    }

    @Override
    public EventFullDto getEvent(Long userId, Long eventId) {
        checkUserExists(userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        return enrich(event);
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

        Event saved = eventRepository.save(event);

        return enrich(saved); // 👈 уже с comments
    }

    private void updateEventFields(Event event, UpdateEventUserRequest req) {

        if (req.getAnnotation() != null && !req.getAnnotation().isBlank()) {
            event.setAnnotation(req.getAnnotation());
        }

        if (req.getCategory() != null) {
            event.setCategory(checkCategoryExists(req.getCategory()));
        }

        if (req.getDescription() != null && !req.getDescription().isBlank()) {
            event.setDescription(req.getDescription());
        }

        if (req.getEventDate() != null) {
            event.setEventDate(req.getEventDate());
        }

        if (req.getLocation() != null) {
            event.setLocation(new ru.practicum.location.model.LocationEntity(
                    req.getLocation().getLat(),
                    req.getLocation().getLon()
            ));
        }

        if (req.getPaid() != null) {
            event.setPaid(req.getPaid());
        }

        if (req.getParticipantLimit() != null) {
            event.setParticipantLimit(req.getParticipantLimit());
        }

        if (req.getRequestModeration() != null) {
            event.setRequestModeration(req.getRequestModeration());
        }

        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            event.setTitle(req.getTitle());
        }
    }

    // ===================== ADMIN =====================

    @Override
    public List<EventFullDto> getEventsByAdminFilters(EventParams params) {
        Pageable pageable = PageRequest.of(
                params.getPageParams().getFrom() / params.getPageParams().getSize(),
                params.getPageParams().getSize()
        );

        List<Event> events = eventRepository.findEventsByAdminFilters(
                params.getUsers(),
                params.getStates(),
                params.getCategories(),
                params.getRangeStart(),
                params.getRangeEnd(),
                pageable
        ).getContent();

        if (events.isEmpty()) return List.of();

        Map<Long, Long> views = getEventViews(events);
        Map<Long, Long> requests = getConfirmedRequestsBatch(events);
        Map<Long, Long> comments = getCommentsBatch(events);

        return events.stream()
                .map(e -> eventMapper.toEventFullDto(
                        e,
                        views.getOrDefault(e.getId(), 0L),
                        requests.getOrDefault(e.getId(), 0L),
                        comments.getOrDefault(e.getId(), 0L)
                ))
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto patchEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (updateRequest.getEventDate() != null) {
            validateEventDate(updateRequest.getEventDate(), 1);
        }

        if (updateRequest.getStateAction() != null) {

            if (updateRequest.getStateAction() == StateActionAdmin.PUBLISH_EVENT) {

                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish event");
                }

                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());

            } else {

                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject published event");
                }

                event.setState(EventState.CANCELED);
            }
        }

        updateEventFields(event, updateRequest);

        Event saved = eventRepository.save(event);

        return enrich(saved); // 👈 с comments
    }

    private void updateEventFields(Event event, UpdateEventAdminRequest req) {

        if (req.getAnnotation() != null && !req.getAnnotation().isBlank()) {
            event.setAnnotation(req.getAnnotation());
        }

        if (req.getCategory() != null) {
            event.setCategory(checkCategoryExists(req.getCategory()));
        }

        if (req.getDescription() != null && !req.getDescription().isBlank()) {
            event.setDescription(req.getDescription());
        }

        if (req.getEventDate() != null) {
            event.setEventDate(req.getEventDate());
        }

        if (req.getLocation() != null) {
            event.setLocation(new ru.practicum.location.model.LocationEntity(
                    req.getLocation().getLat(),
                    req.getLocation().getLon()
            ));
        }

        if (req.getPaid() != null) {
            event.setPaid(req.getPaid());
        }

        if (req.getParticipantLimit() != null) {
            event.setParticipantLimit(req.getParticipantLimit());
        }

        if (req.getRequestModeration() != null) {
            event.setRequestModeration(req.getRequestModeration());
        }

        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            event.setTitle(req.getTitle());
        }
    }

    // ===================== PUBLIC =====================

    @Override
    public List<EventShortDto> getEventsByPublicFilters(PublicEventParams params, HttpServletRequest request) {

        Pageable pageable = PageRequest.of(
                params.getPageParams().getFrom() / params.getPageParams().getSize(),
                params.getPageParams().getSize(),
                Sort.by("eventDate")
        );

        List<Event> events = eventRepository.findEventsByPublicFilters(
                params.getText(),
                params.getCategories(),
                params.getPaid(),
                params.getRangeStart(),
                params.getRangeEnd(),
                pageable
        ).getContent();

        if (events.isEmpty()) return List.of();

        Map<Long, Long> views = getEventViews(events);
        Map<Long, Long> requests = getConfirmedRequestsBatch(events);
        Map<Long, Long> comments = getCommentsBatch(events);

        return events.stream()
                .map(e -> eventMapper.toEventShortDto(
                        e,
                        views.getOrDefault(e.getId(), 0L),
                        requests.getOrDefault(e.getId(), 0L),
                        comments.getOrDefault(e.getId(), 0L)
                ))
                .toList();
    }

    @Override
    public EventFullDto getEventById(Long eventId, HttpServletRequest request) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event must be published");
        }

        return enrich(event); // 👈 comments + views + requests
    }

    // ===================== CORE =====================

    private EventFullDto enrich(Event event) {
        List<Event> list = List.of(event);

        Map<Long, Long> views = getEventViews(list);
        Map<Long, Long> requests = getConfirmedRequestsBatch(list);
        Map<Long, Long> comments = getCommentsBatch(list);

        return eventMapper.toEventFullDto(
                event,
                views.getOrDefault(event.getId(), 0L),
                requests.getOrDefault(event.getId(), 0L),
                comments.getOrDefault(event.getId(), 0L)
        );
    }

    // ===================== BATCH =====================

    private Map<Long, Long> getCommentsBatch(List<Event> events) {
        if (events.isEmpty()) return Map.of();

        List<Long> ids = events.stream().map(Event::getId).toList();

        return commentRepository.countApprovedCommentsByEventIds(ids)
                .stream()
                .collect(Collectors.toMap(
                        r -> (Long) r[0],
                        r -> (Long) r[1]
                ));
    }

    private Map<Long, Long> getConfirmedRequestsBatch(List<Event> events) {
        if (events.isEmpty()) return Map.of();

        List<Long> ids = events.stream().map(Event::getId).toList();

        return requestRepository.countConfirmedRequestsByEventIds(ids)
                .stream()
                .collect(Collectors.toMap(
                        r -> (Long) r[0],
                        r -> (Long) r[1]
                ));
    }

    private Map<Long, Long> getEventViews(List<Event> events) {
        if (events.isEmpty()) return Map.of();

        Map<String, Long> map = events.stream()
                .collect(Collectors.toMap(
                        e -> "/events/" + e.getId(),
                        Event::getId
                ));

        ViewsStatsRequest req = ViewsStatsRequest.builder()
                .start(LocalDateTime.of(2000,1,1,0,0))
                .end(LocalDateTime.now())
                .uris(map.keySet())
                .unique(true)
                .build();

        List<ViewStatsDto> stats = statClient.getStats(req);

        return stats.stream()
                .collect(Collectors.toMap(
                        s -> map.get(s.getUri()),
                        ViewStatsDto::getHits
                ));
    }

    // ===================== VALIDATION =====================

    private User checkUserExists(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Category checkCategoryExists(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private void validateEventDate(LocalDateTime date, int hours) {
        if (date != null && date.isBefore(LocalDateTime.now().plusHours(hours))) {
            throw new BadRequestException("Event date too early");
        }
    }
}