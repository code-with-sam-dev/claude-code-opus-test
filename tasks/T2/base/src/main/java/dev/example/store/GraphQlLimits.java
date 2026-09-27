package dev.example.store;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Depth stops deep nesting. Complexity stops wide queries, such as the same
// field asked for fifty times under fifty aliases.
@Configuration
public class GraphQlLimits {

    @Bean
    public MaxQueryDepthInstrumentation maxDepth() {
        return new MaxQueryDepthInstrumentation(6);
    }

    @Bean
    public MaxQueryComplexityInstrumentation maxComplexity() {
        return new MaxQueryComplexityInstrumentation(100);
    }
}
