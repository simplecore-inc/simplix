package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.core.annotation.References;
import dev.simplecore.simplix.web.meta.model.ReferenceMeta;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A reference that names its target by the served entity's simple name rather than by class. */
class EntityReferencesByNameTest {

    static class Plan {
        @Id String planId;
        String planCode;
    }

    static class Account {
        @Id String accountId;
        @References(entity = "Plan") String currentPlanId;
        @References(entity = "Plan", field = "planCode") String planCode;
        @References(entity = "Plan") Set<String> pastPlanIds;
        @References(Plan.class) String classPlanId;
        @References(value = Plan.class, entity = "Plan") String bothPlanId;
        @References String neitherPlanId;
        @References(entity = "Invoice") String invoiceId;
        @References(entity = "Target") String targetId;
    }

    static class First {
        static class Target {
            @Id String targetId;
        }
    }

    static class Second {
        static class Target {
            @Id String targetId;
        }
    }

    private static final EntityReferences.Directory SERVED =
            new EntityReferences.Directory(List.of(Plan.class, Account.class, First.Target.class, Second.Target.class));

    @Test
    void aNameResolvesToTheServedEntityAndItsIdentifier() {
        assertThat(EntityReferences.resolve(Account.class, "currentPlanId", SERVED))
                .isEqualTo(new ReferenceMeta("Plan", "planId", false));
    }

    @Test
    void aNameKeepsTheFieldTheValueMatches() {
        assertThat(EntityReferences.resolve(Account.class, "planCode", SERVED))
                .isEqualTo(new ReferenceMeta("Plan", "planCode", false));
    }

    @Test
    void aCollectionNamedByNameIsMany() {
        assertThat(EntityReferences.resolve(Account.class, "pastPlanIds", SERVED))
                .isEqualTo(new ReferenceMeta("Plan", "planId", true));
    }

    @Test
    void aClassResolvesAsBeforeWithOrWithoutADirectory() {
        ReferenceMeta expected = new ReferenceMeta("Plan", "planId", false);
        assertThat(EntityReferences.resolve(Account.class, "classPlanId", SERVED)).isEqualTo(expected);
        assertThat(EntityReferences.resolve(Account.class, "classPlanId")).isEqualTo(expected);
    }

    @Test
    void bothFormsAreRefusedNamingTheField() {
        assertThatThrownBy(() -> EntityReferences.resolve(Account.class, "bothPlanId", SERVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Account.class.getName() + ".bothPlanId")
                .hasMessageContaining("both a class")
                .hasMessageContaining("set exactly one");
    }

    @Test
    void neitherFormIsRefusedNamingTheField() {
        assertThatThrownBy(() -> EntityReferences.resolve(Account.class, "neitherPlanId", SERVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Account.class.getName() + ".neitherPlanId")
                .hasMessageContaining("neither a class nor an entity name");
    }

    @Test
    void anUnknownNameIsRefusedRatherThanDropped() {
        assertThatThrownBy(() -> EntityReferences.resolve(Account.class, "invoiceId", SERVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Account.class.getName() + ".invoiceId")
                .hasMessageContaining("\"Invoice\"")
                .hasMessageContaining("serves no entity");
    }

    @Test
    void aNameWithoutADirectoryIsUnknown() {
        assertThatThrownBy(() -> EntityReferences.resolve(Account.class, "currentPlanId"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serves no entity");
    }

    @Test
    void anAmbiguousNameIsRefusedNamingBothEntities() {
        assertThatThrownBy(() -> EntityReferences.resolve(Account.class, "targetId", SERVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Account.class.getName() + ".targetId")
                .hasMessageContaining(First.Target.class.getName())
                .hasMessageContaining(Second.Target.class.getName());
    }
}
