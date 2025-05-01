package dev.simplecore.simplix.demo.config;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
@MapperScan(basePackages = "dev.simplecore.simplix.demo.domain.**.**.mapper")
public class MyBatisConfiguration {
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