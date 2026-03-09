package ru.practicum.statservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.statservice.mapper.EndpointHitMapper;
import ru.practicum.statservice.model.EndpointHit;
import ru.practicum.statservice.repository.StatRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatServiceImpl implements StatService {
    private final StatRepository repository;
    private final EndpointHitMapper mapper;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public void saveHit(EndpointHitDto hitDto) {
        EndpointHit hit = mapper.mapToEndpointHit(hitDto);
        repository.save(hit);
    }

    @Override
    public List<ViewStatsDto> getStats(String start, String end,
                                       List<String> uris, boolean unique) {

        try {
            LocalDateTime startTime = LocalDateTime.parse(start, FORMATTER);
            LocalDateTime endTime = LocalDateTime.parse(end, FORMATTER);

            if (endTime.isBefore(startTime)) {
                throw new IllegalArgumentException("End date must be after start date");
            }

            if (uris == null || uris.isEmpty()) {
                if (unique) {
                    return repository.findUniqueHitsAll(startTime, endTime);
                } else {
                    return repository.findAllHitsAll(startTime, endTime);
                }
            } else {
                if (unique) {
                    return repository.findUniqueHitsByUris(startTime, endTime, uris);
                } else {
                    return repository.findAllHitsByUris(startTime, endTime, uris);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error while getting stats", e);
        }
    }

    @Override
    public List<EndpointHit> getAllHits() {
        return repository.findAll();
    }
}