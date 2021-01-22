package org.kiwiproject.registry.model;

/**
 * Enum that defines whether all data from the native service registry should be included in {@link ServiceInstance}
 * objects returned by the registry, e.g. Eureka or Consul.
 */
public enum NativeRegistryData {

    /**
     * Include all data from the registry, regardless of whether it is mapped into {@link ServiceInstance}. The
     * native data can then be retrieved via {@code ServiceInstance#getNativeRegistryData()}.
     */
    INCLUDE_NATIVE_DATA,

    /**
     * Do not include any data from the registry that isn't mapped into {@link ServiceInstance}.
     */
    IGNORE_NATIVE_DATA
}
