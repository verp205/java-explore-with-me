package ru.practicum.comments.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.comments.dto.CommentDto;
import ru.practicum.comments.model.CommentStatus;
import ru.practicum.comments.service.CommentService;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/admin/comments")
public class AdminCommentController {
    private final CommentService commentService;

    @GetMapping()
    public ResponseEntity<List<CommentDto>> getCommentsByStatus(
            @RequestParam CommentStatus status) {
        return ResponseEntity.ok()
                .body(commentService.getCommentsByStatus(status));
    }

    @PatchMapping("/{commentId}/approve")
    public ResponseEntity<CommentDto> approve(@PathVariable Long commentId) {
        return ResponseEntity.ok()
                .body(commentService.approveComment(commentId));
    }

    @PatchMapping("/{commentId}/reject")
    public ResponseEntity<CommentDto> reject(@PathVariable Long commentId) {
        return ResponseEntity.ok()
                .body(commentService.rejectComment(commentId));
    }
}
