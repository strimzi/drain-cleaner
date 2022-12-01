/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 * Quarkus loads the TLS certificate at startup but does not reload it when it changes. So if the Drain Cleaner runs for
 * too long, it can happen that the certificate expires and Kubernetes cannot call the webhook. To prevent this, the
 * CertificateWatch is watching for changes to the TLS certificate files. If any changes are detected, it exits quarkus
 * to restart the container. This is by default enabled for production with the default path {@code /etc/webhook-certificates/}
 * (which is also the default path in the deployment). It is by default disabled in development. The configuration
 * options {@code certificate.watch.enabled} and {@code certificate.watch.path} can be used to customize the default
 * settings.
 */
@ApplicationScoped
public class CertificateWatch {
    private static final Logger LOG = LoggerFactory.getLogger(CertificateWatch.class);

    private final boolean enabled;

    private ChangeWatcher watcher;

    /**
     * Constructs the certificate watch. The Watcher class is created only if the certificate watch is enabled.
     */
    public CertificateWatch() {
        this.enabled = ConfigProvider.getConfig().getOptionalValue("certificate.watch.enabled", Boolean.class).orElse(false);

        if (enabled) {
            try {
                String path = ConfigProvider.getConfig().getOptionalValue("certificate.watch.path", String.class).orElse("/etc/webhook-certificates/");

                LOG.error("Creating certificate watch for path {}", path);

                this.watcher = new ChangeWatcher(path, () -> {
                    LOG.info("Certificate change detected => Drain Cleaner will be restarted");
                    Quarkus.asyncExit(0);
                });
            } catch (IOException e) {
                LOG.error("Failed to create certificate watch", e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Starts the watcher (if enabled)
     *
     * @param ev    Startup event
     */
    void onStart(@Observes StartupEvent ev) {
        if (enabled) {
            LOG.info("Starting certificate watch");
            watcher.start();
        } else {
            LOG.info("Certificate watch is not enabled");
        }
    }

    /**
     * Stops the watcher (if enabled)
     *
     * @param ev    Shutdown event
     */
    void onStop(@Observes ShutdownEvent ev) {
        if (enabled) {
            LOG.info("Stopping certificate watch");
            try {
                watcher.stop();
            } catch (InterruptedException e) {
                LOG.warn("Failed to stop the certificate watcher", e);
            }
        }
    }

    /**
     * Internal class which does the watching of a directory for changes using the Java NIO Watch service. If any change
     * is detected, a handler is called to take the action.
     */
    public static class ChangeWatcher implements Runnable {
        private final WatchService watchService;
        private final Thread watcherThread;
        private final Runnable changeHandler;

        private volatile boolean stop = false;

        /**
         * Constructs the ChangeWatcher to watch for changes to a directory on the filesystem
         *
         * @param path      The directory which should be watched
         * @param handler   The handler which should be run when any change is detected. The handler allows us to plug-in
         *                  a custom behavior to react for the file change.
         *
         * @throws IOException  IOException is thrown if we fail to create the Watch service
         */
        public ChangeWatcher(String path, Runnable handler) throws IOException {
            LOG.info("Creating ChangeWatcher for path {}", path);
            this.watchService = FileSystems.getDefault().newWatchService();
            this.watcherThread = new Thread(this, "certificate-change-watch");
            this.changeHandler = handler;
            Paths.get(path).register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        }

        /**
         * Starts watching
         */
        public void start() {
            LOG.info("Starting the certificate watcher");
            watcherThread.start();
        }

        @Override
        public void run() {
            while (!stop)   {
                try {
                    WatchKey key = watchService.take();

                    if (key != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            LOG.warn("Certificate {} changed ({})", event.context(), event.kind().name());
                            changeHandler.run();
                        }
                        key.reset();
                    }
                } catch (InterruptedException e) {
                    LOG.debug("Certificate watch was interrupted", e);
                }
            }
        }

        /**
         * Stops watching
         *
         * @throws InterruptedException Thrown if interrupted while trying to stop the watcher thread
         */
        public void stop() throws InterruptedException {
            LOG.info("Requesting the certificate watcher to stop");
            this.stop = true;

            watcherThread.interrupt();
            watcherThread.join();

            try {
                watchService.close();
            } catch (IOException e) {
                LOG.warn("Failed to stop the Java watch service", e);
            }
        }
    }
}
