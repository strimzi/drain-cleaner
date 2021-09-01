/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.test;

import io.strimzi.HealthCheck;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class HealthCheckTest {
    @Test
    public void testStaticSuccessResponse() {
        HealthCheck check = new HealthCheck();
        assertThat(check.health(), is("{\"status\":\"RUNNING\"}"));
    }
}
