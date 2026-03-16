package ru.practicum.comments.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.comments.dto.CommentDto;
import ru.practicum.comments.dto.NewCommentDto;
import ru.practicum.comments.mapper.CommentMapper;
import ru.practicum.comments.model.Comment;
import ru.practicum.comments.model.CommentStatus;
import ru.practicum.comments.repository.CommentRepository;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.handler.exception.NotFoundException;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;

    public CommentDto addComment(Long userId, Long eventId, NewCommentDto dto) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        Comment comment = Comment.builder()
                .text(dto.getText())
                .event(event)
                .author(user)
                .status(CommentStatus.PENDING)
                .created(LocalDateTime.now())
                .build();

        return commentMapper.toCommentDto(commentRepository.save(comment));
    }

    public List<CommentDto> getEventComments(Long eventId) {
        return commentRepository.findByEventIdAndStatus(eventId, CommentStatus.APPROVED)
                .stream()
                .map(commentMapper::toCommentDto)
                .toList();
    }

    public CommentDto approveComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));

        comment.setStatus(CommentStatus.APPROVED);

        return commentMapper.toCommentDto(commentRepository.save(comment));
    }

    public CommentDto rejectComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));

        comment.setStatus(CommentStatus.REJECTED);

        return commentMapper.toCommentDto(commentRepository.save(comment));
    }

    public List<CommentDto> getCommentsByStatus(CommentStatus status) {
        return commentRepository.findByStatus(status)
                .stream()
                .map(commentMapper::toCommentDto)
                .toList();
    }
}
