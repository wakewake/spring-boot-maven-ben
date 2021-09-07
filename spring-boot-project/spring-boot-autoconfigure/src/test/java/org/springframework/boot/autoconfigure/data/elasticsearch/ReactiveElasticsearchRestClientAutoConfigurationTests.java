/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.data.elasticsearch;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.index.get.GetResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testsupport.testcontainers.DockerImageNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.reactive.ReactiveElasticsearchClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ReactiveElasticsearchRestClientAutoConfiguration}.
 *
 * @author Brian Clozel
 */
@Testcontainers(disabledWithoutDocker = true)
class ReactiveElasticsearchRestClientAutoConfigurationTests {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(DockerImageNames.elasticsearch())
            .withStartupAttempts(5).withStartupTimeout(Duration.ofMinutes(10));

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactiveElasticsearchRestClientAutoConfiguration.class));

    @Test
    void configureShouldCreateDefaultBeans() {
        this.contextRunner.run((context) -> assertThat(context).hasSingleBean(ClientConfiguration.class)
                .hasSingleBean(ReactiveElasticsearchClient.class));
    }

    @Test
    void configureWhenCustomClientShouldBackOff() {
        this.contextRunner.withUserConfiguration(CustomClientConfiguration.class).run((context) -> assertThat(context)
                .hasSingleBean(ReactiveElasticsearchClient.class).hasBean("customClient"));
    }

    @Test
    void configureWhenCustomClientConfig() {
        this.contextRunner.withUserConfiguration(CustomClientConfigConfiguration.class)
                .run((context) -> assertThat(context).hasSingleBean(ReactiveElasticsearchClient.class)
                        .hasSingleBean(ClientConfiguration.class).hasBean("customClientConfiguration"));
    }

    @Test
    void restClientCanQueryElasticsearchNode() {
        this.contextRunner.withPropertyValues(
                "spring.data.elasticsearch.client.reactive.endpoints=" + elasticsearch.getHost() + ":"
                        + elasticsearch.getFirstMappedPort(),
                "spring.data.elasticsearch.client.reactive.connection-timeout=120s",
                "spring.data.elasticsearch.client.reactive.socket-timeout=120s").run((context) -> {
            ReactiveElasticsearchClient client = context.getBean(ReactiveElasticsearchClient.class);
            Map<String, String> source = new HashMap<>();
            source.put("a", "alpha");
            source.put("b", "bravo");
            IndexRequest indexRequest = new IndexRequest("foo").id("1").source(source);
            GetRequest getRequest = new GetRequest("foo").id("1");
            GetResult getResult = client.index(indexRequest).then(client.get(getRequest)).block();
            assertThat(getResult).isNotNull();
            assertThat(getResult.isExists()).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomClientConfiguration {

        @Bean
        ReactiveElasticsearchClient customClient() {
            return mock(ReactiveElasticsearchClient.class);
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class CustomClientConfigConfiguration {

        @Bean
        ClientConfiguration customClientConfiguration() {
            return ClientConfiguration.localhost();
        }

    }

}
