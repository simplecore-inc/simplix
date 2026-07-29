package dev.simplecore.simplix.license.store;

/**
 * Package reference for the entity this module persists.
 *
 * <p>An application must include this package wherever it declares which entities its
 * persistence unit manages — {@code @EntityScan}, a {@code PersistenceManagedTypesScanner}, or
 * {@code setPackagesToScan}. Without it the registration entity is unknown and every license
 * query fails at runtime rather than at startup.
 *
 * <p>This module deliberately does not contribute an {@code @EntityScan} of its own. Declaring
 * one from a library replaces the application's default entity scan rather than adding to it,
 * which would silently drop every entity the application owns.
 *
 * <pre>{@code
 * @EntityScan(basePackages = {"com.example.domain", LicenseStorePackage.BASE_PACKAGE})
 * }</pre>
 */
public interface LicenseStorePackage {

    /** Base package: {@code dev.simplecore.simplix.license.store} */
    String BASE_PACKAGE = LicenseStorePackage.class.getPackageName();
}
