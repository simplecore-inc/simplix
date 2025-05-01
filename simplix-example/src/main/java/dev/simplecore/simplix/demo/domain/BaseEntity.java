package dev.simplecore.simplix.demo.domain;


import lombok.Getter;
import lombok.Setter;

import dev.simplecore.simplix.core.entity.SimpliXBaseEntity;

import javax.persistence.MappedSuperclass;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity<K> extends SimpliXBaseEntity<K> {
}
