/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.Shell

/**
 * Runs Trivy and fails the build when findings at or above `severity` exist.
 *
 * Reports are written to `reportDir` and archived even when the scan fails:
 *   <name>.txt  (human-readable table)
 *   <name>.json (machine-readable, for later processing)
 *
 * Usage:
 *   trivyScan(type: 'fs', target: '.')                         // vulnerabilities, secrets, misconfigurations
 *   trivyScan(type: 'image', target: image.primaryReference)    // container image
 *   trivyScan(type: 'config', target: 'k8s/')                  // IaC only
 *   trivyScan(type: 'image', target: ref, failBuild: false)    // report only
 *
 * Options:
 *   severity      default 'HIGH,CRITICAL'
 *   ignoreUnfixed default true  (only findings that have a fix can fail the build)
 *   scanners      default 'vuln,secret,misconfig' for fs, 'vuln,secret' for image
 *   failBuild     default true
 *   reportName    default 'trivy-<type>'
 *   reportDir     default 'reports/trivy'
 */
def call(Map config = [:]) {
    String type = config.type ?: 'fs'
    if (!(type in ['fs', 'image', 'config', 'repo'])) {
        error "trivyScan: unsupported type '${type}'"
    }
    if (!config.target) {
        error 'trivyScan: "target" is required'
    }
    String severity = Shell.requireToken('severity', config.severity ?: 'HIGH,CRITICAL')
    boolean failBuild = config.containsKey('failBuild') ? config.failBuild as boolean : true
    boolean ignoreUnfixed = config.containsKey('ignoreUnfixed') ? config.ignoreUnfixed as boolean : true
    String reportDir = config.reportDir ?: 'reports/trivy'
    String reportName = Shell.requireToken('reportName', config.reportName ?: "trivy-${type}")
    String base = "${reportDir}/${reportName}"

    List common = ['--severity', severity, '--no-progress']
    if (type != 'config') {
        String scanners = Shell.requireToken('scanners', config.scanners ?: (type == 'image' ? 'vuln,secret' : 'vuln,secret,misconfig'))
        common += ['--scanners', scanners]
        if (ignoreUnfixed) {
            common << '--ignore-unfixed'
        }
    }

    sh "mkdir -p ${Shell.quote(reportDir)}"
    int status
    try {
        // JSON report first (never fails), then the gating table report.
        sh Shell.join(['trivy', type] + common + ['--format', 'json', '--output', "${base}.json".toString(), config.target.toString()])
        status = sh(returnStatus: true,
                    script: Shell.join(['trivy', type] + common + ['--format', 'table', '--output', "${base}.txt".toString(),
                                        '--exit-code', '1', config.target.toString()]))
        sh "cat ${Shell.quote(base + '.txt')}"
    } finally {
        archiveArtifacts artifacts: "${base}.*", allowEmptyArchive: true
    }

    if (status != 0) {
        String message = "Trivy found ${severity} issues in ${type} '${config.target}' (see ${base}.txt)"
        if (failBuild) {
            error message
        }
        unstable message
    }
    return status
}
