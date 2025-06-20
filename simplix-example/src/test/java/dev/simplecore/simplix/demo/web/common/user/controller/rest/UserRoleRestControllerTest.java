package dev.simplecore.simplix.demo.web.common.user.controller.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.simplecore.simplix.demo.domain.common.user.entity.UserRole;
import dev.simplecore.simplix.demo.web.common.user.dto.UserRoleDTOs.UserRoleBatchUpdateDTO;
import dev.simplecore.simplix.demo.web.common.user.dto.UserRoleDTOs.UserRoleCreateDTO;
import dev.simplecore.simplix.demo.web.common.user.dto.UserRoleDTOs.UserRoleDetailDTO;
import dev.simplecore.simplix.demo.web.common.user.dto.UserRoleDTOs.UserRoleListDTO;
import dev.simplecore.simplix.demo.web.common.user.dto.UserRoleDTOs.UserRoleSearchDTO;
import dev.simplecore.simplix.demo.web.common.user.dto.UserRoleDTOs.UserRoleUpdateDTO;
import dev.simplecore.simplix.demo.web.common.user.service.UserRoleService;
import net.datafaker.Faker;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.searchable.core.condition.SearchCondition;

@ExtendWith(MockitoExtension.class)
class UserRoleRestControllerTest {

    private MockMvc mockMvc;
    
    @Mock
    private UserRoleService userRoleService;
    
    @InjectMocks
    private UserRoleRestController userRoleRestController;
    
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    
    private Faker faker;
    private AtomicInteger itemOrderCounter;

