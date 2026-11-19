# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project uses [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-11-19

### Added
- Steps: `gitCheckout`, `mavenBuild`, `mavenUnitTest`, `mavenIntegrationTest`,
  `sonarQubeAnalysis`, `sonarQubeQualityGate`, `imageTags`, `dockerBuild`, `dockerPush`,
  `dockerCleanup`, `trivyScan`, `owaspDependencyCheck`, `kubernetesDeploy`.
- Helper classes `ImageReference`, `Shell` and `Maven` with input validation.
- Docker Hub and Amazon ECR support through one image configuration map.
- Self-contained test suite (`test/run-tests.groovy`) and GitHub Actions workflow.
