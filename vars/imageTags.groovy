/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.ImageReference

/**
 * Returns a list of image tags for the current build:
 * an immutable `<BUILD_NUMBER>-<short git sha>` tag first, then optional extra tags.
 *
 * Usage:
 *   def tags = imageTags()                        // ['42-abc1234', 'latest']
 *   def tags = imageTags(extraTags: [], version: '1.2.0')  // ['1.2.0-42-abc1234']
 */
def call(Map config = [:]) {
    String sha = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
    String base = "${env.BUILD_NUMBER}-${sha}"
    if (config.version) {
        base = "${config.version}-${base}"
    }
    List tags = [base] + (config.containsKey('extraTags') ? config.extraTags : ['latest'])
    tags.each { tag ->
        if (!ImageReference.isValidTag(tag?.toString())) {
            error "imageTags: invalid tag '${tag}'"
        }
    }
    return tags.collect { it.toString() }
}
