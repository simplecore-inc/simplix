package dev.simplecore.simplix.demo.web.common.user.controller.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserRole;
import dev.simplecore.simplix.demo.web.common.user.dto.UserRoleDTOs.*;
import dev.simplecore.simplix.demo.web.common.user.service.UserRoleService;
import dev.simplecore.simplix.core.util.UuidUtils;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

import java.util.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserRoleRestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserRoleService userRoleService;

    @InjectMocks
    private UserRoleRestController userRoleRestController;
    
    private ObjectMapper objectMapper;

    private Faker faker;
    private AtomicInteger itemOrderCounter;

    @BeforeEach
    void setUp() {
        faker = new Faker(Locale.US);
        // Use current time + random number to ensure uniqueness across test runs
        int initialValue = (int)(System.currentTimeMillis() % 10000) + faker.random().nextInt(1000);
        itemOrderCounter = new AtomicInteger(initialValue);

        objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // Use the injected controller instance
        mockMvc = MockMvcBuilders.standaloneSetup(userRoleRestController).build();
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
        userRole.setId(UuidUtils.generateUuidV7());
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
    
    /**
     * Creates a UserRoleDetailDTO with random data
     */
    private UserRoleDetailDTO createRandomUserRoleDetailDTO() {
        UserRoleDetailDTO dto = new UserRoleDetailDTO();
        dto.setRoleId(UuidUtils.generateUuidV7());
        dto.setName(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        dto.setRole(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
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

        mockMvc.perform(post("/api/user/role/create")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body.roleId").value(resultDTO.getRoleId()))
                .andExpect(jsonPath("$.body.name").value(resultDTO.getName()))
                .andExpect(jsonPath("$.body.role").value(resultDTO.getRole()));
    }

    @Test
    @DisplayName("Update User Role Test")
    void updateUserRoleTest() throws Exception {
        // Given
        String id = UuidUtils.generateUuidV7();
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
                .andExpect(jsonPath("$.body.roleId").value(resultDTO.getRoleId()))
                .andExpect(jsonPath("$.body.name").value(resultDTO.getName()))
                .andExpect(jsonPath("$.body.role").value(resultDTO.getRole()));
    }

    @Test
    @DisplayName("Delete User Role Test")
    void deleteUserRoleTest() throws Exception {
        // Given
        String id = UuidUtils.generateUuidV7();
        
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
        String id = UuidUtils.generateUuidV7();
        UserRoleDetailDTO detailDTO = createRandomUserRoleDetailDTO();
        
        when(userRoleService.findById(eq(id), eq(UserRoleDetailDTO.class))).thenReturn(Optional.of(detailDTO));
        
        // When & Then
        mockMvc.perform(get("/api/user/role/{id}", id)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body.roleId").value(detailDTO.getRoleId()))
                .andExpect(jsonPath("$.body.name").value(detailDTO.getName()))
                .andExpect(jsonPath("$.body.role").value(detailDTO.getRole()));
    }
    
    @Test
    @DisplayName("User Role Not Found Test")
    void userRoleNotFoundTest() throws Exception {
        // Given
        String id = UuidUtils.generateUuidV7();
        
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
        updateDTOs.add(createRandomUserRoleUpdateDTO(UuidUtils.generateUuidV7()));
        updateDTOs.add(createRandomUserRoleUpdateDTO(UuidUtils.generateUuidV7()));
        
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
                .andExpect(jsonPath("$.body[0].roleId").exists())
                .andExpect(jsonPath("$.body[1].roleId").exists());
    }
    
    @Test
    @DisplayName("Batch Update User Roles Test")
    void batchUpdateUserRolesTest() throws Exception {
        // Given
        UserRoleBatchUpdateDTO batchUpdateDTO = new UserRoleBatchUpdateDTO();
        Set<String> ids = Set.of(
            UuidUtils.generateUuidV7(),
            UuidUtils.generateUuidV7(),
            UuidUtils.generateUuidV7()
        );
        batchUpdateDTO.setRoleIds(ids);
        
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
            UuidUtils.generateUuidV7(),
            UuidUtils.generateUuidV7(),
            UuidUtils.generateUuidV7()
        );
        
        doNothing().when(userRoleService).batchDelete(anyList());
        
        // When & Then
        mockMvc.perform(delete("/api/user/role/batch")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .param("roleIds", ids.get(0))
                .param("roleIds", ids.get(1))
                .param("roleIds", ids.get(2)))
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
        dto1.setRoleId(UuidUtils.generateUuidV7());
        dto1.setName(faker.lorem().word());
        dto1.setRole(faker.lorem().word());
        
        UserRoleListDTO dto2 = new UserRoleListDTO();
        dto2.setRoleId(faker.internet().uuid());
        dto2.setName(faker.lorem().word());
        dto2.setRole(faker.lorem().word());
        
        results.add(dto1);
        results.add(dto2);
        
        Page<UserRoleListDTO> pagedResults = new PageImpl<>(results, Pageable.ofSize(10), results.size());
        
        when(userRoleService.search(anyMap())).thenReturn(pagedResults);
        mockMvc.perform(get("/api/user/role/search")
                .accept(MediaType.APPLICATION_JSON)
                .param("name", "test")
                .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body.content").isArray())
                .andExpect(jsonPath("$.body.content.length()").value(2))
                .andExpect(jsonPath("$.body.content[0].roleId").value(dto1.getRoleId()))
                .andExpect(jsonPath("$.body.content[1].roleId").value(dto2.getRoleId()));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Search User Roles with POST Test")
    void searchUserRolesPostTest() throws Exception {
        // Given
        List<UserRoleListDTO> results = new ArrayList<>();
        // Create actual objects instead of mocks
        UserRoleListDTO dto1 = new UserRoleListDTO();
        dto1.setRoleId(UuidUtils.generateUuidV7());
        dto1.setName(faker.lorem().word());
        dto1.setRole(faker.lorem().word());
        
        UserRoleListDTO dto2 = new UserRoleListDTO();
        dto2.setRoleId(faker.internet().uuid());
        dto2.setName(faker.lorem().word());
        dto2.setRole(faker.lorem().word());
        
        results.add(dto1);
        results.add(dto2);
        
        Page<UserRoleListDTO> pagedResults = new PageImpl<>(results, Pageable.ofSize(10), results.size());
        SearchCondition<UserRoleSearchDTO> searchCondition = new SearchCondition<>();
        
        when(userRoleService.search(any(SearchCondition.class))).thenReturn(pagedResults);

        mockMvc.perform(post("/api/user/role/search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(searchCondition)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body.content").isArray())
                .andExpect(jsonPath("$.body.content.length()").value(2))
                .andExpect(jsonPath("$.body.content[0].roleId").value(dto1.getRoleId()))
                .andExpect(jsonPath("$.body.content[1].roleId").value(dto2.getRoleId()));
    }

    @Test
    @DisplayName("Update User Role Orders Test")
    void updateUserRoleOrdersTest() throws Exception {
        // Given
        String roleId1 = UuidUtils.generateUuidV7();
        String roleId2 = UuidUtils.generateUuidV7();
        Integer newOrder1 = 100;
        Integer newOrder2 = 200;
        
        UserRoleOrderUpdateDTO orderUpdateDto1 = new UserRoleOrderUpdateDTO();
        orderUpdateDto1.setRoleId(roleId1);
        orderUpdateDto1.setItemOrder(newOrder1);
        
        UserRoleOrderUpdateDTO orderUpdateDto2 = new UserRoleOrderUpdateDTO();
        orderUpdateDto2.setRoleId(roleId2);
        orderUpdateDto2.setItemOrder(newOrder2);
        
        List<UserRoleOrderUpdateDTO> orderUpdateDtos = List.of(orderUpdateDto1, orderUpdateDto2);
        
        UserRoleDetailDTO resultDTO1 = createRandomUserRoleDetailDTO();
        resultDTO1.setRoleId(roleId1);
        resultDTO1.setItemOrder(newOrder1);
        
        UserRoleDetailDTO resultDTO2 = createRandomUserRoleDetailDTO();
        resultDTO2.setRoleId(roleId2);
        resultDTO2.setItemOrder(newOrder2);
        
        List<UserRoleDetailDTO> resultDTOs = List.of(resultDTO1, resultDTO2);
        
        when(userRoleService.updateOrders(anyList())).thenReturn(resultDTOs);
        
        // When & Then
        mockMvc.perform(patch("/api/user/role/order")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderUpdateDtos)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.body").isArray())
                .andExpect(jsonPath("$.body.length()").value(2))
                .andExpect(jsonPath("$.body[0].roleId").value(roleId1))
                .andExpect(jsonPath("$.body[0].itemOrder").value(newOrder1))
                .andExpect(jsonPath("$.body[1].roleId").value(roleId2))
                .andExpect(jsonPath("$.body[1].itemOrder").value(newOrder2))
                .andExpect(jsonPath("$.message").value("UserRole orders updated successfully"));
    }
} 