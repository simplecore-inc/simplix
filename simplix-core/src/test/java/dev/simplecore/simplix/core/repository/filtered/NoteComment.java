package dev.simplecore.simplix.core.repository.filtered;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An unfiltered entity holding an eager to-one association to a filtered one. */
@Entity
@Table(name = "note_comments")
@Getter
@Setter
@NoArgsConstructor
public class NoteComment {

    @Id
    @Column(name = "comment_id")
    private String commentId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "note_id")
    private FilteredNote note;

    public NoteComment(String commentId, FilteredNote note) {
        this.commentId = commentId;
        this.note = note;
    }
}
