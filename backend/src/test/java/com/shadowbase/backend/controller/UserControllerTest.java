package com.shadowbase.backend.controller;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.shadowbase.backend.dto.CreateUserRequest;
import com.shadowbase.backend.dto.UserResponse;
import com.shadowbase.backend.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService);
    }

    @Test
    void testListUsers_Returns200Ok() {
        UserResponse u = UserResponse.builder().id(UUID.randomUUID()).emailId("u@example.com").build();
        when(userService.listUsers()).thenReturn(List.of(u));

        ResponseEntity<List<UserResponse>> response = controller.listUsers();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void testGetUser_Returns200Ok() {
        UUID id = UUID.randomUUID();
        UserResponse u = UserResponse.builder().id(id).emailId("u@example.com").build();
        when(userService.getUserById(id)).thenReturn(u);

        ResponseEntity<UserResponse> response = controller.getUser(id);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(id, response.getBody().getId());
    }

    @Test
    void testCreateUser_Returns201Created() {
        CreateUserRequest req = CreateUserRequest.builder().emailId("u@example.com").build();
        UserResponse u = UserResponse.builder().id(UUID.randomUUID()).emailId("u@example.com").build();
        when(userService.createUser(req)).thenReturn(u);

        ResponseEntity<UserResponse> response = controller.createUser(req);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("u@example.com", response.getBody().getEmailId());
    }

    @Test
    void testGetDemoUser_Returns200Ok() {
        UserResponse demo = UserResponse.builder().id(UUID.randomUUID()).emailId("demo@example.com").build();
        when(userService.getOrCreateDemoUser()).thenReturn(demo);

        ResponseEntity<UserResponse> response = controller.getDemoUser();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userService).getOrCreateDemoUser();
    }
}
