package dev.example.orders;

import graphql.analysis.MaxQueryDepthInstrumentation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring Boot adds every Instrumentation bean to the GraphQL engine.
@Configuration
public class GraphQlLimits {

    @Bean
    public MaxQueryDepthInstrumentation maxDepth() {
        return new MaxQueryDepthInstrumentation(5);
    }
}
