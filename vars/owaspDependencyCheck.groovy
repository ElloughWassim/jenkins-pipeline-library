/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.Shell

/**
 * Runs OWASP Dependency-Check through the Jenkins "OWASP Dependency-Check" plugin
 * and publishes the report.
 *
 * `odcInstallation` is the tool name configured under Manage Jenkins > Tools.
 * An NVD API key is strongly recommended (otherwise database updates are very slow):
 * store it as a "Secret text" credential and pass its id as `nvdApiKeyCredentialsId`.
 *
 * Usage:
 *   owaspDependencyCheck(odcInstallation: 'dependency-check', failOnCvss: 7)
 *
 * Options:
 *   scanPath        default '.'
 *   failOnCvss      default 7 (0-10; 11 disables failing)
 *   extraArguments  default '' (for example '--disableYarnAudit')
 *   reportDir       default 'reports/dependency-check'
 */
def call(Map config = [:]) {
    if (!config.odcInstallation) {
        error 'owaspDependencyCheck: "odcInstallation" is required'
    }
    String reportDir = Shell.requirePath('reportDir', config.reportDir ?: 'reports/dependency-check')
    String scanPath = Shell.requirePath('scanPath', config.scanPath ?: '.')
    def cvss = config.containsKey('failOnCvss') ? config.failOnCvss : 7
    String baseArgs = "--scan ${scanPath} --out ${reportDir} --format XML --format HTML " +
                      "--failOnCVSS ${cvss as int} ${config.extraArguments ?: ''}".trim()

    Closure runScan = { String args ->
        try {
            dependencyCheck additionalArguments: args, odcInstallation: config.odcInstallation
        } finally {
            dependencyCheckPublisher pattern: "${reportDir}/dependency-check-report.xml"
            archiveArtifacts artifacts: "${reportDir}/dependency-check-report.*", allowEmptyArchive: true
        }
    }

    if (config.nvdApiKeyCredentialsId) {
        withCredentials([string(credentialsId: config.nvdApiKeyCredentialsId, variable: 'NVD_API_KEY')]) {
            // The plugin passes arguments to the CLI directly; the value is masked in the log.
            runScan("${baseArgs} --nvdApiKey ${env.NVD_API_KEY}")
        }
    } else {
        runScan(baseArgs)
    }
}
