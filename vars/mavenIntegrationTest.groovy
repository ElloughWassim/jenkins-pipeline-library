/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.Maven

/**
 * Runs integration tests (Maven `verify` phase, Failsafe plugin) and publishes reports.
 *
 * Unit tests are skipped through a POM property (default `skipUTs`). The consuming
 * project must bind it to Surefire, for example:
 *
 *   <plugin>
 *     <artifactId>maven-surefire-plugin</artifactId>
 *     <configuration><skipTests>${skipUTs}</skipTests></configuration>
 *   </plugin>
 *
 * Usage:
 *   mavenIntegrationTest()
 *   mavenIntegrationTest(profiles: 'tomcat90', skipUnitTestsProperty: null)  // run everything
 */
def call(Map config = [:]) {
    String skipProperty = config.containsKey('skipUnitTestsProperty') ? config.skipUnitTestsProperty : 'skipUTs'
    Map properties = [:] + (config.properties ?: [:])
    if (skipProperty) {
        properties[skipProperty] = 'true'
    }
    try {
        sh Maven.command(fileExists('mvnw'), ['verify'], [profiles: config.profiles, properties: properties])
    } finally {
        if (config.publishReports != false) {
            junit testResults: '**/target/failsafe-reports/*.xml', allowEmptyResults: true
        }
    }
}
