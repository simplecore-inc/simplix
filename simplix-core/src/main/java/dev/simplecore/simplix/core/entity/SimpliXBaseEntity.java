package dev.simplecore.simplix.core.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class SimpliXBaseEntity<K> {

    /**
     * Returns the primary key of the entity.
     * This method must be implemented by all subclasses to return their @Id field.
     * 
     * @return The primary key of the entity
     */
    public abstract K getId();
    
    /**
     * Sets the primary key of the entity.
     * This method must be implemented by all subclasses to set their @Id field.
     * 
     * @param id The primary key to set
     */
    public abstract void setId(K id);
}