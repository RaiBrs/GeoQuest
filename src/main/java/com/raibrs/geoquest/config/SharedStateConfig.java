package com.raibrs.geoquest.config;

import com.raibrs.geoquest.service.PostgresRoundStorage;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;

/** Explicit opt-in: local and existing single-instance deployments keep their current behavior. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "SHARED_STATE_ENABLED", havingValue = "true")
@EnableJdbcHttpSession(tableName = "geoquest_http_session", maxInactiveIntervalInSeconds = 600)
public class SharedStateConfig {
    @Bean DataSource dataSource(Environment env) {
        var source = new PGSimpleDataSource();
        source.setURL(env.getRequiredProperty("JDBC_DATABASE_URL"));
        source.setUser(env.getRequiredProperty("DATABASE_USER"));
        source.setPassword(env.getRequiredProperty("DATABASE_PASSWORD"));
        source.setConnectTimeout(5);
        source.setSocketTimeout(10);
        source.setApplicationName("GeoQuest-shared-state");
        return source;
    }
    @Bean PlatformTransactionManager transactionManager(DataSource source) {
        return new DataSourceTransactionManager(source);
    }
    @Bean DefaultCookieSerializer cookieSerializer(Environment env) {
        var cookies = new DefaultCookieSerializer();
        cookies.setCookieName("JSESSIONID");
        cookies.setCookiePath("/");
        cookies.setUseHttpOnlyCookie(true);
        cookies.setSameSite("Lax");
        cookies.setUseSecureCookie(env.getProperty("APP_URL", "").startsWith("https://"));
        return cookies;
    }
    @Bean PostgresRoundStorage roundStorage(DataSource source) {
        var storage = new PostgresRoundStorage(source);
        storage.verifySchema();
        return storage;
    }
}
