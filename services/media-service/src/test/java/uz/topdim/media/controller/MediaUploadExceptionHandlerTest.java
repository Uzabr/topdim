package uz.topdim.media.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import uz.topdim.common.dto.ApiResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUploadExceptionHandlerTest {

    @Test
    void convertsMultipartLimitFailureToStablePayloadTooLargeResponse() {
        MediaUploadExceptionHandler handler = new MediaUploadExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(20L * 1024 * 1024)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Размер файла не должен превышать 20 МБ");
    }
}
