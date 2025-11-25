package dev.simplecore.simplix.web.config;

import dev.simplecore.searchable.core.annotation.SearchableField;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Id;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.Length;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Swagger customizer to add internationalized validation messages, SearchableField information, and ID field metadata as extension fields
 */
@Component
public class SwaggerSchemaEnhancer implements OpenApiCustomizer {

    private static final Logger log = LoggerFactory.getLogger(SwaggerSchemaEnhancer.class);

    @Autowired
    private MessageSource messageSource;
    

    
    // Cache for schema name to class mapping
    private final Map<String, Class<?>> schemaClassCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        log.info("Initializing SwaggerSchemaEnhancer with SearchableField and ID field support");
        try {
            cacheSchemaClasses();
            
            // Test SearchableField detection
            testSearchableFieldDetection();
        } catch (Exception e) {
            log.error("Failed to cache schema classes", e);
        }
    }

    @Override
    public void customise(OpenAPI openApi) {
        log.info("Starting OpenAPI customization with SearchableField and ID field support");
        if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
            log.info("Found {} schemas to process", openApi.getComponents().getSchemas().size());
            openApi.getComponents().getSchemas().forEach((schemaName, schema) -> {
                log.info("Processing schema: {}", schemaName);
                
                // Special handling for SearchCondition schemas
                if (schemaName.startsWith("SearchCondition") && schemaName.endsWith("SearchDTO")) {
                    handleSearchConditionSchema(schemaName, schema);
                }
                
                // Regular schema processing
                Class<?> clazz = findClassForSchema(schemaName);
                if (clazz != null) {
                    log.info("Found class {} for schema {}", clazz.getName(), schemaName);
                    addI18nExtensionsToSchema(schema, clazz);
                } else {
                    log.debug("No class found for schema: {}", schemaName);
                }
            });
        } else {
            log.warn("No components or schemas found in OpenAPI");
        }
        log.info("Completed OpenAPI customization");
    }
    
    private void cacheSchemaClasses() {
        try {
            List<String> packageNames = Arrays.asList(
                "dev.simplecore.simplix.demo",
                "dev.simplecore.simplix.core.model",
                "dev.simplecore.simplix.web.model"
            );
            
            for (String packageName : packageNames) {
                log.info("Searching for classes in package: {}", packageName);
                Set<Class<?>> classes = findClassesInPackage(packageName);
                log.info("Found {} classes in package {}", classes.size(), packageName);
                
                for (Class<?> clazz : classes) {
                    // Cache the outer class
                    String simpleName = clazz.getSimpleName();
                    schemaClassCache.put(simpleName, clazz);
                    log.info("Cached class: {} -> {}", simpleName, clazz.getName());
                    
                    // Also cache inner static classes
                    Class<?>[] innerClasses = clazz.getDeclaredClasses();
                    for (Class<?> innerClass : innerClasses) {
                        if (java.lang.reflect.Modifier.isStatic(innerClass.getModifiers())) {
                            String innerSimpleName = innerClass.getSimpleName();
                            schemaClassCache.put(innerSimpleName, innerClass);
                            log.info("Cached inner class: {} -> {}", innerSimpleName, innerClass.getName());
                        }
                    }
                }
            }
            
            log.info("Cached {} classes total", schemaClassCache.size());
            log.info("Cached class names: {}", schemaClassCache.keySet());
        } catch (Exception e) {
            log.error("Failed to cache schema classes", e);
        }
    }
    
    private Class<?> findClassForSchema(String schemaName) {
        log.debug("Finding class for schema: {}", schemaName);
        
        // Handle SearchCondition schemas specially
        if (schemaName.startsWith("SearchCondition") && schemaName.endsWith("SearchDTO")) {
            String searchDtoName = schemaName.substring("SearchCondition".length());
            log.debug("Extracted SearchDTO name from SearchCondition: {}", searchDtoName);
            
            Class<?> searchDtoClass = schemaClassCache.get(searchDtoName);
            if (searchDtoClass != null) {
                log.debug("Found SearchDTO class: {}", searchDtoClass.getName());
                return searchDtoClass;
            }
        }
        
        // Handle regular schemas
        Class<?> clazz = schemaClassCache.get(schemaName);
        if (clazz != null) {
            log.debug("Found class: {} for schema: {}", clazz.getName(), schemaName);
            return clazz;
        }
        
        log.debug("No class found for schema: {}", schemaName);
        return null;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void addI18nExtensionsToSchema(Schema schema, Class<?> clazz) {
        log.info("Processing schema for class: {}", clazz.getName());
        if (schema.getProperties() != null) {
            log.info("Schema has {} properties", schema.getProperties().size());
            schema.getProperties().forEach((propertyName, propertySchema) -> {
                String fieldName = (String) propertyName;
                try {
                    Schema schemaProperty = (Schema) propertySchema;
                    log.info("Processing property: {} in class: {} (schema type: {}, $ref: {})", 
                        fieldName, clazz.getSimpleName(), 
                        schemaProperty.getType(), 
                        schemaProperty.get$ref());
                    Field field = findField(clazz, fieldName);
                    if (field != null) {
                        log.info("Found field {} in class {}", fieldName, clazz.getSimpleName());
                        
                        // Process all extensions for this field
                        addI18nExtensions(field, schemaProperty, fieldName);
                        addSearchableFieldExtensions(field, schemaProperty, fieldName);
                        addIdFieldExtensions(field, schemaProperty, fieldName);

                        // Log final extensions after all processing
                        if (schemaProperty.getExtensions() != null) {
                            log.info("Final extensions for field {}: {}", fieldName, 
                                schemaProperty.getExtensions().keySet());
                        }
                    } else {
                        log.warn("Field not found: {}.{}", clazz.getSimpleName(), fieldName);
                    }
                } catch (Exception e) {
                    log.error("Error processing field {}.{}: {}", clazz.getSimpleName(), fieldName, e.getMessage(), e);
                }
            });
        } else {
            log.warn("Schema has no properties for class: {}", clazz.getName());
        }
    }
    
    private Field findField(Class<?> clazz, String fieldName) {
        log.debug("Looking for field '{}' in class '{}'", fieldName, clazz.getName());
        try {
            Field field = clazz.getDeclaredField(fieldName);
            log.debug("Found field '{}' in class '{}'", fieldName, clazz.getName());
            return field;
        } catch (NoSuchFieldException e) {
            log.debug("Field '{}' not found in class '{}', checking superclass", fieldName, clazz.getName());
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            log.debug("Field '{}' not found in class hierarchy starting from '{}'", fieldName, clazz.getName());
            return null;
        }
    }
    
    @SuppressWarnings("rawtypes")
    private void addI18nExtensions(Field field, Schema propertySchema, String fieldName) {
        log.info("Processing field: {} with schema type: {}, $ref: {}",
            fieldName, propertySchema.getType(), propertySchema.get$ref());

        // Get validation messages
        Map<String, String> validationMessages = getValidationMessages(field, fieldName);

        // Add validation messages
        if (!validationMessages.isEmpty()) {
            propertySchema.addExtension("x-i18n-validation-messages", validationMessages);
            log.info("Added validation messages for field {}: {}", fieldName, validationMessages);
        }
    }
    
    @SuppressWarnings("rawtypes")
    private void addSearchableFieldExtensions(Field field, Schema propertySchema, String fieldName) {
        log.debug("Checking SearchableField annotation for field: {}", fieldName);
        
        // Check all annotations on the field
        Annotation[] annotations = field.getAnnotations();
        log.debug("Field {} has {} annotations", fieldName, annotations.length);
        for (Annotation annotation : annotations) {
            log.debug("Found annotation: {} on field {}", annotation.annotationType().getName(), fieldName);
        }
        
        // Try different ways to get the SearchableField annotation
        SearchableField searchableField = null;
        
        // Method 1: Direct annotation lookup
        try {
            searchableField = field.getAnnotation(SearchableField.class);
            if (searchableField != null) {
                log.info("Found SearchableField annotation using direct lookup on field: {}", fieldName);
            }
        } catch (Exception e) {
            log.debug("Direct annotation lookup failed for field {}: {}", fieldName, e.getMessage());
        }
        
        // Method 2: Check if any annotation is SearchableField by class name
        if (searchableField == null) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().getName().equals("dev.simplecore.searchable.core.annotation.SearchableField")) {
                    log.info("Found SearchableField annotation by class name on field: {}", fieldName);
                    try {
                        // Use reflection to get the annotation values
                        Class<?> annotationType = annotation.annotationType();
                        Object operatorsValue = annotationType.getMethod("operators").invoke(annotation);
                        Object sortableValue = annotationType.getMethod("sortable").invoke(annotation);
                        Object entityFieldValue = annotationType.getMethod("entityField").invoke(annotation);
                        
                        Map<String, Object> searchableInfo = new HashMap<>();
                        
                        // Add operators information
                        if (operatorsValue != null && operatorsValue.getClass().isArray()) {
                            Object[] operators = (Object[]) operatorsValue;
                            List<String> operatorNames = Arrays.stream(operators)
                                .map(Object::toString)
                                .collect(Collectors.toList());
                            searchableInfo.put("operators", operatorNames);
                            log.debug("Added operators for field {}: {}", fieldName, operatorNames);
                        }
                        
                        // Add sortable information
                        if (sortableValue != null) {
                            searchableInfo.put("sortable", sortableValue);
                            log.debug("Added sortable for field {}: {}", fieldName, sortableValue);
                        }
                        
                        // Add entityField information if present
                        if (entityFieldValue != null && !entityFieldValue.toString().isEmpty()) {
                            searchableInfo.put("entityField", entityFieldValue);
                            log.debug("Added entityField for field {}: {}", fieldName, entityFieldValue);
                        }
                        
                        propertySchema.addExtension("x-searchable-field", searchableInfo);
                        log.info("Successfully added SearchableField extensions for field {} using reflection: {}", fieldName, searchableInfo);
                        
                        // Verify the extension was added
                        Object addedExtension = propertySchema.getExtensions() != null ? 
                            propertySchema.getExtensions().get("x-searchable-field") : null;
                        if (addedExtension != null) {
                            log.info("VERIFIED: Extension was added for field {}: {}", fieldName, addedExtension);
                        } else {
                            log.error("ERROR: Extension was NOT added for field {}", fieldName);
                        }
                        
                        // Also log all extensions for this field
                        if (propertySchema.getExtensions() != null) {
                            log.info("All extensions for field {}: {}", fieldName, propertySchema.getExtensions().keySet());
                        }
                        
                        return; // Successfully processed
                    } catch (Exception e) {
                        log.error("Failed to process SearchableField annotation using reflection for field {}: {}", fieldName, e.getMessage());
                    }
                }
            }
        }
        
        if (searchableField != null) {
            log.info("Found SearchableField annotation on field: {}", fieldName);
            Map<String, Object> searchableInfo = new HashMap<>();
            
            // Add operators information
            if (searchableField.operators() != null && searchableField.operators().length > 0) {
                List<String> operators = Arrays.stream(searchableField.operators())
                    .map(Enum::name)
                    .collect(Collectors.toList());
                searchableInfo.put("operators", operators);
                log.info("Added operators for field {}: {}", fieldName, operators);
            }
            
            // Add sortable information
            searchableInfo.put("sortable", searchableField.sortable());
            log.info("Added sortable for field {}: {}", fieldName, searchableField.sortable());
            
            // Add entityField information if present
            if (searchableField.entityField() != null && !searchableField.entityField().isEmpty()) {
                searchableInfo.put("entityField", searchableField.entityField());
                log.info("Added entityField for field {}: {}", fieldName, searchableField.entityField());
            }
            
            // Log before adding extension
            log.info("About to add extension x-searchable-field for field {}: {}", fieldName, searchableInfo);
            
            // Check if extensions map exists
            if (propertySchema.getExtensions() == null) {
                log.info("Extensions map is null for field {}, will be created", fieldName);
            } else {
                log.info("Extensions map exists for field {}, current size: {}", fieldName, propertySchema.getExtensions().size());
            }
            
            propertySchema.addExtension("x-searchable-field", searchableInfo);
            log.info("Successfully added SearchableField extensions for field {}: {}", fieldName, searchableInfo);
            
            // Verify the extension was added
            Object addedExtension = propertySchema.getExtensions() != null ? 
                propertySchema.getExtensions().get("x-searchable-field") : null;
            if (addedExtension != null) {
                log.info("VERIFIED: Extension was added for field {}: {}", fieldName, addedExtension);
            } else {
                log.error("ERROR: Extension was NOT added for field {}", fieldName);
            }
            
            // Also log all extensions for this field
            if (propertySchema.getExtensions() != null) {
                log.info("All extensions for field {}: {}", fieldName, propertySchema.getExtensions().keySet());
            }
        } else {
            log.debug("No SearchableField annotation found on field: {}", fieldName);
        }
    }
    
    @SuppressWarnings("rawtypes")
    private void addIdFieldExtensions(Field field, Schema propertySchema, String fieldName) {
        log.info("Processing ID field: {} with schema type: {}, $ref: {}", 
            fieldName, propertySchema.getType(), propertySchema.get$ref());
        
        boolean isIdField = false;
        String idType = null;
        
        // Check for @Id annotation
        Id idAnnotation = field.getAnnotation(Id.class);
        if (idAnnotation != null) {
            isIdField = true;
            idType = "simple";
            log.info("Found @Id annotation on field: {}", fieldName);
        }
        
        // Check for @EmbeddedId annotation
        EmbeddedId embeddedIdAnnotation = field.getAnnotation(EmbeddedId.class);
        if (embeddedIdAnnotation != null) {
            isIdField = true;
            idType = "embedded";
            log.info("Found @EmbeddedId annotation on field: {}", fieldName);
        }
        
        if (isIdField) {
            Map<String, Object> idInfo = new HashMap<>();
            idInfo.put("type", idType);
            idInfo.put("isPrimaryKey", true);
            
            // Add field type information
            Class<?> fieldType = field.getType();
            idInfo.put("fieldType", fieldType.getSimpleName());
            
            // For embedded IDs, add additional information
            if ("embedded".equals(idType)) {
                idInfo.put("compositeKey", true);
                idInfo.put("embeddedClass", fieldType.getName());
            }
            
            // Handle both $ref and regular properties
            propertySchema.addExtension("x-id-field", idInfo);
            log.info("Successfully added ID field extensions for field {}: {}", fieldName, idInfo);
            
            // Verify the extension was added
            Object addedExtension = propertySchema.getExtensions() != null ? 
                propertySchema.getExtensions().get("x-id-field") : null;
            if (addedExtension != null) {
                log.info("VERIFIED: ID field extension was added for field {}: {}", fieldName, addedExtension);
            } else {
                log.error("ERROR: ID field extension was NOT added for field {}", fieldName);
            }
            
            // Also log all extensions for this field
            if (propertySchema.getExtensions() != null) {
                log.info("All extensions for field {}: {}", fieldName, propertySchema.getExtensions().keySet());
            }
        } else {
            log.debug("No ID field annotation found on field: {}", fieldName);
        }
    }
