package com.eljaibo.pos;

import org.springframework.boot.SpringApplication;

public class TestJaiboPosApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(JaiboPosApiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
