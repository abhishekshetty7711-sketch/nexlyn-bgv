package com.nexlyn.bgv;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only application for the documents module. It sits in the root package, like the real
 * application, so entity and repository scanning finds the modules this one depends on (auth, cases).
 */
@SpringBootApplication
public class DocumentsTestApplication {
}
