package com.example.FirstClubApp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the complete Spring application context can start with its configured dependencies.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class FirstClubAppApplicationTests {

	/**
	 * Loads the application context and succeeds when bean creation completes.
	 *
	 * @return no return value
	 * @implNote Used as an opt-in PostgreSQL startup integration check when
	 * {@code RUN_POSTGRES_INTEGRATION_TESTS=true}.
	 */
	@Test
	void contextLoads() {
	}

}
