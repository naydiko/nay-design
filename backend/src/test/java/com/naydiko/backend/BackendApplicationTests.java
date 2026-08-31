package com.naydiko.backend;

import com.naydiko.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Extends {@link AbstractIntegrationTest} (Testcontainers PostgreSQL) rather
 * than relying on an external, developer-machine-only database at
 * {@code localhost:5433} — this is the only thing that makes the plain
 * context-load smoke test portable to a clean CI runner.
 */
class BackendApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
