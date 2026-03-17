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
import ru.practicum.handler.exception.ConflictException;
import ru.practicum.handler.exception.NotFoundException;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public CommentDto addComment(Long userId, Long eventId, NewCommentDto dto) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        Comment comment = CommentMapper.toComment(dto, event, user);

        return CommentMapper.toCommentDto(commentRepository.save(comment));
    }

    public List<CommentDto> getUserComments(Long userId) {
        return commentRepository.findByAuthorId(userId)
                .stream()
                .map(CommentMapper::toCommentDto)
                .toList();
    }

    public CommentDto getUserCommentById(Long userId, Long commentId) {

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new NotFoundException("Comment not found");
        }

        return CommentMapper.toCommentDto(comment);
    }

    public List<CommentDto> getEventComments(Long eventId) {
        return commentRepository.findByEventIdAndStatus(eventId, CommentStatus.APPROVED)
                .stream()
                .map(CommentMapper::toCommentDto)
                .toList();
    }

    public CommentDto updateComment(Long userId, Long commentId, NewCommentDto dto) {

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));

        // Проверка владельца
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new NotFoundException("Comment not found");
        }

        if (comment.getStatus() == CommentStatus.APPROVED) {
            throw new ConflictException("Cannot edit approved comment");
        }

        comment.setText(dto.getText());
        comment.setStatus(CommentStatus.PENDING);

        return CommentMapper.toCommentDto(commentRepository.save(comment));
    }

    public CommentDto approveComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));

        if (comment.getStatus() != CommentStatus.PENDING) {
            throw new ConflictException("Only PENDING comments can be approved");
        }

        comment.setStatus(CommentStatus.APPROVED);

        return CommentMapper.toCommentDto(commentRepository.save(comment));
    }

    public CommentDto rejectComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));

        if (comment.getStatus() != CommentStatus.PENDING) {
            throw new ConflictException("Only PENDING comments can be rejected");
        }

        comment.setStatus(CommentStatus.REJECTED);

        return CommentMapper.toCommentDto(commentRepository.save(comment));
    }

    public List<CommentDto> getCommentsByStatus(CommentStatus status) {
        return commentRepository.findByStatus(status)
                .stream()
                .map(CommentMapper::toCommentDto)
                .toList();
    }
}
