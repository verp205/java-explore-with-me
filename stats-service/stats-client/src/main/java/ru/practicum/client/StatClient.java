package ru.practicum.client;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class StatClient {

    private final RestClient restClient;
    private final String serviceName;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public StatClient(String baseUrl, String serviceName) {
        this.restClient = RestClient.create(baseUrl);
        this.serviceName = serviceName;
    }

    public void saveHit(String uri, String ip) {
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

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        try {
            String startStr = start.format(FORMATTER);
            String endStr = end.format(FORMATTER);

            ResponseEntity<ViewStatsDto[]> response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/stats")
                                .queryParam("start", startStr)
                                .queryParam("end", endStr)
                                .queryParam("unique", unique);

                        if (uris != null && !uris.isEmpty()) {
                            // Важно: передаем список URI
                            uriBuilder.queryParam("uris", uris);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .toEntity(ViewStatsDto[].class);

            return Arrays.asList(response.getBody() != null ? response.getBody() : new ViewStatsDto[0]);
        } catch (Exception e) {
            throw new RuntimeException("Error while getting stats", e);
        }
    }
}