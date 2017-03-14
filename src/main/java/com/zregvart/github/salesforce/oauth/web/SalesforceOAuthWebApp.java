package com.zregvart.github.salesforce.oauth.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@SpringBootApplication
public class SalesforceOAuthWebApp extends WebMvcConfigurerAdapter {

    public static void main(final String[] args) {
        System.setProperty("spring.cloud.kubernetes.secrets.paths", "/var/run/secrets/salesforce");
        SpringApplication.run(SalesforceOAuthWebApp.class, args);
    }

    @Override
    public void addViewControllers(final ViewControllerRegistry registry) {
        registry.addViewController("/success").setViewName("success");
    }
}
