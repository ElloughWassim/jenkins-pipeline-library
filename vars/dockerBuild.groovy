/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.ImageReference
import io.github.elloughwassim.jenkins.Shell

/**
 * Builds a Docker image and applies every requested tag.
 *
 * Usage (Docker Hub):
 *   dockerBuild(image: [registry: 'dockerhub', namespace: '<DOCKERHUB_USER>', name: 'my-app', tags: imageTags()])
 * Usage (Amazon ECR):
 *   dockerBuild(image: [registry: 'ecr', awsAccountId: '<AWS_ACCOUNT_ID>', awsRegion: 'eu-west-1',
 *                       name: 'my-app', tags: imageTags()],
 *               context: '.', dockerfile: 'Dockerfile', buildArgs: [APP_VERSION: '1.0.0'])
 *
 * A build argument whose value is null is passed as `--build-arg NAME` only, so Docker
 * reads the value from the environment variable of the same name. Use this with
 * `withCredentials` to keep values out of the command line:
 *
 *   withCredentials([string(credentialsId: 'api-key', variable: 'API_KEY')]) {
 *       dockerBuild(image: imageConfig, buildArgs: [API_KEY: null])
 *   }
 *
 * Build arguments are still visible in the image history: never use them for
 * server-side secrets (use BuildKit secrets instead).
 * Returns the ImageReference that was built.
 */
def call(Map config = [:]) {
    ImageReference image = toImage(config.image)
    String context = config.context ?: '.'
    String dockerfile = config.dockerfile ?: 'Dockerfile'

    List args = ['docker', 'build', '--pull', '-f', dockerfile]
    (config.buildArgs ?: [:]).each { key, value ->
        if (!(key ==~ /^[A-Za-z_][A-Za-z0-9_]*$/)) {
            error "dockerBuild: invalid build argument name '${key}'"
        }
        args << '--build-arg' << (value == null ? key.toString() : "${key}=${value}".toString())
    }
    image.fullReferences.each { ref -> args << '-t' << ref }
    args << context

    sh Shell.join(args)
    echo "Built image ${image.fullReferences.join(', ')}"
    return image
}

private ImageReference toImage(def image) {
    try {
        return image instanceof ImageReference ? image : ImageReference.from(image as Map)
    } catch (IllegalArgumentException e) {
        error "dockerBuild: ${e.message}"
    }
}
