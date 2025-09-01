package dev.simplecore.simplix.demo.config;

import org.h2.tools.Server;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.sql.SQLException;

@AutoConfiguration
@Profile({"dev", "prod"})  // Only enable in dev and prod profiles, not in test
public class H2ServerConfig {

    // JDBC URL: jdbc:h2:tcp://localhost:9092/mem:simplixdb
    @Bean(initMethod = "start", destroyMethod = "stop")
    public Server h2TcpServer() throws SQLException {
        return Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9092");
    }
} 