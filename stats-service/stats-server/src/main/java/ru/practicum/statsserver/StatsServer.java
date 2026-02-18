package ru.practicum.statsserver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.EndpointHitDto;
import ru.practicum.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsServer {

    private final EndpointHitRepository repository;

    public void save(EndpointHitDto dto) {
        EndpointHit hit = new EndpointHit(dto.getApp(), dto.getUri(), dto.getIp(), dto.getTimestamp());
        repository.save(hit);
    }

    public List<ViewStatsDto> getStats(
            LocalDateTime start,
            LocalDateTime end,
            List<String> uris,
            boolean unique
    ) {
        List<Object[]> rows;

        if (uris == null || uris.isEmpty()) {
            rows = repository.findStatsAll(start, end);
        } else {
            rows = repository.findStatsByUris(start, end, uris, unique);
        }

        return rows.stream()
                .map(r -> new ViewStatsDto(
                        (String) r[0],
                        (String) r[1],
                        ((Number) r[2]).longValue()))
                .toList();
    }
}