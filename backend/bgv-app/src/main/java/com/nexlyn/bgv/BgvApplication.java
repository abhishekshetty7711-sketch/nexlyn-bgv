package com.nexlyn.bgv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Nexlyn BGV Platform")
@SpringBootApplication
public class BgvApplication {

    public static void main(String[] args) {
        SpringApplication.run(BgvApplication.class, args);
    }
}
