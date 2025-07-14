package dev.simplecore.simplix.demo.domain;


import dev.simplecore.simplix.core.entity.SimpliXBaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.MappedSuperclass;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity<K> extends SimpliXBaseEntity<K> {
}
