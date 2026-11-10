package io.github.oliverjuhasz.boardkeeper;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(BoardKeeperApplication.class);

    /** Fails when a module uses another module's internals, or when modules depend on each other in a cycle. */
    @Test
    void modulesRespectEachOthersBoundaries() {
        modules.verify();
    }

    /** Writes diagrams and a summary of the modules to target/spring-modulith-docs. */
    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