    @BeforeEach
    void setUp() {
        faker = new Faker(Locale.US);
        itemOrderCounter = new AtomicInteger(1);
        mockMvc = MockMvcBuilders
                .standaloneSetup(userRoleRestController)
                .alwaysDo(print())
                .build();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /**
     * Creates a UserRole with random data
     */
    private UserRole createRandomUserRole() {
        UserRole userRole = new UserRole();
        userRole.setName(faker.lorem().word());
        userRole.setRole(faker.lorem().word());
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
        dto.setName(faker.lorem().word());
        dto.setRole(faker.lorem().word());
        dto.setDescription(faker.lorem().sentence(8));
        dto.setItemOrder(itemOrderCounter.getAndIncrement());
        return dto;
    }

    /**
     * Creates a UserRoleUpdateDTO with random data
     */
    private UserRoleUpdateDTO createRandomUserRoleUpdateDTO(String id) {
        UserRoleUpdateDTO dto = new UserRoleUpdateDTO();
        dto.setId(id);
        dto.setName(faker.lorem().word());
        dto.setRole(faker.lorem().word());
        dto.setDescription(faker.lorem().sentence(8));
        dto.setItemOrder(itemOrderCounter.getAndIncrement());
        return dto;
    }
    
    /**
     * Creates a UserRoleDetailDTO with random data
     */
    private UserRoleDetailDTO createRandomUserRoleDetailDTO() {
        UserRoleDetailDTO dto = new UserRoleDetailDTO();
        dto.setId(faker.internet().uuid());
        dto.setName(faker.lorem().word());
        dto.setRole(faker.lorem().word());
        dto.setDescription(faker.lorem().sentence(8));
        dto.setItemOrder(itemOrderCounter.getAndIncrement());
        return dto;
    }

    @Test
    @DisplayName("Create UserRole Test")
    void createUserRoleTest() throws Exception {
        // Given
        UserRoleCreateDTO createDTO = createRandomUserRoleCreateDTO();
        UserRoleDetailDTO resultDTO = createRandomUserRoleDetailDTO();
        
        when(userRoleService.create(any(UserRoleCreateDTO.class))).thenReturn(resultDTO);
        
        // When & Then
        mockMvc.perform(post("/api/user/role/create")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body.id").value(resultDTO.getId()))
                .andExpect(jsonPath("$.body.name").value(resultDTO.getName()))
                .andExpect(jsonPath("$.body.role").value(resultDTO.getRole()));
    }

    @Test
    @DisplayName("Update User Role Test")
    void updateUserRoleTest() throws Exception {
        // Given
        String id = faker.internet().uuid();
        UserRole existingRole = createRandomUserRole();
        UserRoleUpdateDTO updateDTO = createRandomUserRoleUpdateDTO(id);
        UserRoleDetailDTO resultDTO = createRandomUserRoleDetailDTO();
        
        when(userRoleService.findById(id)).thenReturn(Optional.of(existingRole));
        when(userRoleService.update(any(UserRole.class), any(UserRoleUpdateDTO.class))).thenReturn(resultDTO);
        
        // When & Then
        mockMvc.perform(put("/api/user/role/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body.id").value(resultDTO.getId()))
                .andExpect(jsonPath("$.body.name").value(resultDTO.getName()))
                .andExpect(jsonPath("$.body.role").value(resultDTO.getRole()));
    }

    @Test
    @DisplayName("Delete User Role Test")
    void deleteUserRoleTest() throws Exception {
        // Given
        String id = faker.internet().uuid();
        
        when(userRoleService.existsById(id)).thenReturn(true);
        doNothing().when(userRoleService).delete(id);
        
        // When & Then
        mockMvc.perform(delete("/api/user/role/{id}", id)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Get User Role By ID Test")
    void getUserRoleTest() throws Exception {
        // Given
        String id = faker.internet().uuid();
        UserRoleDetailDTO detailDTO = createRandomUserRoleDetailDTO();
        
        when(userRoleService.findById(eq(id), eq(UserRoleDetailDTO.class))).thenReturn(Optional.of(detailDTO));
        
        // When & Then
        mockMvc.perform(get("/api/user/role/{id}", id)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body.id").value(detailDTO.getId()))
                .andExpect(jsonPath("$.body.name").value(detailDTO.getName()))
                .andExpect(jsonPath("$.body.role").value(detailDTO.getRole()));
    }
    
    @Test
    @DisplayName("User Role Not Found Test")
    void userRoleNotFoundTest() throws Exception {
        // Given
        String id = faker.internet().uuid();
        
        when(userRoleService.findById(eq(id), eq(UserRoleDetailDTO.class))).thenReturn(Optional.empty());
        
        // When & Then
        mockMvc.perform(get("/api/user/role/{id}", id)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FAILURE"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Multi Update User Roles Test")
    void multiUpdateUserRolesTest() throws Exception {
        // Given
        Set<UserRoleUpdateDTO> updateDTOs = new HashSet<>();
        updateDTOs.add(createRandomUserRoleUpdateDTO(faker.internet().uuid()));
        updateDTOs.add(createRandomUserRoleUpdateDTO(faker.internet().uuid()));
        
        List<UserRoleDetailDTO> resultDTOs = List.of(
            createRandomUserRoleDetailDTO(),
            createRandomUserRoleDetailDTO()
        );
        
        when(userRoleService.multiUpdate(anySet())).thenReturn(resultDTOs);
        
        // When & Then
        mockMvc.perform(patch("/api/user/role")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTOs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body").isArray())
                .andExpect(jsonPath("$.body.length()").value(2))
                .andExpect(jsonPath("$.body[0].id").exists())
                .andExpect(jsonPath("$.body[1].id").exists());
    }
    
    @Test
    @DisplayName("Batch Update User Roles Test")
    void batchUpdateUserRolesTest() throws Exception {
        // Given
        UserRoleBatchUpdateDTO batchUpdateDTO = new UserRoleBatchUpdateDTO();
        Set<String> ids = Set.of(
            faker.internet().uuid(),
            faker.internet().uuid(),
            faker.internet().uuid()
        );
        batchUpdateDTO.setIds(ids);
        
        doNothing().when(userRoleService).batchUpdate(any(UserRoleBatchUpdateDTO.class));
        
        // When & Then
        mockMvc.perform(patch("/api/user/role/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batchUpdateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.message").exists());
    }
    
    @Test
    @DisplayName("Batch Delete User Roles Test")
    void batchDeleteUserRolesTest() throws Exception {
        // Given
        List<String> ids = List.of(
            faker.internet().uuid(),
            faker.internet().uuid(),
            faker.internet().uuid()
        );
        
        doNothing().when(userRoleService).batchDelete(anyList());
        
        // When & Then
        mockMvc.perform(delete("/api/user/role/batch")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .param("ids", ids.get(0))
                .param("ids", ids.get(1))
                .param("ids", ids.get(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Search User Roles with GET Test")
    void searchUserRolesGetTest() throws Exception {
        // Given
        List<UserRoleListDTO> results = new ArrayList<>();
        // Create actual objects instead of mocks
        UserRoleListDTO dto1 = new UserRoleListDTO();
        dto1.setId(faker.internet().uuid());
        dto1.setName(faker.lorem().word());
        dto1.setRole(faker.lorem().word());
        
        UserRoleListDTO dto2 = new UserRoleListDTO();
        dto2.setId(faker.internet().uuid());
        dto2.setName(faker.lorem().word());
        dto2.setRole(faker.lorem().word());
        
        results.add(dto1);
        results.add(dto2);
        
        Page<UserRoleListDTO> pagedResults = new PageImpl<>(results);
        
        when(userRoleService.search(anyMap())).thenReturn(pagedResults);
        
        // When & Then
        mockMvc.perform(get("/api/user/role/search")
                .accept(MediaType.APPLICATION_JSON)
                .param("name", "test")
                .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body.content").isArray())
                .andExpect(jsonPath("$.body.content.length()").value(2))
                .andExpect(jsonPath("$.body.content[0].id").value(dto1.getId()))
                .andExpect(jsonPath("$.body.content[1].id").value(dto2.getId()));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Search User Roles with POST Test")
    void searchUserRolesPostTest() throws Exception {
        // Given
        List<UserRoleListDTO> results = new ArrayList<>();
        // Create actual objects instead of mocks
        UserRoleListDTO dto1 = new UserRoleListDTO();
        dto1.setId(faker.internet().uuid());
        dto1.setName(faker.lorem().word());
        dto1.setRole(faker.lorem().word());
        
        UserRoleListDTO dto2 = new UserRoleListDTO();
        dto2.setId(faker.internet().uuid());
        dto2.setName(faker.lorem().word());
        dto2.setRole(faker.lorem().word());
        
        results.add(dto1);
        results.add(dto2);
        
        Page<UserRoleListDTO> pagedResults = new PageImpl<>(results);
        SearchCondition<UserRoleSearchDTO> searchCondition = new SearchCondition<>();
        
        when(userRoleService.search(any(SearchCondition.class))).thenReturn(pagedResults);
        
        // When & Then
        mockMvc.perform(post("/api/user/role/search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(searchCondition)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body.content").isArray())
                .andExpect(jsonPath("$.body.content.length()").value(2))
                .andExpect(jsonPath("$.body.content[0].id").value(dto1.getId()))
                .andExpect(jsonPath("$.body.content[1].id").value(dto2.getId()));
    }
} 