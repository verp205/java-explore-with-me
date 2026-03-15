package ru.practicum.statservice.service;

import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.statservice.model.EndpointHit;

import java.util.List;

public interface StatService {
    void saveHit(EndpointHitDto hitDto);

    List<ViewStatsDto> getStats(String start, String end, List<String> uris, boolean unique);

    List<EndpointHit> getAllHits();
}
