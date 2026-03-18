package ru.practicum.comments.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.comments.model.Comment;
import ru.practicum.comments.model.CommentStatus;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByEventIdAndStatus(Long eventId, CommentStatus status);

    List<Comment> findByStatus(CommentStatus status);

    List<Comment> findByAuthorId(Long authorId);

    @Query("""
    SELECT c.event.id, COUNT(c)
    FROM Comment c
    WHERE c.event.id IN :eventIds
      AND c.status = 'APPROVED'
    GROUP BY c.event.id
    """)
    List<Object[]> countApprovedCommentsByEventIds(List<Long> eventIds);
}