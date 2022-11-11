#!/usr/bin/env bash
set -e
set +x

# Exit when we run out of heap memory
JAVA_OPTS="${JAVA_OPTS} -XX:+ExitOnOutOfMemoryError"

# Disable FIPS if needed
if [ "$FIPS_MODE" = "disabled" ]; then
    export JAVA_OPTS="${JAVA_OPTS} -Dcom.redhat.fips=false"
fi

# Start Drain Cleaner
#exec /usr/bin/tini -s -w -e 143 -- java $JAVA_OPTS -classpath "$JAVA_CLASSPATH" "$JAVA_MAIN" "$@"
exec /usr/bin/tini -s -w -e 143 -- java $JAVA_OPTS -jar /opt/strimzi/quarkus-run.jar "$@"