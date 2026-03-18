package ru.practicum.comments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NewCommentDto {

    @NotBlank(message = "Комментарий не может быть пустым")
    @Size(min = 3, max = 2000, message = "Длина комментария должна быть от 3 до 2000 символов")
    private String text;
}
