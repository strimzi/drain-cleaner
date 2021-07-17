package cz.scholz;

import io.quarkus.runtime.Quarkus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.enterprise.inject.Produces;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@CommandLine.Command
public class DrainCleaner implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(DrainCleaner.class);

    @CommandLine.Option(names = {"-k", "--kafka"}, description = "Handle Kafka pod evictions", defaultValue = "false")
    boolean kafka;

    @CommandLine.Option(names = {"-z", "--zookeeper"}, description = "Handle ZooKeeper pod evictions", defaultValue = "false")
    boolean zoo;

    @Produces
    static Pattern matchingPattern;

    @Override
    public void run() {
        if (!kafka && !zoo) {
            LOG.error("At least one of the --kafka and --zookeeper options needs ot be enabled!");
            System.exit(1);
        } else {
            List<String> contains = new ArrayList<>(2);

            if (kafka)  {
                contains.add("-kafka-");
                LOG.info("Draining of Kafka pods enabled");
            }

            if (zoo)    {
                contains.add("-zookeeper-");
                LOG.info("Draining of ZooKeeper pods enabled");
            }

            String patternString = ".+(" + String.join("|", contains) + ")[0-9]+";
            LOG.info("Matching pattern is {}", patternString);
            matchingPattern = Pattern.compile(patternString);
        }

        Quarkus.waitForExit();
    }
}