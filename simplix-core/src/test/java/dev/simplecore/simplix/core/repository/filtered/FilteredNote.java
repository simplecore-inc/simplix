package dev.simplecore.simplix.core.repository.filtered;

import dev.simplecore.simplix.core.entity.SoftDeletable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/** A soft-deleted entity whose session filter is declared on the entity itself. */
@Entity
@Table(name = "filtered_notes")
@FilterDef(name = FilteredNote.FILTER, parameters = @ParamDef(name = "isDeleted", type = Boolean.class))
@Filter(name = FilteredNote.FILTER, condition = "deleted = :isDeleted")
@Getter
@Setter
@NoArgsConstructor
public class FilteredNote implements SoftDeletable {

    public static final String FILTER = "filteredNoteDeleted";

    @Id
    @Column(name = "note_id")
    private String noteId;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    public FilteredNote(String noteId) {
        this.noteId = noteId;
    }

    @Override
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }
}
