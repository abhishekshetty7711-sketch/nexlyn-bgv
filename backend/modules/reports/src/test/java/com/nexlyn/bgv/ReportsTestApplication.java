package com.nexlyn.bgv;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only application for the reports module. It sits in the root package, like the real application,
 * so entity and repository scanning finds the modules this one depends on (auth, cases, documents).
 */
@SpringBootApplication
public class ReportsTestApplication {
}
