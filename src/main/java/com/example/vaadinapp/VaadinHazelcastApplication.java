package com.example.vaadinapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;

@SpringBootApplication
@EnableHazelcastHttpSession
public class VaadinHazelcastApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaadinHazelcastApplication.class, args);
    }
}

