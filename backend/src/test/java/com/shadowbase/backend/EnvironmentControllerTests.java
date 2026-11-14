
package com.shadowbase.backend;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shadowbase.backend.controller.EnvironmentController;
import com.shadowbase.backend.dto.EnvironmentResponse;
import com.shadowbase.backend.service.MySQLContainerService;

@WebMvcTest(EnvironmentController.class)
@WithMockUser
class EnvironmentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MySQLContainerService containerService;

    @Test
    void shouldCreateEnvironment() throws Exception {

        EnvironmentResponse response = new EnvironmentResponse(
                "test-123",
                "RUNNING",
                "jdbc:mysql://localhost:3306/test",
                "testuser",
                "testpass"
        );

        when(containerService.createEnvironment()).thenReturn(response);

        mockMvc.perform(post("/api/environments")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("test-123"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void shouldRetrieveEnvironment() throws Exception {

        EnvironmentResponse response = new EnvironmentResponse(
                "test-123",
                "RUNNING",
                "jdbc:mysql://localhost:3306/test",
                "testuser",
                "testpass"
        );

        when(containerService.getEnvironment("test-123"))
                .thenReturn(response);

        mockMvc.perform(get("/api/environments/test-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-123"));
    }

    @Test
    void shouldStopEnvironment() throws Exception {

        when(containerService.stopEnvironment("test-123"))
                .thenReturn(true);

        mockMvc.perform(delete("/api/environments/test-123")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());
    }
}
