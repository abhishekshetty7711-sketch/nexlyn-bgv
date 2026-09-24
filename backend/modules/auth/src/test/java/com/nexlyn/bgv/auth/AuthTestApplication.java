package com.nexlyn.bgv.auth;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only application that boots just the auth module (plus bgv-common) so its integration
 * tests do not need the other modules or the full app.
 */
@SpringBootApplication(scanBasePackages = {"com.nexlyn.bgv.auth", "com.nexlyn.bgv.common"})
public class AuthTestApplication {
}
