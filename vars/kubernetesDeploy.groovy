/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.Shell

/**
 * Renders Kubernetes manifests with the image to deploy, applies them and waits
 * for the Deployment rollout. A failed rollout is rolled back and fails the build.
 *
 * The manifests reference the image through a placeholder (default `IMAGE_PLACEHOLDER`),
 * which is replaced in a temporary copy. The files in Git are never modified, and
 * re-applying them never resets the running image to an old value.
 *
 *   containers:
 *     - name: app
 *       image: IMAGE_PLACEHOLDER
 *
 * Requires `kubectl` on the agent. Cluster access comes from either:
 *   - `kubeconfigCredentialsId`: a "Secret file" credential holding a kubeconfig, or
 *   - the agent's current kubeconfig (for example after `aws eks update-kubeconfig`).
 *
 * Usage:
 *   kubernetesDeploy(manifests: 'k8s', namespace: 'my-app', deployment: 'my-app',
 *                    image: image.primaryReference, kubeconfigCredentialsId: 'kubeconfig')
 *
 * Options:
 *   imagePlaceholder  default 'IMAGE_PLACEHOLDER'
 *   rolloutTimeout    default '180s'
 */
def call(Map config = [:]) {
    ['manifests', 'namespace', 'deployment', 'image'].each { key ->
        if (!config[key]) {
            error "kubernetesDeploy: \"${key}\" is required"
        }
    }
    String manifests = Shell.requirePath('manifests', config.manifests)
    String ns = Shell.requireToken('namespace', config.namespace)
    String deployment = Shell.requireToken('deployment', config.deployment)
    String placeholder = Shell.requireToken('imagePlaceholder', config.imagePlaceholder ?: 'IMAGE_PLACEHOLDER')
    String timeoutValue = Shell.requireToken('rolloutTimeout', config.rolloutTimeout ?: '180s')
    String image = config.image.toString()
    if (!(image ==~ /^[A-Za-z0-9][A-Za-z0-9._\/:@-]*$/)) {
        error "kubernetesDeploy: invalid image reference '${image}'"
    }
    String rendered = '.k8s-rendered'

    Closure deploy = {
        sh "rm -rf ${rendered} && mkdir -p ${rendered} && cp -R ${Shell.quote(manifests)} ${rendered}/"
        sh "find ${rendered} -type f \\( -name '*.yaml' -o -name '*.yml' \\) " +
           "-exec sed -i 's|${placeholder}|${image}|g' {} +"
        sh "kubectl apply -n ${ns} --recursive -f ${rendered}"
        int status = sh(returnStatus: true,
                        script: "kubectl rollout status -n ${ns} deployment/${deployment} --timeout=${timeoutValue}")
        if (status != 0) {
            sh "kubectl get pods -n ${ns} -o wide || true"
            sh "kubectl rollout undo -n ${ns} deployment/${deployment} || true"
            error "kubernetesDeploy: rollout of deployment/${deployment} failed and was rolled back"
        }
        echo "Deployed ${image} to deployment/${deployment} in namespace ${ns}"
    }

    if (config.kubeconfigCredentialsId) {
        withCredentials([file(credentialsId: config.kubeconfigCredentialsId, variable: 'KUBECONFIG')]) {
            deploy()
        }
    } else {
        deploy()
    }
}
