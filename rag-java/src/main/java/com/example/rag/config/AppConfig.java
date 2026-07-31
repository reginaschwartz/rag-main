package com.example.rag.config;

import com.example.rag.vectorstore.PgVectorConnection;
import com.zaxxer.hikari.HikariDataSource;
import java.net.http.HttpClient;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

    @Bean
    public PgVectorConnection pgVectorConnection(PgVectorProperties pgVector, PostgresProperties postgres) {
        return PgVectorConnection.resolve(pgVector.connection(), postgres);
    }

    @Bean(destroyMethod = "close")
    public DataSource dataSource(PgVectorConnection connection) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("rag-pgvector");
        dataSource.setJdbcUrl(connection.jdbcUrl());
        dataSource.setUsername(connection.username());
        dataSource.setPassword(connection.password());
        dataSource.setMaximumPoolSize(10);
        return dataSource;
    }

    @Bean
    public RestClient openAiRestClient(OpenAiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
