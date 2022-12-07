/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class CertificateWatchTest {
    @Test
    public void testChangeDetection() throws IOException, InterruptedException {
        Path tempDirectory = Files.createTempDirectory("watcher-test-dir");

        // We use Atomic Reference to be able to reset the CountDownLatch while calking it from Lambda
        AtomicReference<CountDownLatch> latch = new AtomicReference<>();
        latch.set(new CountDownLatch(1));

        CertificateWatch.ChangeWatcher watcher = new CertificateWatch.ChangeWatcher(tempDirectory.toFile().getAbsolutePath(), () -> latch.get().countDown());
        watcher.start();

        try {
            Path testFile = Files.createTempFile(tempDirectory, "test", "file");

            // Check creation
            latch.get().await();
            latch.set(new CountDownLatch(1));

            // Check modification
            Files.write(testFile, "Hello world".getBytes());
            latch.get().await();
            latch.set(new CountDownLatch(1));

            // Check deletion
            Files.deleteIfExists(testFile);
            latch.get().await();
        } finally {
            watcher.stop();
            Files.deleteIfExists(tempDirectory);
        }
    }
}
