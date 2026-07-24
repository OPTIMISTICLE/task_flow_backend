package com.example.taskmanagement.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class GmailRestClientConfiguration {

    @Bean("gmailOAuthClient")
    RestClient gmailOAuthClient() {
        return RestClient.builder()
                .baseUrl("https://oauth2.googleapis.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "TaskFlow/0.1")
                .build();
    }

    @Bean("gmailApiClient")
    RestClient gmailApiClient() {
        return RestClient.builder()
                .baseUrl("https://gmail.googleapis.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "TaskFlow/0.1")
                .build();
    }
}
