/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.ImageReference
import io.github.elloughwassim.jenkins.Shell

/**
 * Removes the locally built tags from the agent to free disk space.
 * Never fails the build. Typically called from `post { always { ... } }`.
 *
 * Usage:
 *   dockerCleanup(image: imageConfig)
 */
def call(Map config = [:]) {
    try {
        ImageReference image = config.image instanceof ImageReference ? config.image : ImageReference.from(config.image as Map)
        sh "docker image rm ${Shell.join(image.fullReferences)} || true"
    } catch (IllegalArgumentException e) {
        echo "dockerCleanup: skipped (${e.message})"
    }
}
