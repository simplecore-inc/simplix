package dev.simplecore.simplix.demo.config;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import java.util.Properties;

@AutoConfiguration
@Order(15)
@MapperScan(basePackages = {
    "dev.simplecore.simplix.demo.domain.common.user.mapper",
    "dev.simplecore.simplix.demo.domain.*.mapper"
})
public class MyBatisConfiguration {
    
    @Bean
    public JdbcTemplate jdbcTemplate(DataSourceProperties dataSourceProperties) {
        return new JdbcTemplate(dataSourceProperties.initializeDataSourceBuilder().build());
    }
    
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        DatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.setProperty("SQL Server", "mssql");
        properties.setProperty("MySQL", "mysql");
        properties.setProperty("MariaDB", "mariadb");
        properties.setProperty("PostgreSQL", "postgresql");
        properties.setProperty("Oracle", "oracle");
        properties.setProperty("H2", "h2");
        databaseIdProvider.setProperties(properties);
        return databaseIdProvider;
    }
} 