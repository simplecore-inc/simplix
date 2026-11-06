package dev.simplecore.simplix.core.repository.filtered;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/** A mapped superclass that declares the session filter for every entity extending it. */
@MappedSuperclass
@FilterDef(name = FilteredBase.FILTER, parameters = @ParamDef(name = "isDeleted", type = Boolean.class))
@Filter(name = FilteredBase.FILTER, condition = "deleted = :isDeleted")
@Getter
@Setter
public abstract class FilteredBase {

    public static final String FILTER = "filteredBaseDeleted";

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
}
