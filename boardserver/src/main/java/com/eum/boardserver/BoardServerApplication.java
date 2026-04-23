package com.eum.boardserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.eum")
public class BoardServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BoardServerApplication.class, args);
	}

}
