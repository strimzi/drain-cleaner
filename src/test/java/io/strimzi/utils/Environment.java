/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Environment {
    private static final Logger LOGGER = LogManager.getLogger(Environment.class);
    private static final Map<String, String> VALUES = new HashMap<>();

    private static final String CLEANER_REGISTRY_ENV = "DOCKER_REGISTRY";

    private static final String CLEANER_ORG_ENV = "DOCKER_ORG";

    private static final String CLEANER_TAG_ENV = "DOCKER_TAG";

    public static final String CLEANER_REGISTRY = getOrDefault(CLEANER_REGISTRY_ENV, null);
    public static final String CLEANER_ORG = getOrDefault(CLEANER_ORG_ENV, null);
    public static final String CLEANER_TAG = getOrDefault(CLEANER_TAG_ENV, null);

    static {
        String debugFormat = "{}: {}";
        LOGGER.info(String.join("", Collections.nCopies(76, "#")));
        LOGGER.info("Used environment variables:");
        VALUES.forEach((key, value) -> LOGGER.info(debugFormat, key, value));
        LOGGER.info(String.join("", Collections.nCopies(76, "#")));
    }

    private static String getOrDefault(String var, String defaultValue) {
        String value = System.getenv(var) != null ? System.getenv(var) : defaultValue;
        VALUES.put(var, value);
        return value;
    }
}
