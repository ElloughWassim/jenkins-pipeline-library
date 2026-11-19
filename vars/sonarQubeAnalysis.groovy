/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.Maven
import io.github.elloughwassim.jenkins.Shell

/**
 * Runs a SonarQube analysis inside `withSonarQubeEnv`.
 *
 * Requires the "SonarQube Scanner" Jenkins plugin and a SonarQube server
 * configured under Manage Jenkins > System (the `installationName`).
 *
 * Usage (Maven project, reuses compiled classes, no extra `clean`):
 *   sonarQubeAnalysis(installationName: 'sonarqube', projectKey: 'my-app')
 *
 * Usage (any language, SonarScanner CLI tool configured in Jenkins):
 *   sonarQubeAnalysis(installationName: 'sonarqube', projectKey: 'my-app',
 *                     scanner: 'cli', scannerTool: 'sonar-scanner')
 */
def call(Map config = [:]) {
    if (!config.installationName) {
        error 'sonarQubeAnalysis: "installationName" is required'
    }
    String scanner = config.scanner ?: 'maven'
    Map sonarProperties = [:]
    if (config.projectKey) {
        sonarProperties['sonar.projectKey'] = Shell.requireToken('projectKey', config.projectKey)
    }
    if (config.projectName) {
        sonarProperties['sonar.projectName'] = config.projectName
    }
    sonarProperties.putAll(config.properties ?: [:])

    Map envArgs = config.credentialsId ? [installationName: config.installationName, credentialsId: config.credentialsId]
                                       : [installationName: config.installationName]
    withSonarQubeEnv(envArgs) {
        if (scanner == 'maven') {
            sh Maven.command(fileExists('mvnw'), ['sonar:sonar'], [properties: sonarProperties])
        } else if (scanner == 'cli') {
            String scannerHome = tool(config.scannerTool ?: 'sonar-scanner')
            List args = sonarProperties.collect { key, value -> "-D${key}=${value}" }
            sh "${Shell.quote(scannerHome + '/bin/sonar-scanner')} ${Shell.join(args)}".trim()
        } else {
            error "sonarQubeAnalysis: unsupported scanner '${scanner}' (use 'maven' or 'cli')"
        }
    }
}
