/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */

/**
 * Waits for the SonarQube Quality Gate result of the previous analysis.
 *
 * Fails the build when the gate fails (abortPipeline defaults to true) and never
 * waits longer than `timeoutMinutes`. Requires a SonarQube webhook pointing to
 * <JENKINS_URL>/sonarqube-webhook/.
 *
 * Usage:
 *   sonarQubeQualityGate()
 *   sonarQubeQualityGate(timeoutMinutes: 5, abortPipeline: false)  // report only
 */
def call(Map config = [:]) {
    int minutes = (config.timeoutMinutes ?: 10) as int
    boolean abort = config.containsKey('abortPipeline') ? config.abortPipeline as boolean : true
    Map gateArgs = [abortPipeline: abort]
    if (config.credentialsId) {
        gateArgs.credentialsId = config.credentialsId
    }
    timeout(time: minutes, unit: 'MINUTES') {
        def gate = waitForQualityGate(gateArgs)
        echo "SonarQube Quality Gate status: ${gate?.status}"
        return gate
    }
}
