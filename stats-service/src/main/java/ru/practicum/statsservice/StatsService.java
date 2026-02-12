package ru.practicum.statsservice;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.EndpointHitDto;
import ru.practicum.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final EndpointHitRepository repository;

    public void save(EndpointHitDto dto) {
        EndpointHit hit = new EndpointHit();
        hit.setApp(dto.getApp());
        hit.setUri(dto.getUri());
        hit.setIp(dto.getIp());
        hit.setTimestamp(dto.getTimestamp());
        repository.save(hit);
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        List<EndpointHit> hits = repository.findAllByTimestampBetween(start, end);

        Stream<EndpointHit> stream = hits.stream();
        if (uris != null && !uris.isEmpty()) {
            stream = stream.filter(h -> uris.contains(h.getUri()));
        }

        if (unique) {
            stream = stream.collect(Collectors.toMap(
                    h -> h.getApp() + h.getUri() + h.getIp(),
                    h -> h,
                    (h1, h2) -> h1
            )).values().stream();
        }

        return stream
                .collect(Collectors.groupingBy(
                        h -> h.getApp() + "|" + h.getUri(),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\|");
                    return new ViewStatsDto(parts[0], parts[1], e.getValue());
                })
                .sorted((a, b) -> Long.compare(b.getHits(), a.getHits()))
                .collect(Collectors.toList());
    }
}