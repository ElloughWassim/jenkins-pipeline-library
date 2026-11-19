/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.Maven

/**
 * Runs unit tests (Maven `test` phase) and publishes the Surefire reports.
 *
 * Usage:
 *   mavenUnitTest()
 *   mavenUnitTest(profiles: 'ci', publishReports: false)
 */
def call(Map config = [:]) {
    try {
        sh Maven.command(fileExists('mvnw'), ['test'], [profiles: config.profiles, properties: config.properties])
    } finally {
        if (config.publishReports != false) {
            junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
        }
    }
}
