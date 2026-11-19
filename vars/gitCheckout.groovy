/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */

/**
 * Checks out a Git repository.
 *
 * Usage:
 *   gitCheckout(url: 'https://github.com/<owner>/<repo>.git', branch: 'main')
 *   gitCheckout(url: 'git@github.com:<owner>/<repo>.git', branch: 'main', credentialsId: 'github-ssh')
 *
 * In a Multibranch Pipeline prefer the built-in `checkout scm`.
 */
def call(Map config = [:]) {
    if (!config.url) {
        error 'gitCheckout: "url" is required'
    }
    String branch = config.branch ?: 'main'
    Map remote = [url: config.url]
    if (config.credentialsId) {
        remote.credentialsId = config.credentialsId
    }
    checkout([
        $class           : 'GitSCM',
        branches         : [[name: branch]],
        userRemoteConfigs: [remote]
    ])
}
