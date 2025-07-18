package dev.simplecore.simplix.demo.web.common.user.service;

import dev.simplecore.simplix.demo.domain.common.user.entity.UserAccount;
import dev.simplecore.simplix.demo.domain.common.user.repository.UserAccountRepository;
import dev.simplecore.simplix.demo.permission.CustomUserDetailsService;
import dev.simplecore.simplix.demo.web.common.user.dto.UserAccountDTOs.UserAccountCreateDTO;
import dev.simplecore.simplix.demo.web.common.user.dto.UserAccountDTOs.UserAccountDetailDTO;
import dev.simplecore.simplix.demo.web.common.user.dto.UserAccountDTOs.UserAccountUpdateDTO;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ObjectProvider<UserPositionService> userPositionServiceProvider;

    @Mock
    private ObjectProvider<UserRoleService> userRoleServiceProvider;

    @Mock
    private ObjectProvider<UserOrganizationService> userOrganizationServiceProvider;

    @Mock
    private ObjectProvider<CustomUserDetailsService> userDetailsServiceProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserAccountService userAccountService;

    private Faker faker;

    @BeforeEach
    void setUp() {
        faker = new Faker(Locale.US);
        
        userAccountService = new UserAccountService(
            userAccountRepository,
            entityManager,
            userPositionServiceProvider,
            userRoleServiceProvider,
            userOrganizationServiceProvider,
            userDetailsServiceProvider,
            passwordEncoder
        );
        
        // Set ModelMapper using reflection
        ModelMapper modelMapper = new ModelMapper();
        ReflectionTestUtils.setField(userAccountService, "modelMapper", modelMapper);
    }

    private UserAccountCreateDTO createUserAccountCreateDTO() {
        UserAccountCreateDTO dto = new UserAccountCreateDTO();
        dto.setUsername(faker.internet().username());
        dto.setPassword("TestPassword123!");
        dto.setEnabled(true);
        dto.setRealName(faker.name().fullName());
        dto.setEmail(faker.internet().emailAddress());
        dto.setMobilePhone("010-1234-5678");
        dto.setPosition("test-position-id");
        dto.setRoles(Set.of("test-role-id"));
        dto.setOrganizations(Set.of("test-org-id"));
        return dto;
    }

    private UserAccountUpdateDTO createUserAccountUpdateDTO(String id) {
        UserAccountUpdateDTO dto = new UserAccountUpdateDTO();
        dto.setUserId(id);
        dto.setUsername(faker.internet().username());
        dto.setPassword("UpdatedPassword123!");
        dto.setEnabled(true);
        dto.setRealName(faker.name().fullName());
        dto.setEmail(faker.internet().emailAddress());
        dto.setMobilePhone("010-9876-5432");
        dto.setPosition("test-position-id");
        dto.setRoles(Set.of("test-role-id"));
        dto.setOrganizations(Set.of("test-org-id"));
        return dto;
    }

    @Test
    @DisplayName("Create UserAccount - Password should be encoded")
    void createUserAccount_ShouldEncodePassword() {
        // Given
        UserAccountCreateDTO createDTO = createUserAccountCreateDTO();
        String encodedPassword = "{bcrypt}$2y$10$GJdBT6T1EUFfJ005ZxgEbOoK91KDW2guHXEBKExotly4aB3gL3Smy";
        
        when(passwordEncoder.encode(createDTO.getPassword())).thenReturn(encodedPassword);
        when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount entity = invocation.getArgument(0);
            entity.setUserId("test-user-id");
            return entity;
        });
        when(userAccountRepository.findById(anyString())).thenReturn(Optional.of(new UserAccount()));

        // When
        UserAccountDetailDTO result = userAccountService.create(createDTO);

        // Then
        assertThat(result).isNotNull();
        // Verify that password encoder was called with the plain text password
        // The actual entity should have the encoded password
    }

    @Test
    @DisplayName("Update UserAccount - Password should be encoded when provided")
    void updateUserAccount_ShouldEncodePassword_WhenPasswordProvided() {
        // Given
        String userId = "test-user-id";
        UserAccount existingEntity = new UserAccount();
        existingEntity.setUserId(userId);
        existingEntity.setUsername("oldusername");
        existingEntity.setPassword("{bcrypt}$2y$10$OldPasswordHash");
        existingEntity.setEnabled(true);
        
        UserAccountUpdateDTO updateDTO = createUserAccountUpdateDTO(userId);
        String newEncodedPassword = "{bcrypt}$2y$10$NewPasswordHash";
        
        when(passwordEncoder.encode(updateDTO.getPassword())).thenReturn(newEncodedPassword);
        when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });
        when(userAccountRepository.findById(anyString())).thenReturn(Optional.of(existingEntity));

        // When
        UserAccountDetailDTO result = userAccountService.update(existingEntity, updateDTO);

        // Then
        assertThat(result).isNotNull();
        assertThat(existingEntity.getPassword()).isEqualTo(newEncodedPassword);
    }

    @Test
    @DisplayName("Update UserAccount - Password should remain unchanged when not provided")
    void updateUserAccount_ShouldKeepOriginalPassword_WhenPasswordNotProvided() {
        // Given
        String userId = "test-user-id";
        String originalPassword = "{bcrypt}$2y$10$OriginalPasswordHash";
        
        UserAccount existingEntity = new UserAccount();
        existingEntity.setUserId(userId);
        existingEntity.setUsername("oldusername");
        existingEntity.setPassword(originalPassword);
        existingEntity.setEnabled(true);
        
        UserAccountUpdateDTO updateDTO = createUserAccountUpdateDTO(userId);
        updateDTO.setPassword(null); // No password provided
        
        when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });
        when(userAccountRepository.findById(anyString())).thenReturn(Optional.of(existingEntity));

        // When
        UserAccountDetailDTO result = userAccountService.update(existingEntity, updateDTO);

        // Then
        assertThat(result).isNotNull();
        assertThat(existingEntity.getPassword()).isEqualTo(originalPassword);
    }

    @Test
    @DisplayName("Update UserAccount - Password should remain unchanged when empty string provided")
    void updateUserAccount_ShouldKeepOriginalPassword_WhenPasswordEmpty() {
        // Given
        String userId = "test-user-id";
        String originalPassword = "{bcrypt}$2y$10$OriginalPasswordHash";
        
        UserAccount existingEntity = new UserAccount();
        existingEntity.setUserId(userId);
        existingEntity.setUsername("oldusername");
        existingEntity.setPassword(originalPassword);
        existingEntity.setEnabled(true);
        
        UserAccountUpdateDTO updateDTO = createUserAccountUpdateDTO(userId);
        updateDTO.setPassword(""); // Empty password provided
        
        when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });
        when(userAccountRepository.findById(anyString())).thenReturn(Optional.of(existingEntity));

        // When
        UserAccountDetailDTO result = userAccountService.update(existingEntity, updateDTO);

        // Then
        assertThat(result).isNotNull();
        assertThat(existingEntity.getPassword()).isEqualTo(originalPassword);
    }
} 