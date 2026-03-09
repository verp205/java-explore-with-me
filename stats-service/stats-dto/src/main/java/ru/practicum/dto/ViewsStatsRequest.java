package ru.practicum.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
public class ViewsStatsRequest {

    private LocalDateTime start;
    private LocalDateTime end;
    private Set<String> uris;
    private boolean unique;
}