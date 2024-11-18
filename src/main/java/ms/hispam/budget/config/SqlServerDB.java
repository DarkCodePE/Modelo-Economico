package ms.hispam.budget.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(entityManagerFactoryRef ="sqlServerEntityManagerFactory",
        transactionManagerRef = "sqlServerTransactionManager",basePackages = {
        "ms.hispam.budget.repository.sqlserver"
})
public class SqlServerDB {

    @Value("${sqlserver.jpa.database-platform}")
    private String dialect;

    @Bean(name = "sqlServerDataSource")
    @ConfigurationProperties(prefix = "sqlserver.datasource")
    public DataSource dataSource(){
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "sqlServerEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean
    localContainerEntityManagerFactoryBean(@Qualifier("sqlServerDataSource") DataSource dataSource){
        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPackagesToScan("ms.hispam.budget.entity.sqlserver");
        HibernateJpaVendorAdapter vendor = new HibernateJpaVendorAdapter();
        factoryBean.setJpaVendorAdapter(vendor);
        Map<String,Object> properties= new HashMap<>();
        properties.put("hibernate.dialect",dialect);
        factoryBean.setJpaPropertyMap(properties);
        factoryBean.setPersistenceUnitName("sqlServerPU"); // Asegúrate de establecer el nombre del persistence unit
        return factoryBean;
    }

    @Bean(name ="sqlServerTransactionManager" )
    public PlatformTransactionManager platformTransactionManager(
            @Qualifier("sqlServerEntityManagerFactory") EntityManagerFactory entityManagerFactory){
        return new JpaTransactionManager(entityManagerFactory);

    }
    @Bean(name = "sqlServerJdbcTemplate")
    public JdbcTemplate sqlServerJdbcTemplate(@Qualifier("sqlServerDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
