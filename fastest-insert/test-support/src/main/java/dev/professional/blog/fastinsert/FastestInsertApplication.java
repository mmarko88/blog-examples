package dev.professional.blog.fastinsert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "dev.professional.blog.fastinsert")
@EntityScan("dev.professional.blog.fastinsert")
@EnableJpaRepositories("dev.professional.blog.fastinsert")
public class FastestInsertApplication {
    public static void main(String[] args) {
        SpringApplication.run(FastestInsertApplication.class, args);
    }
}
