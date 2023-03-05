/*
 * #%L
 * MariaDB4j
 * %%
 * Copyright (C) 2012 - 2018 Yuexiang Gao
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ch.vorburger.mariadb4j.springboot.autoconfigure;


import ch.vorburger.mariadb4j.springframework.MariaDB4jSpringService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
//CHECKSTYLE:OFF
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.sql.init.SqlDataSourceScriptDatabaseInitializer;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
//CHECKSTYLE:ON
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SqlInitializationProperties.class)
public class MariaDB4jSpringConfiguration implements PriorityOrdered, BeanPostProcessor, ApplicationContextAware {
    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(MariaDB4jSpringConfiguration.class);

    private static final int ORDER = Ordered.LOWEST_PRECEDENCE - 1;

    public MariaDB4jSpringConfiguration() {
        super();
    }

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private boolean mariaDB4jNotInited = true;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (mariaDB4jNotInited && (!(bean instanceof BeanPostProcessor))
            && (!bean.getClass().getName().startsWith("org.springframework.boot"))
        ) {
            String enabled = applicationContext.getEnvironment().getProperty("mariaDB4j.enabled");
            if (true || !"false".equals(enabled)) {
                applicationContext.getBeanProvider(MariaDB4jSpringService.class).getIfAvailable();
            }
            mariaDB4jNotInited = false;
        }
        return bean;
    }

    private boolean dbNotInited = true;

    private boolean dataSourceOk = false;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (dataSourceOk && dbNotInited) {
            dbNotInited = false;
            Class[] clsList = new Class[] { DataSourceScriptDatabaseInitializer.class};
            for (Class cls : clsList) {
                Object vo = applicationContext.getBeanProvider(cls).getIfAvailable();
                System.out.println("vo:" + vo);
            }
        }
        if (bean instanceof DataSource) { //"dynamicDataSource".equals(beanName)
            dataSourceOk = true;
            this.dataSource = (DataSource) bean;
        }
        return bean;
    }

    private DataSource dataSource;

    @ConditionalOnMissingBean()
    @Bean
    SqlDataSourceScriptDatabaseInitializer dataSourceScriptDatabaseInitializer(
            @Value("${spring.sql.init.preConditionSql:}") final String preCheckSql,
            SqlInitializationProperties properties) {
        return new SqlDataSourceScriptDatabaseInitializer(dataSource, properties) {

            @Override
            public boolean initializeDatabase() {
                if (dataSource == null) {
                    return false;
                }
                if (StringUtils.hasText(preCheckSql)) {
                    try (Connection conn = dataSource.getConnection()) {
                        Statement statement = conn.createStatement();
                        try (ResultSet rs = statement.executeQuery(preCheckSql)) {
                            if (rs.next()) {
                                logger.warn("preConditionSql has result, ignore init sql!");
                                return false;
                            }
                            int colCount = rs.getMetaData().getColumnCount();
                            if (colCount == 1 && Boolean.FALSE.equals(rs.getObject(1))) {
                                logger.warn("preConditionSql has result, ignore init sql!");
                                return false;
                            }
                        }
                    } catch (SQLException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                return super.initializeDatabase();
            }
        };
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean()
    @ConditionalOnProperty(prefix = "mariaDB4j", name = "enabled", havingValue = "true", matchIfMissing = false)
    public MariaDB4jSpringService mariaDB4j() {
        mariaDB4jNotInited = false;
        MariaDB4jSpringService service = new MariaDB4jSpringService();
        return service;
    }
}
