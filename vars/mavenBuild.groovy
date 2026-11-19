/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
import io.github.elloughwassim.jenkins.Maven

/**
 * Packages a Maven project. Uses ./mvnw when it is present, otherwise `mvn`.
 *
 * Usage:
 *   mavenBuild()                                  // ./mvnw -B -ntp clean package -DskipTests
 *   mavenBuild(goals: ['clean', 'install'], skipTests: false, profiles: 'prod')
 */
def call(Map config = [:]) {
    List goals = config.goals ?: ['clean', 'package']
    Map options = [
        profiles  : config.profiles,
        properties: config.properties,
        skipTests : config.containsKey('skipTests') ? config.skipTests : true
    ]
    sh Maven.command(fileExists('mvnw'), goals, options)
}
