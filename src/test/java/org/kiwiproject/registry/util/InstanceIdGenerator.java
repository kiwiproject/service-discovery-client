package org.kiwiproject.registry.util;

import static org.kiwiproject.base.UUIDs.randomUUIDString;

import lombok.experimental.UtilityClass;

@UtilityClass
public class InstanceIdGenerator {

    public static String uniqueInstanceId() {
        return "instance-" + randomUUIDString();
    }
}
