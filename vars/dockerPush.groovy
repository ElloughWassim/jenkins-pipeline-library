/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.ImageReference
import io.github.elloughwassim.jenkins.Shell

/**
 * Logs in to the registry, pushes every tag, then logs out.
 *
 * Docker Hub: `credentialsId` must reference a "Username with password" credential
 * (use a Docker Hub access token as the password).
 * Amazon ECR: the agent needs AWS credentials, either from an instance/IRSA role or
 * from an "AWS Credentials" credential passed as `awsCredentialsId`
 * (requires the "AWS Credentials" / "Pipeline: AWS Steps" plugin).
 *
 * Usage:
 *   dockerPush(image: imageConfig, credentialsId: 'dockerhub-credentials')
 *   dockerPush(image: ecrImageConfig, awsCredentialsId: 'aws-credentials')
 */
def call(Map config = [:]) {
    ImageReference image
    try {
        image = config.image instanceof ImageReference ? config.image : ImageReference.from(config.image as Map)
    } catch (IllegalArgumentException e) {
        error "dockerPush: ${e.message}"
    }

    if (image.registry == ImageReference.DOCKERHUB) {
        if (!config.credentialsId) {
            error 'dockerPush: "credentialsId" is required for Docker Hub'
        }
        withCredentials([usernamePassword(credentialsId: config.credentialsId,
                                          usernameVariable: 'REGISTRY_USERNAME',
                                          passwordVariable: 'REGISTRY_PASSWORD')]) {
            // Single quotes: the shell expands the secret, Groovy never interpolates it.
            sh 'printf "%s" "$REGISTRY_PASSWORD" | docker login --username "$REGISTRY_USERNAME" --password-stdin'
            pushAll(image)
        }
    } else {
        Closure loginAndPush = {
            String host = Shell.quote(image.registryHost)
            sh "aws ecr get-login-password --region ${Shell.quote(image.awsRegion)} | docker login --username AWS --password-stdin ${host}"
            pushAll(image)
        }
        if (config.awsCredentialsId) {
            withCredentials([aws(credentialsId: config.awsCredentialsId,
                                 accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                 secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                loginAndPush()
            }
        } else {
            loginAndPush()
        }
    }
    return image
}

private void pushAll(ImageReference image) {
    try {
        image.fullReferences.each { ref -> sh "docker push ${Shell.quote(ref)}" }
    } finally {
        String host = image.registryHost ? ' ' + Shell.quote(image.registryHost) : ''
        sh "docker logout${host} || true"
    }
}
