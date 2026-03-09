package ru.practicum.dto;

import lombok.Getter;

@Getter
public class ViewStats {

    private String app;
    private String uri;
    private Long hits;
}
