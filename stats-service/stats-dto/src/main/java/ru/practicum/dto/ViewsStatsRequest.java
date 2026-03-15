package ru.practicum.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class ViewsStatsRequest {
    private LocalDateTime start;
    private LocalDateTime end;
    private Set<String> uris;
    private boolean unique;
}