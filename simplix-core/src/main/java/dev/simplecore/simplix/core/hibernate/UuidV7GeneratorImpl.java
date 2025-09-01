package dev.simplecore.simplix.core.hibernate;

import dev.simplecore.simplix.core.util.UuidUtils;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

/**
 * Implementation of UUID Version 7 Generator
 */
public class UuidV7GeneratorImpl implements IdentifierGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        return UuidUtils.generateUuidV7();
    }
}
