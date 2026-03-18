package ru.practicum.comments.mapper;

import ru.practicum.comments.dto.CommentDto;
import ru.practicum.comments.dto.NewCommentDto;
import ru.practicum.comments.model.Comment;
import ru.practicum.comments.model.CommentStatus;
import ru.practicum.event.model.Event;
import ru.practicum.user.model.User;

import java.time.LocalDateTime;

public class CommentMapper {

    public static CommentDto toCommentDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .eventId(comment.getEvent().getId())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .status(comment.getStatus())
                .created(comment.getCreated())
                .build();
    }

    public static Comment toComment(NewCommentDto dto, Event event, User user) {
        return Comment.builder()
                .text(dto.getText())
                .event(event)
                .author(user)
                .status(CommentStatus.PENDING)
                .created(LocalDateTime.now())
                .build();
    }
}
