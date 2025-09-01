package dev.simplecore.simplix.core.tree.repository;

import dev.simplecore.simplix.core.tree.annotation.LookupColumn;
import dev.simplecore.simplix.core.tree.base.TreeQueries;
import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base repository implementation for entities with tree structure.
 * This implementation provides:
 * - Hierarchical data querying using database-specific recursive queries
 * - Support for multiple databases (PostgreSQL, MySQL, MariaDB, Oracle, SQL Server, H2)
 * - Automatic query optimization based on database type
 * - Dynamic lookup capabilities using additional columns
 * - Built-in sorting functionality
 *
 * @param <T> The entity type that implements TreeEntity
 * @param <ID> The type of the entity's identifier
 */
public class SimpliXTreeRepositoryImpl<T extends TreeEntity<T, ID>, ID>
        extends SimpleJpaRepository<T, ID> 
        implements SimpliXTreeRepository<T, ID> {

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final TreeQueries queries;

    /**
     * Creates a new TreeRepositoryImpl.
     *
     * @param entityInformation JPA entity metadata
     * @param entityManager JPA EntityManager for database operations
     * @param jdbcTemplate Spring's JdbcTemplate for native SQL operations
     * @param tableName Name of the database table
     * @param idColumn Name of the primary key column
     * @param parentIdColumn Name of the parent reference column
     * @param sortOrderColumn Name of the column used for sorting (optional)
     * @param lookupColumns Array of additional columns that can be used for searching
     */
    public SimpliXTreeRepositoryImpl(
            JpaEntityInformation<T, ID> entityInformation,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate,
            String tableName,
            String idColumn,
            String parentIdColumn,
            String sortOrderColumn,
            LookupColumn[] lookupColumns) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.queries = new TreeQueries(tableName, idColumn, parentIdColumn, sortOrderColumn, lookupColumns);
    }

    /**
     * Retrieves the complete hierarchy of all items.
     * Uses database-specific recursive queries when supported, falls back to in-memory hierarchy building otherwise.
     *
     * @return List of all items with their hierarchical relationships
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<T> findCompleteHierarchy() {
        String dbType = getDatabaseType();
        List<T> items;
        
        try {
            String query = queries.getHierarchyQuery(dbType);
            if (query != null) {
                items = entityManager.createNativeQuery(query, getDomainClass())
                    .getResultList();
                return buildHierarchy(items);
            } else {
                items = findAll();
                return buildHierarchy(items);
            }
        } catch (Exception e) {
            items = findAll();
            return buildHierarchy(items);
        }
    }

    /**
     * Finds an item and all its descendants.
     * Uses database-specific recursive queries when supported.
     *
     * @param itemId ID of the root item to start the search from
     * @return List containing the item and all its descendants
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<T> findItemWithAllDescendants(ID itemId) {
        String dbType = getDatabaseType();
        List<T> items;
        
        try {
            String query = queries.getDescendantsQuery(dbType);
            if (query != null) {
                items = entityManager.createNativeQuery(query, getDomainClass())
                    .setParameter(1, itemId)
                    .getResultList();
                
                // In some databases like H2, WITH RECURSIVE may not work correctly
                if (items.isEmpty()) {
                    items = getAllDescendantsGeneric(itemId);
                }
                
                return items;
            }
            items = getAllDescendantsGeneric(itemId);
            return items;
        } catch (Exception e) {
            items = getAllDescendantsGeneric(itemId);
            return items;
        }
    }

    /**
     * Fallback method to find all descendants using a generic approach.
     * Used when database-specific recursive queries are not available.
     *
     * @param itemId ID of the root item
     * @return List of all descendants
     */
    private List<T> getAllDescendantsGeneric(ID itemId) {
        List<T> allItems = findAll();
        List<T> result = new ArrayList<>();
        
        for (T item : allItems) {
            if (item.getId().equals(itemId)) {
                result.add(item);
                findDescendantsRecursive(allItems, item.getId(), result);
                break;
            }
        }
        
        return result;
    }

    /**
     * Recursive helper method to find descendants.
     * Used by getAllDescendantsGeneric for in-memory hierarchy traversal.
     *
     * @param allItems Complete list of items
     * @param parentId ID of the parent item
     * @param result List to collect descendants
     */
    private void findDescendantsRecursive(List<T> allItems, ID parentId, List<T> result) {
        for (T item : allItems) {
            if (parentId.equals(item.getParentId())) {
                result.add(item);
                findDescendantsRecursive(allItems, item.getId(), result);
            }
        }
    }

    /**
     * Builds a hierarchical structure from a flat list of items.
     * Used when database-specific hierarchical queries are not available.
     *
     * @param allItems Flat list of all items
     * @return List of root items with their hierarchical structure
     */
    @Override
    public List<T> buildHierarchy(List<T> allItems) {
        Map<ID, List<T>> parentChildMap = new HashMap<>();
        List<T> rootItems = new ArrayList<>();
        
        for (T item : allItems) {
            ID parentId = item.getParentId();
            
            // Treat empty string as null for root items
            if (parentId == null || (parentId instanceof String && ((String) parentId).trim().isEmpty())) {
                rootItems.add(item);
            } else {
                parentChildMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(item);
            }
        }
        
        for (T item : allItems) {
            List<T> children = parentChildMap.get(item.getId());
            item.setChildren(children != null ? children : new ArrayList<>());
        }
        
        return rootItems;
    }

    /**
     * Finds all root items (items with no parent).
     *
     * @return List of root items
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<T> findRootItems() {
        String dbType = getDatabaseType();
        String query = queries.getRootItemsQuery(dbType);
        return entityManager.createNativeQuery(query, getDomainClass()).getResultList();
    }
    
    /**
     * Finds direct children of a specific item.
     *
     * @param parentId ID of the parent item
     * @return List of direct children
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<T> findDirectChildren(ID parentId) {
        String query = queries.getDirectChildrenQuery();
        return entityManager.createNativeQuery(query, getDomainClass())
            .setParameter(1, parentId)
            .getResultList();
    }

    /**
     * Finds items based on lookup column values.
     *
     * @param parameters Map of column names and their values to search for
     * @return List of matching items
     */
    @SuppressWarnings("unchecked")
    public List<T> findByLookup(Map<String, String> parameters) {
        String dbType = getDatabaseType();
        String query = queries.getLookupQuery(parameters, dbType);
        return entityManager.createNativeQuery(query, getDomainClass()).getResultList();
    }

    /**
     * Determines the type of database being used.
     * This information is used to generate appropriate database-specific queries.
     *
     * @return String identifying the database type
     */
    private String getDatabaseType() {
        try {
            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource != null) {
                try (Connection connection = dataSource.getConnection()) {
                    DatabaseMetaData metaData = connection.getMetaData();
                    String dbProductName = metaData.getDatabaseProductName().toLowerCase();
                    
                    if (dbProductName.contains("postgresql")) {
                        return "postgresql";
                    } else if (dbProductName.contains("mysql")) {
                        return "mysql";
                    } else if (dbProductName.contains("mariadb")) {
                        return "mariadb";
                    } else if (dbProductName.contains("oracle")) {
                        return "oracle";
                    } else if (dbProductName.contains("microsoft") && dbProductName.contains("sql server")) {
                        return "mssql";
                    } else if (dbProductName.contains("h2")) {
                        return "h2";
                    }
                }
            }
        } catch (SQLException e) {
            // Log error and continue with default handling
        }
        
        return "unknown";
    }

} 