private Map<String, String> getValidationMessages(Field field, String fieldName) {
        Map<String, String> messages = new HashMap<>();
        
        // Check for validation annotations and get corresponding messages
        Annotation[] annotations = field.getAnnotations();
        for (Annotation annotation : annotations) {
            String messageKey = null;
            String defaultMessage = null;
            
            if (annotation instanceof NotNull notNull) {
				messageKey = "validation.notnull";
                defaultMessage = notNull.message();
            } else if (annotation instanceof NotBlank notBlank) {
				messageKey = "validation.notblank";
                defaultMessage = notBlank.message();
            } else if (annotation instanceof NotEmpty notEmpty) {
				messageKey = "validation.notempty";
                defaultMessage = notEmpty.message();
            } else if (annotation instanceof Size size) {
				messageKey = "validation.size";
                defaultMessage = size.message();
            } else if (annotation instanceof Min min) {
				messageKey = "validation.min";
                defaultMessage = min.message();
            } else if (annotation instanceof Max max) {
				messageKey = "validation.max";
                defaultMessage = max.message();
            } else if (annotation instanceof Pattern pattern) {
				messageKey = "validation.pattern";
                defaultMessage = pattern.message();
            } else if (annotation instanceof Email email) {
				messageKey = "validation.email";
                defaultMessage = email.message();
            } else if (annotation instanceof Length length) {
				messageKey = "validation.length";
                defaultMessage = length.message();
            }
            
            if (messageKey != null) {
                try {
                    // Try to get Korean message
                    String koMessage = messageSource.getMessage(messageKey, null, Locale.KOREAN);
                    messages.put("ko", koMessage);
                } catch (Exception e) {
                    // Use default message if no Korean message found
                    messages.put("ko", defaultMessage);
                }
                
                try {
                    // Try to get English message
                    String enMessage = messageSource.getMessage(messageKey, null, Locale.ENGLISH);
                    messages.put("en", enMessage);
                } catch (Exception e) {
                    // Use default message if no English message found
                    messages.put("en", defaultMessage);
                }
            }
        }
        
        return messages;
    }
    
    private void testSearchableFieldDetection() {
        try {
            // Test with a known class that has SearchableField annotations
            Class<?> testClass = schemaClassCache.get("UserAccountSearchDTO");
            if (testClass != null) {
                log.info("Testing SearchableField detection with class: {}", testClass.getName());
                
                Field[] fields = testClass.getDeclaredFields();
                log.info("Class {} has {} fields", testClass.getSimpleName(), fields.length);
                
                for (Field field : fields) {
                    SearchableField searchableField = field.getAnnotation(SearchableField.class);
                    if (searchableField != null) {
                        log.info("Found SearchableField on field {}: operators={}, sortable={}", 
                            field.getName(), 
                            Arrays.toString(searchableField.operators()),
                            searchableField.sortable());
                    } else {
                        log.debug("No SearchableField on field: {}", field.getName());
                    }
                }
            } else {
                log.warn("UserAccountSearchDTO not found in cache");
                log.info("Available classes in cache: {}", schemaClassCache.keySet());
            }

            // Test ID field detection
            testIdDetection();
        } catch (Exception e) {
            log.error("Failed to test SearchableField detection", e);
        }
    }
    
    private void testIdDetection() {
        try {
            // Test with UserAccount class
            Class<?> userAccountClass = schemaClassCache.get("UserAccount");
            if (userAccountClass != null) {
                log.info("Testing ID detection with class: {}", userAccountClass.getName());

                Field[] fields = userAccountClass.getDeclaredFields();
                log.info("Class {} has {} fields", userAccountClass.getSimpleName(), fields.length);

                for (Field field : fields) {
                    // Check for ID annotations
                    Id idAnnotation = field.getAnnotation(Id.class);
                    EmbeddedId embeddedIdAnnotation = field.getAnnotation(EmbeddedId.class);

                    if (idAnnotation != null) {
                        log.info("Found @Id annotation on field: {}", field.getName());
                    }
                    if (embeddedIdAnnotation != null) {
                        log.info("Found @EmbeddedId annotation on field: {}", field.getName());
                    }
                }
            } else {
                log.warn("UserAccount not found in cache");
            }
        } catch (Exception e) {
            log.error("Failed to test ID detection", e);
        }
    }

    private Set<Class<?>> findClassesInPackage(String packageName) {
        Set<Class<?>> classes = new HashSet<>();
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            log.info("Looking for resources in path: {}", path);
            Enumeration<URL> resources = classLoader.getResources(path);
            
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                log.info("Found resource: {} with protocol: {}", resource, resource.getProtocol());
                
                if (resource.getProtocol().equals("file")) {
                    // Handle file system
                    File directory = new File(resource.getFile());
                    log.info("Checking directory: {} (exists: {})", directory.getAbsolutePath(), directory.exists());
                    if (directory.exists()) {
                        findClassesInDirectory(directory, packageName, classes);
                    }
                } else if (resource.getProtocol().equals("jar")) {
                    // Handle JAR files
                    String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
                    log.info("Checking JAR: {}", jarPath);
                    findClassesInJar(jarPath, packageName, classes);
                }
            }
        } catch (Exception e) {
            log.error("Error finding classes in package: {}", packageName, e);
        }
        log.info("Found {} classes in package {}", classes.size(), packageName);
        return classes;
    }
    
    private void findClassesInDirectory(File directory, String packageName, Set<Class<?>> classes) {
        if (!directory.exists()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findClassesInDirectory(file, packageName + "." + file.getName(), classes);
                } else if (file.getName().endsWith(".class")) {
                    String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                    try {
                        Class<?> clazz = Class.forName(className);
                        classes.add(clazz);
                    } catch (ClassNotFoundException e) {
                        log.debug("Could not load class: {}", className);
                    }
                }
            }
        }
    }
    
    private void findClassesInJar(String jarPath, String packageName, Set<Class<?>> classes) {
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            String packagePath = packageName.replace('.', '/');
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                
                if (name.startsWith(packagePath) && name.endsWith(".class") && !entry.isDirectory()) {
                    String className = name.replace('/', '.').substring(0, name.length() - 6);
                    try {
                        Class<?> clazz = Class.forName(className);
                        classes.add(clazz);
                    } catch (ClassNotFoundException e) {
                        log.debug("Could not load class: {}", className);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error reading JAR file: {}", jarPath, e);
        }
    }

    @SuppressWarnings("rawtypes")
    private void handleSearchConditionSchema(String schemaName, Schema schema) {
        log.info("Special handling for SearchCondition schema: {}", schemaName);
        
        // Extract the SearchDTO class name
        String searchDtoName = schemaName.substring("SearchCondition".length());
        log.info("Looking for SearchDTO class: {}", searchDtoName);
        
        Class<?> searchDtoClass = schemaClassCache.get(searchDtoName);
        if (searchDtoClass != null) {
            log.info("Found SearchDTO class: {}", searchDtoClass.getName());
            
            // Extract SearchableField information
            Map<String, Object> searchableFields = new HashMap<>();
            Field[] fields = searchDtoClass.getDeclaredFields();
            
            for (Field field : fields) {
                SearchableField searchableField = field.getAnnotation(SearchableField.class);
                if (searchableField != null) {
                    Map<String, Object> fieldInfo = new HashMap<>();
                    
                    // Add operators
                    if (searchableField.operators() != null && searchableField.operators().length > 0) {
                        List<String> operators = Arrays.stream(searchableField.operators())
                            .map(Enum::name)
                            .collect(Collectors.toList());
                        fieldInfo.put("operators", operators);
                    }
                    
                    // Add sortable
                    fieldInfo.put("sortable", searchableField.sortable());
                    
                    // Add entityField if specified
                    if (!searchableField.entityField().isEmpty()) {
                        fieldInfo.put("entityField", searchableField.entityField());
                    }
                    
                    searchableFields.put(field.getName(), fieldInfo);
                    log.debug("Added searchable field info for {}: {}", field.getName(), fieldInfo);
                }
            }
            
            if (!searchableFields.isEmpty()) {
                schema.addExtension("x-searchable-fields", searchableFields);
                log.info("Added x-searchable-fields extension to {} with {} fields", 
                    schemaName, searchableFields.size());
            }
        } else {
            log.warn("SearchDTO class not found for: {}", searchDtoName);
        }
    }
} 