package com.example.FirstClubApp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the FirstClub membership service and enables scheduled subscription expiry processing.
 */
@SpringBootApplication
@EnableScheduling
public class FirstClubAppApplication {

	/**
	 * Launches the Spring Boot application.
	 *
	 * @param args command-line arguments supplied by the runtime; defaults to an empty array
	 * @return no return value
	 * @implNote Used by the Java runtime when the application process starts.
	 */
	public static void main(String[] args) {
		SpringApplication.run(FirstClubAppApplication.class, args);
	}

}
