/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
package io.github.elloughwassim.jenkins

/**
 * Builds Maven command lines in a consistent, reproducible way.
 */
final class Maven implements Serializable {

    private static final long serialVersionUID = 1L

    private Maven() {
    }

    /**
     * @param useWrapper  {@code true} to call {@code ./mvnw}, otherwise {@code mvn}
     * @param goals       Maven goals/phases, for example {@code ['clean', 'verify']}
     * @param config      optional keys: {@code profiles} (String or List),
     *                    {@code properties} (Map), {@code skipTests} (boolean)
     */
    static String command(boolean useWrapper, List goals, Map config = [:]) {
        List<String> parts = [useWrapper ? './mvnw' : 'mvn', '-B', '-ntp']
        goals.each { parts << Shell.requireToken('goal', it) }

        def profiles = config.profiles
        if (profiles) {
            String joined = profiles instanceof Collection ? profiles.join(',') : profiles.toString()
            parts << '-P' + Shell.requireToken('profiles', joined)
        }
        if (config.skipTests) {
            parts << '-DskipTests'
        }
        (config.properties ?: [:]).each { key, value ->
            Shell.requireToken('property name', key)
            parts << Shell.quote("-D${key}=${value}")
        }
        parts.join(' ')
    }
}
