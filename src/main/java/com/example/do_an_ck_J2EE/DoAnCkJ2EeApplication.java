package com.example.do_an_ck_J2EE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DoAnCkJ2EeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoAnCkJ2EeApplication.class, args);
    }
}