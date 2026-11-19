/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
package io.github.elloughwassim.jenkins

/**
 * Immutable, validated reference to a container image in Docker Hub or Amazon ECR.
 *
 * <p>Every value that ends up in a shell command is validated against the
 * Docker/ECR naming rules, so a malicious or mistyped parameter fails fast
 * instead of being interpolated into {@code sh}.</p>
 *
 * <pre>
 * // Docker Hub
 * ImageReference.from(registry: 'dockerhub', namespace: 'myuser', name: 'my-app', tags: ['42-abc1234', 'latest'])
 * // Amazon ECR
 * ImageReference.from(registry: 'ecr', awsAccountId: '123456789012', awsRegion: 'eu-west-1', name: 'my-app', tags: ['42-abc1234'])
 * </pre>
 */
final class ImageReference implements Serializable {

    private static final long serialVersionUID = 1L

    static final String DOCKERHUB = 'dockerhub'
    static final String ECR = 'ecr'

    private static final String NAME_PATTERN = /^[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*(?:\/[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*)*$/
    private static final String NAMESPACE_PATTERN = /^[a-z0-9]+(?:[._-][a-z0-9]+)*$/
    private static final String TAG_PATTERN = /^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/
    private static final String ACCOUNT_PATTERN = /^[0-9]{12}$/
    private static final String REGION_PATTERN = /^[a-z]{2}(?:-gov|-iso[a-z]*)?-[a-z]+-[0-9]$/

    final String registry
    final String namespace
    final String awsAccountId
    final String awsRegion
    final String name
    final List<String> tags

    private ImageReference(String registry, String namespace, String awsAccountId,
                           String awsRegion, String name, List<String> tags) {
        this.registry = registry
        this.namespace = namespace
        this.awsAccountId = awsAccountId
        this.awsRegion = awsRegion
        this.name = name
        this.tags = Collections.unmodifiableList(new ArrayList<String>(tags))
    }

    /**
     * Builds a reference from a configuration map and validates it.
     *
     * @throws IllegalArgumentException when a value is missing or invalid
     */
    static ImageReference from(Map config) {
        if (config == null) {
            throw new IllegalArgumentException('Image configuration is required')
        }
        String registry = (config.registry ?: DOCKERHUB).toString().toLowerCase()
        String name = requireMatch('name', config.name, NAME_PATTERN)
        List<String> tags = normalizeTags(config.tags)

        switch (registry) {
            case DOCKERHUB:
                String namespace = requireMatch('namespace', config.namespace, NAMESPACE_PATTERN)
                return new ImageReference(DOCKERHUB, namespace, null, null, name, tags)
            case ECR:
                String account = requireMatch('awsAccountId', config.awsAccountId, ACCOUNT_PATTERN)
                String region = requireMatch('awsRegion', config.awsRegion, REGION_PATTERN)
                return new ImageReference(ECR, null, account, region, name, tags)
            default:
                throw new IllegalArgumentException(
                    "Unsupported registry '${registry}'. Use '${DOCKERHUB}' or '${ECR}'.")
        }
    }

    /** Registry host used for {@code docker login}; {@code null} means Docker Hub. */
    String getRegistryHost() {
        registry == ECR ? "${awsAccountId}.dkr.ecr.${awsRegion}.amazonaws.com" : null
    }

    /** Repository without tag, for example {@code myuser/my-app}. */
    String getRepository() {
        registry == ECR ? "${registryHost}/${name}" : "${namespace}/${name}"
    }

    /** Fully qualified references, one per tag. */
    List<String> getFullReferences() {
        tags.collect { String tag -> "${repository}:${tag}".toString() }
    }

    /** First (primary, normally immutable) reference. */
    String getPrimaryReference() {
        fullReferences.first()
    }

    /** Local build name used before tagging, for example {@code my-app:42-abc1234}. */
    String getLocalReference() {
        "${name}:${tags.first()}"
    }

    static boolean isValidTag(String tag) {
        tag != null && tag ==~ TAG_PATTERN
    }

    private static List<String> normalizeTags(Object rawTags) {
        List<String> tags
        if (rawTags == null) {
            tags = ['latest']
        } else if (rawTags instanceof Collection) {
            tags = rawTags.collect { it?.toString() }
        } else {
            tags = rawTags.toString().split(',').collect { it.trim() }
        }
        tags = tags.findAll { it }.unique()
        if (tags.isEmpty()) {
            throw new IllegalArgumentException('At least one image tag is required')
        }
        tags.each { String tag ->
            if (!isValidTag(tag)) {
                throw new IllegalArgumentException("Invalid image tag '${tag}'")
            }
        }
        tags
    }

    private static String requireMatch(String field, Object value, String pattern) {
        if (value == null || value.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("Image configuration '${field}' is required")
        }
        String text = value.toString().trim()
        if (!(text ==~ pattern)) {
            throw new IllegalArgumentException("Invalid value for '${field}': '${text}'")
        }
        text
    }

    @Override
    String toString() {
        primaryReference
    }
}
