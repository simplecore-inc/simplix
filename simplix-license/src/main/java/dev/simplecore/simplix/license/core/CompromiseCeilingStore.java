package dev.simplecore.simplix.license.core;

import java.util.Optional;

/**
 * Where the fixed ceiling is kept.
 *
 * <p>It lives beside the registration rather than in a file of its own, because it has to
 * survive exactly what the registration survives. A ceiling a replaced container loses would
 * re-fix itself from whatever token that container then holds, which is the one thing it exists
 * to prevent.
 *
 * <p>The store a product contributes implements this alongside the SDK's registration store.
 * There is no default implementation on purpose: a deployment silently unable to keep a ceiling
 * would fail at a moment nobody is watching, whereas a context that will not start is read the
 * day the product is built.
 */
public interface CompromiseCeilingStore {

    /**
     * @return the fixed ceiling, or empty when none has been fixed yet
     */
    Optional<CompromiseCeiling> loadCeiling();

    /**
     * Fixes the ceiling, replacing whatever was stored before.
     *
     * @param ceiling what this deployment held when it learned of the compromise
     */
    void saveCeiling(CompromiseCeiling ceiling);
}
