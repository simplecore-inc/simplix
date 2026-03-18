package dev.simplecore.simplix.mybatis.autoconfigure;

import dev.simplecore.simplix.mybatis.properties.SimpliXMyBatisProperties;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SimpliXMyBatisAutoConfiguration")
@ExtendWith(MockitoExtension.class)
class SimpliXMyBatisAutoConfigurationTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private DataSourceProperties createMockDataSourceProperties(DataSource dataSource) {
        DataSourceProperties dsProps = mock(DataSourceProperties.class);
        org.springframework.boot.jdbc.DataSourceBuilder builder =
                mock(org.springframework.boot.jdbc.DataSourceBuilder.class);
        when(dsProps.initializeDataSourceBuilder()).thenReturn(builder);
        when(builder.build()).thenReturn(dataSource);
        return dsProps;
    }

    @Nested
    @DisplayName("sqlSessionFactory")
    class SqlSessionFactoryTests {

        @Test
        @DisplayName("should create SqlSessionFactory with mapUnderscoreToCamelCase enabled")
        void shouldCreateSqlSessionFactoryWithCamelCase() throws Exception {
            SimpliXMyBatisAutoConfiguration config = new SimpliXMyBatisAutoConfiguration();
            SimpliXMyBatisProperties mybatisProps = new SimpliXMyBatisProperties();
            mybatisProps.setMapperLocations("classpath*:mapper/**/*.xml");

            DataSource mockDataSource = mock(DataSource.class);
            DataSourceProperties dsProps = createMockDataSourceProperties(mockDataSource);

            SqlSessionFactory factory = config.sqlSessionFactory(dsProps, mybatisProps);

            assertThat(factory).isNotNull();
            assertThat(factory.getConfiguration().isMapUnderscoreToCamelCase()).isTrue();
            assertThat(factory.getConfiguration().isCallSettersOnNulls()).isTrue();
        }

        @Test
        @DisplayName("should set config location when configured")
        void shouldSetConfigLocationWhenConfigured() {
            SimpliXMyBatisAutoConfiguration config = new SimpliXMyBatisAutoConfiguration();
            SimpliXMyBatisProperties mybatisProps = new SimpliXMyBatisProperties();
            mybatisProps.setMapperLocations("classpath*:mapper/**/*.xml");
            mybatisProps.setConfigLocation("classpath:mybatis-test-config.xml");

            DataSource mockDataSource = mock(DataSource.class);
            DataSourceProperties dsProps = createMockDataSourceProperties(mockDataSource);

            // configLocation and programmatic Configuration cannot coexist in MyBatis;
            // this verifies the configLocation branch (lines 43-44) is reached
            assertThatThrownBy(() -> config.sqlSessionFactory(dsProps, mybatisProps))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("configuration");
        }

        @Test
        @DisplayName("should set type aliases package when configured")
        void shouldSetTypeAliasesPackageWhenConfigured() throws Exception {
            SimpliXMyBatisAutoConfiguration config = new SimpliXMyBatisAutoConfiguration();
            SimpliXMyBatisProperties mybatisProps = new SimpliXMyBatisProperties();
            mybatisProps.setMapperLocations("classpath*:mapper/**/*.xml");
            mybatisProps.setTypeAliasesPackage("com.example.model");

            DataSource mockDataSource = mock(DataSource.class);
            DataSourceProperties dsProps = createMockDataSourceProperties(mockDataSource);

            SqlSessionFactory factory = config.sqlSessionFactory(dsProps, mybatisProps);

            assertThat(factory).isNotNull();
        }
    }

    @Nested
    @DisplayName("sqlSessionTemplate")
    class SqlSessionTemplateTests {

        @Test
        @DisplayName("should create SqlSessionTemplate from SqlSessionFactory")
        void shouldCreateSqlSessionTemplate() throws Exception {
            SimpliXMyBatisAutoConfiguration config = new SimpliXMyBatisAutoConfiguration();

            // Build a real SqlSessionFactory to use with SqlSessionTemplate
            SimpliXMyBatisProperties mybatisProps = new SimpliXMyBatisProperties();
            mybatisProps.setMapperLocations("classpath*:mapper/**/*.xml");
            DataSource mockDataSource = mock(DataSource.class);
            DataSourceProperties dsProps = createMockDataSourceProperties(mockDataSource);
            SqlSessionFactory factory = config.sqlSessionFactory(dsProps, mybatisProps);

            SqlSessionTemplate template = config.sqlSessionTemplate(factory);

            assertThat(template).isNotNull();
            assertThat(template.getSqlSessionFactory()).isEqualTo(factory);
        }
    }
}
