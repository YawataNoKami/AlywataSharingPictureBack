package com.photoapp.config;

import org.springframework.context.annotation.Configuration;

/**
 * Placeholder for MongoDB-specific bean customizations.
 *
 * <p>Note: Spring Boot's auto-configuration ({@code GridFsAutoConfiguration})
 * already exposes a {@link org.springframework.data.mongodb.gridfs.GridFsTemplate}
 * bean out of the box as soon as {@code spring-boot-starter-data-mongodb} is
 * on the classpath and {@code spring.data.mongodb.uri} is set, so no manual
 * bean definition is required here. This class is kept as an extension point
 * for future Mongo-specific configuration (e.g. custom converters, indexes).
 */
@Configuration
public class MongoConfig {
}
