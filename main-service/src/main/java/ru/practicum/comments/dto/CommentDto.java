package ru.practicum.comments.dto;

import lombok.Builder;
import lombok.Data;
import ru.practicum.comments.model.CommentStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentDto {
    private Long id;
    private String text;
    private Long eventId;
    private Long authorId;
    private String authorName;
    private CommentStatus status;
    private LocalDateTime created;
}
