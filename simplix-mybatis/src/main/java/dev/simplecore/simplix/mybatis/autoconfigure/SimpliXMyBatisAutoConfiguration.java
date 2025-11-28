package dev.simplecore.simplix.mybatis.autoconfigure;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import dev.simplecore.simplix.mybatis.properties.SimpliXMyBatisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@AutoConfiguration(after = MybatisAutoConfiguration.class)
@ConditionalOnClass({ SqlSessionTemplate.class, SqlSessionFactoryBean.class })
@ConditionalOnProperty(prefix = "mybatis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SimpliXMyBatisProperties.class)
@EnableTransactionManagement
@MapperScan(basePackages = "${mybatis.mapper-locations:dev.simplecore.simplix.**.mapper}")
public class SimpliXMyBatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SqlSessionFactory sqlSessionFactory(
            DataSourceProperties dataSourceProperties,
            SimpliXMyBatisProperties mybatisProperties) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSourceProperties.initializeDataSourceBuilder().build());

        sessionFactory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources(mybatisProperties.getMapperLocations()));

        if (mybatisProperties.getTypeAliasesPackage() != null) {
            sessionFactory.setTypeAliasesPackage(mybatisProperties.getTypeAliasesPackage());
        }

        if (mybatisProperties.getConfigLocation() != null) {
            sessionFactory.setConfigLocation(
                    new PathMatchingResourcePatternResolver().getResource(mybatisProperties.getConfigLocation()));
        }

        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCallSettersOnNulls(true);
        sessionFactory.setConfiguration(configuration);

        return sessionFactory.getObject();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
} 