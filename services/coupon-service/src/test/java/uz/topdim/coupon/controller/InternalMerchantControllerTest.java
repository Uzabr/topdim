package uz.topdim.coupon.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.topdim.coupon.dto.MerchantContextResponse;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.service.MerchantService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalMerchantControllerTest {

    @Mock private MerchantService merchantService;
    @InjectMocks private InternalMerchantController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/internal/merchants/by-user/{userId}: returns merchant context")
    void getMerchantContextByUser_returnsMerchantContext() throws Exception {
        MerchantContextResponse response = MerchantContextResponse.builder()
                .merchantId(77L)
                .userId(10L)
                .name("SPA Oasis")
                .active(true)
                .build();

        when(merchantService.getMerchantContextByUserId(10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/internal/merchants/by-user/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.merchantId").value(77))
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.name").value("SPA Oasis"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/internal/merchants/by-user/{userId}: unknown user returns 404")
    void getMerchantContextByUser_unknownUser_returns404() throws Exception {
        when(merchantService.getMerchantContextByUserId(999L))
                .thenThrow(new ResourceNotFoundException("Мерчант для пользователя не найден"));

        mockMvc.perform(get("/api/v1/internal/merchants/by-user/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
