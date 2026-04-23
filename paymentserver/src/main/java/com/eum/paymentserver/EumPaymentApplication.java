package com.eum.paymentserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EumPaymentApplication {

	public static void main(String[] args) {
		SpringApplication.run(EumPaymentApplication.class, args);
	}

}
