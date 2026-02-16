package ru.practicum.statsclient;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.practicum.EndpointHitDto;

@Component
@RequiredArgsConstructor
public class StatsClient {

    private final RestTemplate restTemplate;

    public void hit(EndpointHitDto dto) {
        restTemplate.postForLocation("/hit", dto);
    }
}
