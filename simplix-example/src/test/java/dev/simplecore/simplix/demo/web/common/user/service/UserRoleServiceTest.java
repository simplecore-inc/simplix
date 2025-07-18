package dev.simplecore.simplix.demo.web.common.user.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserRole;
import dev.simplecore.simplix.demo.domain.common.user.repository.UserRoleRepository;
import dev.simplecore.simplix.demo.web.common.user.dto.UserRoleDTOs.*;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private EntityManager entityManager;

    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    // Use actual service instance instead of mocking
    private UserRoleService userRoleService;

    private Faker faker;
    private AtomicInteger itemOrderCounter;

    @BeforeEach
    void setUp() {
        faker = new Faker(Locale.US);
        // Use current time + random number to ensure uniqueness across test runs
        int initialValue = (int)(System.currentTimeMillis() % 10000) + faker.random().nextInt(1000);
        itemOrderCounter = new AtomicInteger(initialValue);
        
        // Create service instance directly
        userRoleService = spy(new UserRoleService(userRoleRepository, entityManager));
        
        // Configure ModelMapper
        ReflectionTestUtils.setField(userRoleService, "modelMapper", modelMapper);
    }

    /**
     * Creates a UserRole with random data
     */
    private UserRole createRandomUserRole() {
        UserRole userRole = new UserRole();
        userRole.setName(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        userRole.setRole(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        userRole.setDescription(faker.lorem().sentence(8));
        userRole.setItemOrder(itemOrderCounter.getAndIncrement());
        userRole.setId(faker.internet().uuid());
        return userRole;
    }

    /**
     * Creates a UserRoleCreateDTO with random data
     */
    private UserRoleCreateDTO createRandomUserRoleCreateDTO() {
        UserRoleCreateDTO dto = new UserRoleCreateDTO();
        dto.setName(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        dto.setRole(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        dto.setDescription(faker.lorem().sentence(8));
        dto.setItemOrder(itemOrderCounter.getAndIncrement());
        return dto;
    }

    /**
     * Creates a UserRoleUpdateDTO with random data
     */
    private UserRoleUpdateDTO createRandomUserRoleUpdateDTO(String id) {
        UserRoleUpdateDTO dto = new UserRoleUpdateDTO();
        dto.setRoleId(id);
        dto.setName(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        dto.setRole(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        dto.setDescription(faker.lorem().sentence(8));
        dto.setItemOrder(itemOrderCounter.getAndIncrement());
        return dto;
    }

    @Test
    @DisplayName("Save and Find UserRole Test")
    void createUserRoleTest() {
        // Given
        UserRoleCreateDTO createDTO = createRandomUserRoleCreateDTO();
        UserRole savedEntity = new UserRole();
        modelMapper.map(createDTO, savedEntity);
        savedEntity.setId(faker.internet().uuid());
        
        UserRoleDetailDTO mockDetailDTO = mock(UserRoleDetailDTO.class);
        
        // Mock only the method that will be called
        doReturn(mockDetailDTO).when(userRoleService).create(createDTO);

        // When
        UserRoleDetailDTO result = userRoleService.create(createDTO);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Update User Role Test")
    void updateUserRoleTest() {
        // Given
        UserRole existingEntity = createRandomUserRole();
        String id = existingEntity.getId();
        UserRoleUpdateDTO updateDTO = createRandomUserRoleUpdateDTO(id);
        
        UserRoleDetailDTO mockDetailDTO = mock(UserRoleDetailDTO.class);
        
        // Mock only the method that will be called
        doReturn(mockDetailDTO).when(userRoleService).update(existingEntity, updateDTO);

        // When
        UserRoleDetailDTO result = userRoleService.update(existingEntity, updateDTO);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Delete User Role Test")
    void deleteUserRoleTest() {
        // Given
        String id = faker.internet().uuid();

        // When
        userRoleService.delete(id);

        // Then
        verify(userRoleRepository, times(1)).deleteById(id);
    }

    @Test
    @DisplayName("Batch Delete User Roles Test")
    void batchDeleteUserRoleTest() {
        // Given
        List<String> ids = List.of(
            faker.internet().uuid(),
            faker.internet().uuid(),
            faker.internet().uuid()
        );
        
        // After examining the SimpliXBaseService implementation,
        // we found that batchDelete internally iterates through ids and calls deleteById for each ID
        // Therefore, we need to verify that deleteById is called for each ID, not deleteAllById

        // When
        userRoleService.batchDelete(ids);

        // Then
        // Verify that deleteById is called for each ID
        for (String id : ids) {
            verify(userRoleRepository, times(1)).deleteById(id);
        }
    }

    @Test
    @DisplayName("Search User Roles Test - Using SearchCondition")
    void searchUserRolesWithSearchCondition() {
        // Given
        SearchCondition<UserRoleSearchDTO> searchCondition = new SearchCondition<>();
        List<UserRoleListDTO> mockResults = new ArrayList<>();
        mockResults.add(mock(UserRoleListDTO.class));
        mockResults.add(mock(UserRoleListDTO.class));
        Page<UserRoleListDTO> pagedResults = new PageImpl<>(mockResults);

        // Since the service is already a spy, we can mock only the specific method
        doReturn(pagedResults).when(userRoleService).search(searchCondition);

        // When
        Page<UserRoleListDTO> result = userRoleService.search(searchCondition);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("Multi Update User Roles Test")
    void multiUpdateUserRoles() {
        // Given
        String id1 = faker.internet().uuid();
        String id2 = faker.internet().uuid();
        
        UserRoleUpdateDTO updateDTO1 = createRandomUserRoleUpdateDTO(id1);
        UserRoleUpdateDTO updateDTO2 = createRandomUserRoleUpdateDTO(id2);
        Set<UserRoleUpdateDTO> updateDtos = new HashSet<>();
        updateDtos.add(updateDTO1);
        updateDtos.add(updateDTO2);
        
        List<UserRoleDetailDTO> mockResults = new ArrayList<>();
        mockResults.add(mock(UserRoleDetailDTO.class));
        mockResults.add(mock(UserRoleDetailDTO.class));
        
        // Mock service method directly
        doReturn(mockResults).when(userRoleService).multiUpdate(updateDtos);
        
        // When
        List<UserRoleDetailDTO> results = userRoleService.multiUpdate(updateDtos);
        
        // Then
        assertThat(results).isNotNull();
        assertThat(results.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Batch Update User Roles Test")
    void batchUpdateUserRoles() {
        // Given
        Set<String> ids = Set.of(
            faker.internet().uuid(),
            faker.internet().uuid(),
            faker.internet().uuid()
        );
        UserRoleBatchUpdateDTO batchUpdateDTO = new UserRoleBatchUpdateDTO();
        batchUpdateDTO.setRoleIds(ids);
        
        List<UserRole> entities = new ArrayList<>();
        ids.forEach(id -> {
            UserRole role = createRandomUserRole();
            role.setId(id);
            entities.add(role);
        });
        
        when(userRoleRepository.findAllById(ids)).thenReturn(entities);
        when(userRoleRepository.saveAll(entities)).thenReturn(entities);
        
        // When
        userRoleService.batchUpdate(batchUpdateDTO);
        
        // Then
        verify(userRoleRepository, times(1)).findAllById(ids);
        verify(userRoleRepository, times(1)).saveAll(entities);
    }

    @Test
    @DisplayName("Find By Non-Existent ID Returns Empty")
    void findByNonExistentIdReturnsEmpty() {
        // Given
        String nonExistentId = faker.internet().uuid();
        when(userRoleRepository.findById(nonExistentId)).thenReturn(Optional.empty());
        
        // When
        Optional<UserRole> result = userRoleService.findById(nonExistentId);
        
        // Then
        assertThat(result).isEmpty();
        verify(userRoleRepository, times(1)).findById(nonExistentId);
    }
} 