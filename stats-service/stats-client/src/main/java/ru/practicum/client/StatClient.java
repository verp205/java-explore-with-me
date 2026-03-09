package ru.practicum.client;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStats;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.dto.ViewsStatsRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class StatClient {

    private final RestClient restClient;
    private final String serviceName;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public StatClient(String baseUrl, @Value("${stats.service-name}") String serviceName) {
        this.restClient = RestClient.create(baseUrl);
        this.serviceName = serviceName;
    }

    public void saveHit(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ip = request.getRemoteAddr();

        EndpointHitDto hitDto = EndpointHitDto.builder()
                .app(serviceName)
                .uri(uri)
                .ip(ip)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/hit")
                    .body(hitDto)
                    .retrieve()
                    .toBodilessEntity();

            if (response.getStatusCode().isError()) {
                throw new RuntimeException("Failed to save hit: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while saving hit to stats service", e);
        }
    }

    public List<ViewStats> getStats(ViewsStatsRequest request) {

        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stats")
                            .queryParam("start", request.getStart())
                            .queryParam("end", request.getEnd())
                            .queryParam("uris", request.getUris())
                            .queryParam("unique", request.isUnique())
                            .build()
                    )
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ViewStats>>() {});

        } catch (Exception e) {
            throw new RuntimeException("Error while getting stats", e);
        }
    }
}