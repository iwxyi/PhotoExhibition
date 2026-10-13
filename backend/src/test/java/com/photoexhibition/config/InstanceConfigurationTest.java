package com.photoexhibition.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InstanceConfigurationTest {
    private StandardEnvironment environment(Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("instance", variables));
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        environment.getPropertySources().addLast(new PropertiesPropertySource("application", yaml.getObject()));
        return environment;
    }

    @Test void preservesDefaults() {
        StandardEnvironment env = environment(Collections.emptyMap());
        assertEquals("6060", env.getProperty("server.port"));
        assertTrue(env.getProperty("spring.datasource.url").startsWith("jdbc:mysql://localhost:3306/photo_exhibition?"));
        assertEquals("root", env.getProperty("spring.datasource.username"));
    }

    @Test void allowsIndependentInstancesAndFullSpringOverrides() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("SERVER_PORT", "6061");
        variables.put("PHOTO_DB_HOST", "127.0.0.1");
        variables.put("PHOTO_DB_PORT", "3307");
        variables.put("PHOTO_DB_NAME", "photo2");
        variables.put("PHOTO_DB_USER", "photo_user_2");
        variables.put("PHOTO_DB_PASSWORD", "test-only");
        StandardEnvironment env = environment(variables);
        assertEquals("6061", env.getProperty("server.port"));
        assertTrue(env.getProperty("spring.datasource.url").startsWith("jdbc:mysql://127.0.0.1:3307/photo2?"));
        assertEquals("photo_user_2", env.getProperty("spring.datasource.username"));
        assertEquals("test-only", env.getProperty("spring.datasource.password"));
        variables.put("SPRING_DATASOURCE_URL", "jdbc:mysql://db:3306/photo_custom");
        assertEquals("jdbc:mysql://db:3306/photo_custom", env.getProperty("spring.datasource.url"));
    }
}
