package org.example.support;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class RedisTestContainer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS_CONTAINER.start();
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "spring.data.redis.host=" + REDIS_CONTAINER.getHost(),
                "spring.data.redis.port=" + REDIS_CONTAINER.getMappedPort(6379)
        ).applyTo(context.getEnvironment());
    }
}