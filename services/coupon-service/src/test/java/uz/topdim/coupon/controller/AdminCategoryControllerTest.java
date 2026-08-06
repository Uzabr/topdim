package uz.topdim.coupon.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uz.topdim.coupon.config.SecurityConfig;
import uz.topdim.coupon.dto.AdminCategoryResponse;
import uz.topdim.coupon.exception.GlobalExceptionHandler;
import uz.topdim.coupon.security.RoleHeaderAuthenticationFilter;
import uz.topdim.coupon.service.CategoryService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCategoryController.class)
@Import({SecurityConfig.class, RoleHeaderAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminCategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private CategoryService categoryService;

    @Test
    void adminList_exposesInactiveState() throws Exception {
        when(categoryService.getAllCategoriesForAdmin()).thenReturn(List.of(
                AdminCategoryResponse.builder()
                        .id(2L)
                        .name("Архив")
                        .slug("archive")
                        .sortOrder(2)
                        .active(false)
                        .build()
        ));

        mockMvc.perform(withStaff(get("/api/v1/admin/categories"), "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].active").value(false));
    }

    @Test
    void moderator_cannotReadAdminCategoryList() throws Exception {
        mockMvc.perform(withStaff(get("/api/v1/admin/categories"), "MODERATOR"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void blankName_isRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(withStaff(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "slug": "food",
                                  "sortOrder": 1,
                                  "active": true
                                }
                                """), "ADMIN"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    void negativeSortOrder_isRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(withStaff(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Еда",
                                  "slug": "food",
                                  "sortOrder": -1,
                                  "active": true
                                }
                                """), "ADMIN"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    void superAdmin_canUpdateCategory() throws Exception {
        when(categoryService.updateCategory(any(Long.class), any()))
                .thenReturn(AdminCategoryResponse.builder()
                        .id(7L)
                        .name("Еда")
                        .slug("food")
                        .active(false)
                        .build());

        mockMvc.perform(withStaff(put("/api/v1/admin/categories/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Еда",
                                  "slug": "food",
                                  "sortOrder": 1,
                                  "active": false
                                }
                                """), "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        verify(categoryService).updateCategory(any(Long.class), any());
    }

    @Test
    void referencedDelete_isReturnedAsConflict() throws Exception {
        doThrow(new IllegalStateException("Категория используется купонами"))
                .when(categoryService).deleteCategory(8L);

        mockMvc.perform(withStaff(delete("/api/v1/admin/categories/8"), "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Категория используется купонами"));
    }

    private MockHttpServletRequestBuilder withStaff(
            MockHttpServletRequestBuilder request,
            String role
    ) {
        return request
                .header("X-User-Id", "42")
                .header("X-User-Role", role);
    }
}
