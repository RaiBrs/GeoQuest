package com.raibrs.geoquest.config;

import com.raibrs.geoquest.service.PostgresRoundStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.*;
import org.springframework.session.web.http.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SharedStateConfigTest {
    @Test void sharedStorageIsDisabledByDefault() {
        new WebApplicationContextRunner().withUserConfiguration(SharedStateConfig.class).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(PostgresRoundStorage.class);
            assertThat(context).doesNotHaveBean("springSessionRepositoryFilter");
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql://.*")
    void enablingSharedStateCreatesTheSessionFilterAndPreservesSecureHandshakeCookies() throws Exception {
        String url = System.getenv("TEST_DATABASE_URL");
        String user = System.getenv().getOrDefault("TEST_DATABASE_USER", "geoquest_test");
        String password = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "test-only-password");
        try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
            assertThat(db.getCatalog()).isEqualTo("geoquest_test");
            for (String migration : List.of("001_accounts", "002_oauth_profile", "003_shared_rounds", "004_shared_oauth_sessions"))
                sql.execute(Files.readString(Path.of("database/" + migration + ".sql")));
        }
        new WebApplicationContextRunner().withUserConfiguration(SharedStateConfig.class).withPropertyValues(
                "SHARED_STATE_ENABLED=true", "APP_URL=https://geoquest.example.test", "JDBC_DATABASE_URL=" + url,
                "DATABASE_USER=" + user, "DATABASE_PASSWORD=" + password).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PostgresRoundStorage.class);
            assertThat(context).hasBean("springSessionRepositoryFilter");
            var response = new MockHttpServletResponse();
            context.getBean(DefaultCookieSerializer.class).writeCookieValue(
                    new CookieSerializer.CookieValue(new MockHttpServletRequest(), response, "test-session"));
            assertThat(response.getHeader("Set-Cookie")).startsWith("JSESSIONID=")
                    .contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/");
        });
    }
}
