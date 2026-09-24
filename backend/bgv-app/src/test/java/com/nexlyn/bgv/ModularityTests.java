package com.nexlyn.bgv;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(BgvApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }
}
