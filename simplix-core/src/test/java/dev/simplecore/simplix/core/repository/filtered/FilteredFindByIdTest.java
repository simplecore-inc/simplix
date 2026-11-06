package dev.simplecore.simplix.core.repository.filtered;

import dev.simplecore.simplix.core.tree.factory.SimpliXRepositoryFactoryBean;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A session filter declared on an entity hides a row from a read by id exactly as it hides it from
 * a query, on the H2 database of the test profile.
 */
@SpringBootTest(classes = FilteredFindByIdTest.Config.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("findById honours the entity's enabled session filters")
class FilteredFindByIdTest {

    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = FilteredNote.class)
    @EnableJpaRepositories(basePackageClasses = FilteredNote.class,
            repositoryFactoryBeanClass = SimpliXRepositoryFactoryBean.class)
    static class Config { }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired private FilteredNoteRepository notes;
    @Autowired private InheritedFilterItemRepository inherited;
    @Autowired private PlainItemRepository plain;
    @Autowired private NoteCommentRepository comments;

    @BeforeEach
    void setUp() {
        notes.save(new FilteredNote("active-note"));
        FilteredNote deletedNote = notes.save(new FilteredNote("deleted-note"));
        inherited.save(new InheritedFilterItem("active-item"));
        inherited.save(new InheritedFilterItem("deleted-item"));
        plain.save(new PlainItem("deleted-plain"));
        comments.save(new NoteComment("comment-on-deleted", deletedNote));
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE filtered_notes SET deleted = true WHERE note_id = 'deleted-note'")
                .executeUpdate();
        entityManager.createNativeQuery("UPDATE inherited_filter_items SET deleted = true WHERE item_id = 'deleted-item'")
                .executeUpdate();
        entityManager.createNativeQuery("UPDATE plain_items SET deleted = true").executeUpdate();
        entityManager.clear();
    }

    private void enable(String filter, boolean isDeleted) {
        entityManager.unwrap(Session.class).enableFilter(filter).setParameter("isDeleted", isDeleted);
    }

    @Test
    @DisplayName("a row the enabled filter excludes is not found by id, and existsById agrees")
    void excludedRowIsNotFound() {
        enable(FilteredNote.FILTER, false);

        assertThat(notes.findById("deleted-note")).isEmpty();
        assertThat(notes.existsById("deleted-note")).isFalse();
        assertThat(notes.findById("active-note")).isPresent();
    }

    @Test
    @DisplayName("the filter's own condition decides, so a deleted-only filter hides the active row")
    void filterConditionDecides() {
        enable(FilteredNote.FILTER, true);

        assertThat(notes.findById("active-note")).isEmpty();
        assertThat(notes.findById("deleted-note")).isPresent();
    }

    @Test
    @DisplayName("with the filter disabled a soft-deleted row is still readable by id")
    void disabledFilterReadsEverything() {
        assertThat(notes.findById("deleted-note")).isPresent();
    }

    @Test
    @DisplayName("a filter declared on a mapped superclass applies to its entities")
    void inheritedFilterApplies() {
        enable(FilteredBase.FILTER, false);

        assertThat(inherited.findById("deleted-item")).isEmpty();
        assertThat(inherited.findById("active-item")).isPresent();
    }

    @Test
    @DisplayName("an entity without a filter is untouched while other filters are enabled")
    void unfilteredEntityIsUntouched() {
        enable(FilteredNote.FILTER, false);
        enable(FilteredBase.FILTER, false);

        assertThat(plain.findById("deleted-plain")).isPresent();
    }

    @Test
    @DisplayName("an eager association to an excluded row still loads, by id and by query")
    void associationsToExcludedRowsStillLoad() {
        enable(FilteredNote.FILTER, false);

        assertThat(comments.findById("comment-on-deleted"))
                .hasValueSatisfying(c -> assertThat(c.getNote().getNoteId()).isEqualTo("deleted-note"));
        entityManager.clear();
        List<NoteComment> all = comments.findAll();
        assertThat(all).singleElement()
                .satisfies(c -> assertThat(c.getNote().getNoteId()).isEqualTo("deleted-note"));
    }
}
