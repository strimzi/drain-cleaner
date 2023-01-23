/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.enums;

public enum InstallType {
    Bundle,
    Helm,
    Unknown;

    public static InstallType fromString(String text) {
        for (InstallType installType : InstallType.values()) {
            if (installType.toString().equalsIgnoreCase(text)) {
                return installType;
            }
        }
        return Unknown;
    }
}
