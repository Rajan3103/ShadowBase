package com.shadowbase.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shadowbase.backend.Entity.User;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.UserRepository;
import com.shadowbase.backend.dto.CreateUserRequest;
import com.shadowbase.backend.dto.UserResponse;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository);
    }

    @Test
    void testListUsers_WhenUsersExist_ReturnsList() {
        User u1 = User.builder().id(UUID.randomUUID()).emailId("user1@example.com").build();
        User u2 = User.builder().id(UUID.randomUUID()).emailId("user2@example.com").build();

        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        List<UserResponse> result = userService.listUsers();

        assertEquals(2, result.size());
        assertEquals("user1@example.com", result.get(0).getEmailId());
    }

    @Test
    void testListUsers_WhenEmpty_SeedsDemoUser() {
        when(userRepository.findAll()).thenReturn(List.of());
        when(userRepository.findById(UserServiceImpl.DEMO_USER_ID)).thenReturn(Optional.empty());

        User demo = User.builder()
                .id(UserServiceImpl.DEMO_USER_ID)
                .emailId(UserServiceImpl.DEMO_USER_EMAIL)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(demo);

        List<UserResponse> result = userService.listUsers();

        assertEquals(1, result.size());
        assertEquals(UserServiceImpl.DEMO_USER_ID, result.get(0).getId());
        assertEquals(UserServiceImpl.DEMO_USER_EMAIL, result.get(0).getEmailId());
    }

    @Test
    void testGetUserById_Success() {
        UUID id = UUID.randomUUID();
        User u = User.builder().id(id).emailId("test@example.com").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        UserResponse res = userService.getUserById(id);

        assertNotNull(res);
        assertEquals(id, res.getId());
        assertEquals("test@example.com", res.getEmailId());
    }

    @Test
    void testGetUserById_NotFound_ThrowsException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(id));
    }

    @Test
    void testCreateUser_NewUser_Success() {
        CreateUserRequest req = CreateUserRequest.builder().emailId("new@example.com").password("pass123").build();
        when(userRepository.findByEmailId("new@example.com")).thenReturn(Optional.empty());

        User saved = User.builder().id(UUID.randomUUID()).emailId("new@example.com").password("pass123").build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserResponse res = userService.createUser(req);

        assertNotNull(res);
        assertEquals("new@example.com", res.getEmailId());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testCreateUser_ExistingUser_ReturnsExisting() {
        CreateUserRequest req = CreateUserRequest.builder().emailId("exist@example.com").build();
        User existing = User.builder().id(UUID.randomUUID()).emailId("exist@example.com").build();
        when(userRepository.findByEmailId("exist@example.com")).thenReturn(Optional.of(existing));

        UserResponse res = userService.createUser(req);

        assertNotNull(res);
        assertEquals(existing.getId(), res.getId());
    }

    @Test
    void testCreateUser_BlankEmail_ThrowsException() {
        CreateUserRequest req = CreateUserRequest.builder().emailId("   ").build();
        assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
    }
}
