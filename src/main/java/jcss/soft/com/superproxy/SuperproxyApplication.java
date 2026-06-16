package jcss.soft.com.superproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class SuperproxyApplication {

	public static void main(String[] args) {
		SpringApplication.run(SuperproxyApplication.class, args);
	}

}
