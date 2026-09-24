package com.nexlyn.bgv;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only application for the cases module. It sits in the root package, like the real
 * application, so entity and repository scanning finds the cases module and the auth module it
 * depends on (only modules on this module's classpath are scanned).
 */
@SpringBootApplication
public class CasesTestApplication {
}
