/**
 * Shared building blocks (errors, crypto, masking, validation, logging, enums) used by every
 * module. It is an OPEN module: all of its packages may be used by other modules, but it must
 * never contain business logic or entities (CLAUDE.md {@literal §4.2} rule 8).
 */
@ApplicationModule(displayName = "Common", type = ApplicationModule.Type.OPEN)
package com.nexlyn.bgv.common;

import org.springframework.modulith.ApplicationModule;
