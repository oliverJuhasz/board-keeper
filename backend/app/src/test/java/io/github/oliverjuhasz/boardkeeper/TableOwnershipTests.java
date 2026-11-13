package io.github.oliverjuhasz.boardkeeper;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Keeps every Spring Modulith module to its own database tables. The jOOQ classes for all
 * tables sit in one shared package, so without this check nothing would stop, say, the players
 * module from reading the accounts tables behind the accounts module's back. Spring Modulith
 * itself cannot see that kind of dependency.
 *
 * <p>When a migration adds a table, add it here under the module that owns it.
 */
class TableOwnershipTests {

    /** Each module's tables, by the name of the generated jOOQ class. */
    private static final Map<String, Set<String>> TABLES_OF = Map.of(
            "games", Set.of("Game"),
            "players", Set.of("Player"),
            "plays", Set.of("Play", "PlayResult"),
            "stats", Set.of("StatsPlay", "StatsResult"),
            "accounts", Set.of("Account", "AccountRole", "Role", "RolePermission", "Invite", "PasswordReset"));

    private static final String ROOT = "io.github.oliverjuhasz.boardkeeper";
    private static final String TABLES_PACKAGE = ROOT + ".jooq.tables";

    private final JavaClasses application = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void everyTableBelongsToAModule() {
        Set<String> generated = application.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(TABLES_PACKAGE))
                .map(TableOwnershipTests::tableOf)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> owned = TABLES_OF.values().stream().flatMap(Set::stream).collect(Collectors.toCollection(TreeSet::new));

        assertThat(owned).as("the tables listed in TABLES_OF").isEqualTo(generated);
    }

    @Test
    void aModuleUsesOnlyItsOwnTables() {
        TABLES_OF.forEach((module, ownTables) -> classes()
                .that()
                .resideInAPackage(ROOT + "." + module + "..")
                .should(useOnlyTheTables(ownTables))
                .because("the " + module + " module owns only " + new TreeSet<>(ownTables))
                .allowEmptyShould(true)
                .check(application));
    }

    /** Modules without tables of their own must not touch any table. */
    @Test
    void theOtherModulesUseNoTablesAtAll() {
        classes()
                .that()
                .resideInAPackage(ROOT + "..")
                .and()
                .resideOutsideOfPackage(ROOT + ".jooq..")
                .and(new com.tngtech.archunit.base.DescribedPredicate<JavaClass>("belong to a module without tables") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return TABLES_OF.keySet().stream()
                                .noneMatch(module -> javaClass.getPackageName().startsWith(ROOT + "." + module));
                    }
                })
                .should(useOnlyTheTables(Set.of()))
                .allowEmptyShould(true)
                .check(application);
    }

    private static ArchCondition<JavaClass> useOnlyTheTables(Set<String> ownTables) {
        return new ArchCondition<>("use only its own module's tables") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                // The table and record classes a class refers to, plus the tables it picks out
                // of the shared Tables class, such as Tables.GAME.
                Set<String> used = new TreeSet<>();
                javaClass.getDirectDependenciesFromSelf().stream()
                        .map(dependency -> dependency.getTargetClass())
                        .filter(target -> target.getPackageName().startsWith(TABLES_PACKAGE))
                        .forEach(target -> used.add(tableOf(target)));
                javaClass.getFieldAccessesFromSelf().stream()
                        .map(access -> access.getTarget().getRawType())
                        .filter(type -> type.getPackageName().startsWith(TABLES_PACKAGE))
                        .forEach(type -> used.add(tableOf(type)));

                used.removeAll(ownTables);
                if (!used.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass, javaClass.getName() + " uses tables of another module: " + used));
                }
            }
        };
    }

    /**
     * The table a generated class is about: "Game" for the table class Game, for its record
     * class GameRecord, and for Game.GamePath, the class jOOQ nests inside it for joins.
     */
    private static String tableOf(JavaClass generated) {
        String topLevel = generated.getName().substring(generated.getPackageName().length() + 1).split("\\$")[0];
        return topLevel.endsWith("Record") ? topLevel.substring(0, topLevel.length() - "Record".length()) : topLevel;
    }
}
