package com.webapi;

import org.springframework.boot.SpringApplication;

public class TestWebapiApplication {

	public static void main(String[] args) {
		SpringApplication.from(WebapiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
