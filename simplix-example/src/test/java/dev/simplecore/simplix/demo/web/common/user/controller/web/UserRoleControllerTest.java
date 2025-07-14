package dev.simplecore.simplix.demo.web.common.user.controller.web;

import dev.simplecore.simplix.demo.web.common.user.dto.UserRoleDTOs.UserRoleDetailDTO;
import dev.simplecore.simplix.demo.web.common.user.service.UserRoleService;
import net.datafaker.Faker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.NestedServletException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserRoleControllerTest {

    private MockMvc mockMvc;
    
    @Mock
    private UserRoleService userRoleService;
    
    @InjectMocks
    private UserRoleController userRoleController;
    
    private Faker faker;
    private AtomicReference<BigDecimal> itemOrderCounter;

    @BeforeEach
    void setUp() {
        faker = new Faker(Locale.US);
        // Use current time + random number to ensure uniqueness across test runs
        BigDecimal initialValue = BigDecimal.valueOf(System.currentTimeMillis() % 10000)
            .add(BigDecimal.valueOf(faker.random().nextInt(1000)));
        itemOrderCounter = new AtomicReference<>(initialValue);
        mockMvc = MockMvcBuilders
                .standaloneSetup(userRoleController)
                .alwaysDo(print())
                .build();
    }
    
    /**
     * Creates a UserRoleDetailDTO with random data
     */
    private UserRoleDetailDTO createRandomUserRoleDetailDTO() {
        UserRoleDetailDTO dto = new UserRoleDetailDTO();
        dto.setId(faker.internet().uuid());
        dto.setName(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        dto.setRole(faker.lorem().word() + "_" + System.currentTimeMillis() + "_" + faker.random().nextInt(1000));
        dto.setDescription(faker.lorem().sentence(8));
        dto.setItemOrder(itemOrderCounter.getAndUpdate(current -> current.add(BigDecimal.ONE)));
        return dto;
    }

    @Test
    @DisplayName("List Page Test")
    void listTest() throws Exception {
        // When & Then
        mockMvc.perform(get("/user/role/list"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/role/list"))
                .andExpect(model().attributeExists("pageTitle"));
    }

    @Test
    @DisplayName("Detail Page Test - Item Found")
    void detailFoundTest() throws Exception {
        // Given
        String id = faker.internet().uuid();
        UserRoleDetailDTO detailDTO = createRandomUserRoleDetailDTO();
        
        when(userRoleService.findById(eq(id), eq(UserRoleDetailDTO.class))).thenReturn(Optional.of(detailDTO));
        
        // When & Then
        mockMvc.perform(get("/user/role/detail/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("user/role/detail"))
                .andExpect(model().attributeExists("pageTitle"))
                .andExpect(model().attributeExists("item"))
                .andExpect(model().attribute("item", detailDTO));
    }
    
    @Test
    @DisplayName("Detail Page Test - Item Not Found")
    void detailNotFoundTest() throws Exception {
        // Given
        String id = faker.internet().uuid();
        
        when(userRoleService.findById(eq(id), eq(UserRoleDetailDTO.class))).thenReturn(Optional.empty());
        
        // When & Then
        mockMvc.perform(get("/user/role/detail/{id}", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/user/role/list"));
                // Not checking exact redirectedUrl because it may include pageTitle parameter
    }
    
    @Test
    @DisplayName("Brief Content Fragment Test")
    void getBriefTest() throws Exception {
        // Given
        String id = faker.internet().uuid();
        UserRoleDetailDTO detailDTO = createRandomUserRoleDetailDTO();
        
        when(userRoleService.findById(eq(id), eq(UserRoleDetailDTO.class))).thenReturn(Optional.of(detailDTO));
        
        // When & Then
        mockMvc.perform(get("/user/role/brief/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("user/role/detail :: brief-content"))
                .andExpect(model().attributeExists("item"))
                .andExpect(model().attribute("item", detailDTO));
    }
    
    @Test
    @DisplayName("Edit Page Test")
    void editTest() throws Exception {
        // Given
        String id = faker.internet().uuid();
        UserRoleDetailDTO detailDTO = createRandomUserRoleDetailDTO();
        
        when(userRoleService.findById(eq(id), eq(UserRoleDetailDTO.class))).thenReturn(Optional.of(detailDTO));
        
        // When & Then
        mockMvc.perform(get("/user/role/edit/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("user/role/edit"))
                .andExpect(model().attributeExists("pageTitle"))
                .andExpect(model().attributeExists("item"))
                .andExpect(model().attribute("item", detailDTO));
    }
    
    @Test
    @DisplayName("Create Page Test")
    void createTest() throws Exception {
        // When & Then
        mockMvc.perform(get("/user/role/create"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/role/edit"))
                .andExpect(model().attributeExists("pageTitle"));
    }
    
    @Test
    @DisplayName("Edit Page Test - Item Not Found")
    void editNotFoundTest() throws Exception {
        // Given
        String id = faker.internet().uuid();
        
        when(userRoleService.findById(eq(id), eq(UserRoleDetailDTO.class))).thenReturn(Optional.empty());
        
        // When & Then
        NestedServletException exception = Assertions.assertThrows(
            NestedServletException.class,
            () -> mockMvc.perform(get("/user/role/edit/{id}", id))
        );
        
        // Verify the exception
        Throwable rootCause = exception.getCause();
        Assertions.assertNotNull(rootCause);
        Assertions.assertTrue(rootCause instanceof RuntimeException);
        Assertions.assertEquals("UserRole not found", rootCause.getMessage());
    }
} 