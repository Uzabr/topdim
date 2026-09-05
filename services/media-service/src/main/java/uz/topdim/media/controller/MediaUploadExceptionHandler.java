package uz.topdim.media.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.media.service.ImageProcessingException;

@RestControllerAdvice(assignableTypes = MediaController.class)
public class MediaUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("Размер файла не должен превышать 20 МБ"));
    }

    @ExceptionHandler(ImageProcessingException.class)
    public ResponseEntity<ApiResponse<Void>> onProcessing(ImageProcessingException e) {
        return ResponseEntity.unprocessableEntity()
                .body(ApiResponse.error("Не удалось обработать изображение"));
    }
}
