/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.utils.logs;

import io.skodjob.testframe.LogCollector;
import io.skodjob.testframe.LogCollectorBuilder;
import io.skodjob.testframe.clients.KubeClient;
import io.skodjob.testframe.clients.cmdClient.Kubectl;
import io.strimzi.utils.Constants;
import io.strimzi.utils.Environment;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestLogCollector {

    private static final String CURRENT_DATE;

    static {
        // Get current date to create a unique folder
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        dateTimeFormatter = dateTimeFormatter.withZone(ZoneId.of("GMT"));
        CURRENT_DATE = dateTimeFormatter.format(LocalDateTime.now());
    }

    /**
     * Method for creating default builder of the {@link LogCollector}.
     * It provides default list of resources into the builder and configures the required {@link KubeClient} and
     * {@link Kubectl}
     *
     * @return  {@link LogCollectorBuilder} with default configuration for the tests
     */
    private static LogCollectorBuilder defaultLogCollector() {
        List<String> resources = new ArrayList<>(List.of(
            "secret",
            "deployment"
        ));

        return new LogCollectorBuilder()
            .withKubeClient(new KubeClient())
            .withKubeCmdClient(new Kubectl())
            .withNamespacedResources(resources.toArray(new String[0]));
    }

    /**
     * Method that checks existence of the folder on specified path.
     * From there, if there are no sub-dirs created - for each of the test-case run/re-run - the method returns the
     * full path containing the specified path and index (1).
     * Otherwise, it lists all the directories, filtering all the folders that are indexes, takes the last one, and returns
     * the full path containing specified path and index increased by one.
     *
     * @param rootPathToLogsForTestCase     complete path for test-class/test-class and test-case logs
     *
     * @return  full path to logs directory built from specified root path and index
     */
    private static Path checkPathAndReturnFullRootPathWithIndexFolder(Path rootPathToLogsForTestCase) {
        File logsForTestCase = rootPathToLogsForTestCase.toFile();
        int index = 1;

        if (logsForTestCase.exists()) {
            String[] filesInLogsDir = logsForTestCase.list();

            if (filesInLogsDir != null && filesInLogsDir.length > 0) {
                index = Integer.parseInt(
                    Arrays
                        .stream(filesInLogsDir)
                        .filter(file -> {
                            try {
                                Integer.parseInt(file);
                                return true;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        })
                        .sorted()
                        .toList()
                        .get(filesInLogsDir.length - 1)
                ) + 1;
            }
        }

        return rootPathToLogsForTestCase.resolve(String.valueOf(index));
    }

    /**
     * Method for building the full path to logs for specified test-class and test-case.
     *
     * @param testClass     name of the test-class
     * @param testCase      name of the test-case
     *
     * @return full path to the logs for test-class and test-case, together with index
     */
    private static Path buildFullPathToLogs(String testClass, String testCase) {
        Path rootPathToLogsForTestCase = Path.of(Environment.TEST_LOG_DIR, CURRENT_DATE, testClass);

        if (testCase != null) {
            rootPathToLogsForTestCase = rootPathToLogsForTestCase.resolve(testCase);
        }

        return checkPathAndReturnFullRootPathWithIndexFolder(rootPathToLogsForTestCase);
    }

    /**
     * Method that uses {@link LogCollector#collectFromNamespaces(String...)} method for collecting logs from Namespaces
     * for the particular combination of test-class and test-case.
     *
     * @param testClass     name of the test-class, for which the logs should be collected
     * @param testCase      name of the test-case, for which the logs should be collected
     */
    public static void collectLogs(String testClass, String testCase) {
        Path rootPathToLogsForTestCase = buildFullPathToLogs(testClass, testCase);

        final LogCollector testCaseCollector = defaultLogCollector()
            .withRootFolderPath(rootPathToLogsForTestCase.toString())
            .build();

        testCaseCollector.collectFromNamespaces(Constants.NAMESPACE);
    }
}
