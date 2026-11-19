/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 *
 * Self-contained test runner for the shared library.
 *
 * It loads each `vars/*.groovy` step with a mocked Jenkins pipeline context
 * (sh, withCredentials, error, ...), records every call, and asserts on the
 * generated commands. Only a plain Groovy 4 runtime is needed:
 *
 *   groovy test/run-tests.groovy
 */
import groovy.transform.Field

@Field File root = new File(getClass().protectionDomain.codeSource.location.path).parentFile.parentFile
@Field GroovyClassLoader loader = new GroovyClassLoader(this.class.classLoader)
loader.addClasspath(new File(root, 'src').absolutePath)

// ---------------------------------------------------------------------------
// Minimal pipeline mock
// ---------------------------------------------------------------------------
class PipelineError extends RuntimeException {
    PipelineError(String message) { super(message) }
}

class Pipeline {
    List<String> commands = []
    List<Map> calls = []
    Map<String, Integer> statusByCommand = [:]
    Map env = [BUILD_NUMBER: '42']
    Set<String> existingFiles = ['mvnw'] as Set
    String stdout = 'abc1234\n'

    Binding binding() {
        Binding b = new Binding()
        b.setVariable('env', env)
        b.setVariable('sh', { arg ->
            if (arg instanceof Map) {
                commands << arg.script.toString()
                if (arg.returnStdout) { return stdout }
                if (arg.returnStatus) {
                    return statusByCommand.find { k, v -> arg.script.toString().contains(k) }?.value ?: 0
                }
                return null
            }
            commands << arg.toString()
            null
        })
        b.setVariable('error', { String msg -> throw new PipelineError(msg) })
        b.setVariable('echo', { String msg -> calls << [step: 'echo', message: msg] })
        b.setVariable('unstable', { String msg -> calls << [step: 'unstable', message: msg] })
        b.setVariable('fileExists', { String f -> existingFiles.contains(f) })
        b.setVariable('tool', { String name -> "/opt/tools/${name}".toString() })
        ['checkout', 'junit', 'archiveArtifacts', 'dependencyCheck', 'dependencyCheckPublisher'].each { name ->
            b.setVariable(name, { Map args -> calls << ([step: name] + args) })
        }
        ['usernamePassword', 'string', 'file', 'aws'].each { name ->
            b.setVariable(name, { Map args -> [binding: name] + args })
        }
        b.setVariable('withCredentials', { List creds, Closure body ->
            calls << [step: 'withCredentials', credentials: creds]
            body()
        })
        b.setVariable('withSonarQubeEnv', { Map args, Closure body ->
            calls << ([step: 'withSonarQubeEnv'] + args)
            body()
        })
        b.setVariable('timeout', { Map args, Closure body ->
            calls << ([step: 'timeout'] + args)
            body()
        })
        b.setVariable('waitForQualityGate', { Map args ->
            calls << ([step: 'waitForQualityGate'] + args)
            [status: 'OK']
        })
        b
    }

    Map call(String step) { calls.find { it.step == step } }
}

Script loadStep(String name, Pipeline pipeline) {
    GroovyShell shell = new GroovyShell(loader, pipeline.binding())
    Script script = shell.parse(new File(root, "vars/${name}.groovy"))
    script
}

// ---------------------------------------------------------------------------
// Tiny test framework
// ---------------------------------------------------------------------------
@Field List<String> failures = []
@Field int passed = 0

void test(String name, Closure body) {
    try {
        body()
        passed++
        println "  PASS  ${name}"
    } catch (Throwable t) {
        failures << name
        println "  FAIL  ${name}: ${t.class.simpleName}: ${t.message}"
    }
}

void expectError(String fragment, Closure body) {
    try {
        body()
    } catch (Throwable t) {
        assert t.message?.contains(fragment): "expected error containing '${fragment}' but got '${t.message}'"
        return
    }
    assert false: "expected an error containing '${fragment}'"
}

def ImageReference = loader.loadClass('io.github.elloughwassim.jenkins.ImageReference')
def Shell = loader.loadClass('io.github.elloughwassim.jenkins.Shell')
def Maven = loader.loadClass('io.github.elloughwassim.jenkins.Maven')

Map dockerHub = [registry: 'dockerhub', namespace: 'exampleuser', name: 'demo-app', tags: ['42-abc1234', 'latest']]
Map ecr = [registry: 'ecr', awsAccountId: '123456789012', awsRegion: 'eu-west-1', name: 'demo-app', tags: ['42-abc1234']]

println 'src/ImageReference'
test('Docker Hub references') {
    def ref = ImageReference.from(dockerHub)
    assert ref.repository == 'exampleuser/demo-app'
    assert ref.fullReferences == ['exampleuser/demo-app:42-abc1234', 'exampleuser/demo-app:latest']
    assert ref.registryHost == null
}
test('ECR references') {
    def ref = ImageReference.from(ecr)
    assert ref.registryHost == '123456789012.dkr.ecr.eu-west-1.amazonaws.com'
    assert ref.primaryReference == '123456789012.dkr.ecr.eu-west-1.amazonaws.com/demo-app:42-abc1234'
}
test('tags accept a comma separated string and default to latest') {
    assert ImageReference.from(dockerHub + [tags: 'v1, latest']).tags == ['v1', 'latest']
    assert ImageReference.from(dockerHub + [tags: null]).tags == ['latest']
}
test('rejects shell metacharacters and invalid values') {
    expectError('name') { ImageReference.from(dockerHub + [name: 'app; rm -rf /']) }
    expectError('tag') { ImageReference.from(dockerHub + [tags: ['v1$(id)']]) }
    expectError('namespace') { ImageReference.from(dockerHub + [namespace: 'Bad User']) }
    expectError('awsAccountId') { ImageReference.from(ecr + [awsAccountId: '1234']) }
    expectError('awsRegion') { ImageReference.from(ecr + [awsRegion: 'europe']) }
    expectError('Unsupported registry') { ImageReference.from(dockerHub + [registry: 'quay']) }
}

println 'src/Shell and src/Maven'
test('quote escapes single quotes') {
    assert Shell.quote("it's") == "'it'\"'\"'s'"
    assert Shell.quote(null) == "''"
}
test('maven command uses wrapper, batch mode and quoted properties') {
    String cmd = Maven.command(true, ['clean', 'verify'], [profiles: ['ci', 'tomcat90'], properties: [skipUTs: 'true']])
    assert cmd == "./mvnw -B -ntp clean verify -Pci,tomcat90 '-DskipUTs=true'"
    assert Maven.command(false, ['test']) == 'mvn -B -ntp test'
}
test('maven command rejects injected goals') {
    expectError('goal') { Maven.command(true, ['clean && curl evil']) }
}

println 'vars/*'
test('gitCheckout passes branch, url and credentials') {
    def p = new Pipeline()
    loadStep('gitCheckout', p).call(url: 'https://example.com/r.git', branch: 'dev', credentialsId: 'git')
    Map c = p.call('checkout')
    assert c.branches == [[name: 'dev']]
    assert c.userRemoteConfigs == [[url: 'https://example.com/r.git', credentialsId: 'git']]
    expectError('url') { loadStep('gitCheckout', new Pipeline()).call([:]) }
}
test('mavenBuild skips tests by default and falls back to mvn without wrapper') {
    def p = new Pipeline()
    loadStep('mavenBuild', p).call()
    assert p.commands == ['./mvnw -B -ntp clean package -DskipTests']
    def p2 = new Pipeline(existingFiles: [] as Set)
    loadStep('mavenBuild', p2).call(skipTests: false, goals: ['install'])
    assert p2.commands == ['mvn -B -ntp install']
}
test('mavenUnitTest publishes reports even when tests fail') {
    def p = new Pipeline()
    loadStep('mavenUnitTest', p).call()
    assert p.commands == ['./mvnw -B -ntp test']
    assert p.call('junit').testResults.contains('surefire-reports')
}
test('mavenIntegrationTest skips unit tests through skipUTs') {
    def p = new Pipeline()
    loadStep('mavenIntegrationTest', p).call(profiles: 'tomcat90')
    assert p.commands == ["./mvnw -B -ntp verify -Ptomcat90 '-DskipUTs=true'"]
    def p2 = new Pipeline()
    loadStep('mavenIntegrationTest', p2).call(skipUnitTestsProperty: null)
    assert p2.commands == ['./mvnw -B -ntp verify']
}
test('sonarQubeAnalysis (maven) runs inside withSonarQubeEnv') {
    def p = new Pipeline()
    loadStep('sonarQubeAnalysis', p).call(installationName: 'sonarqube', projectKey: 'demo-app')
    assert p.call('withSonarQubeEnv').installationName == 'sonarqube'
    assert p.commands == ["./mvnw -B -ntp sonar:sonar '-Dsonar.projectKey=demo-app'"]
}
test('sonarQubeAnalysis (cli) uses the configured scanner tool') {
    def p = new Pipeline()
    loadStep('sonarQubeAnalysis', p).call(installationName: 'sonarqube', projectKey: 'web', scanner: 'cli')
    assert p.commands == ["'/opt/tools/sonar-scanner/bin/sonar-scanner' '-Dsonar.projectKey=web'"]
    expectError('installationName') { loadStep('sonarQubeAnalysis', new Pipeline()).call([:]) }
}
test('sonarQubeQualityGate aborts by default and is bounded by a timeout') {
    def p = new Pipeline()
    def gate = loadStep('sonarQubeQualityGate', p).call()
    assert p.call('timeout') == [step: 'timeout', time: 10, unit: 'MINUTES']
    assert p.call('waitForQualityGate').abortPipeline == true
    assert gate.status == 'OK'
}
test('imageTags builds an immutable build-number/sha tag') {
    def p = new Pipeline()
    assert loadStep('imageTags', p).call() == ['42-abc1234', 'latest']
    assert loadStep('imageTags', new Pipeline()).call(version: '1.2.0', extraTags: []) == ['1.2.0-42-abc1234']
}
test('dockerBuild tags every reference and quotes arguments') {
    def p = new Pipeline()
    loadStep('dockerBuild', p).call(image: dockerHub, buildArgs: [APP_VERSION: '1.0'])
    assert p.commands == ["'docker' 'build' '--pull' '-f' 'Dockerfile' '--build-arg' 'APP_VERSION=1.0' " +
                          "'-t' 'exampleuser/demo-app:42-abc1234' '-t' 'exampleuser/demo-app:latest' '.'"]
    expectError('dockerBuild') { loadStep('dockerBuild', new Pipeline()).call(image: dockerHub + [name: 'BAD NAME']) }
}
test('dockerBuild passes null build args by name so Docker reads them from the environment') {
    def p = new Pipeline()
    loadStep('dockerBuild', p).call(image: dockerHub + [tags: ['1']], buildArgs: [API_KEY: null])
    assert p.commands[0].contains("'--build-arg' 'API_KEY' '-t'")
    expectError('build argument') { loadStep('dockerBuild', new Pipeline()).call(image: dockerHub, buildArgs: ['A;B': 'x']) }
}
test('dockerPush to Docker Hub uses --password-stdin without Groovy interpolation') {
    def p = new Pipeline()
    loadStep('dockerPush', p).call(image: dockerHub, credentialsId: 'dockerhub')
    assert p.commands[0] == 'printf "%s" "$REGISTRY_PASSWORD" | docker login --username "$REGISTRY_USERNAME" --password-stdin'
    assert p.commands.contains("docker push 'exampleuser/demo-app:42-abc1234'")
    assert p.commands.contains("docker push 'exampleuser/demo-app:latest'")
    assert p.commands.last() == 'docker logout || true'
    expectError('credentialsId') { loadStep('dockerPush', new Pipeline()).call(image: dockerHub) }
}
test('dockerPush to ECR logs in with the AWS CLI') {
    def p = new Pipeline()
    loadStep('dockerPush', p).call(image: ecr, awsCredentialsId: 'aws')
    assert p.call('withCredentials').credentials[0].binding == 'aws'
    assert p.commands[0] == "aws ecr get-login-password --region 'eu-west-1' | docker login --username AWS --password-stdin '123456789012.dkr.ecr.eu-west-1.amazonaws.com'"
    assert p.commands[1] == "docker push '123456789012.dkr.ecr.eu-west-1.amazonaws.com/demo-app:42-abc1234'"
}
test('dockerCleanup never fails') {
    def p = new Pipeline()
    loadStep('dockerCleanup', p).call(image: dockerHub)
    assert p.commands == ["docker image rm 'exampleuser/demo-app:42-abc1234' 'exampleuser/demo-app:latest' || true"]
    loadStep('dockerCleanup', new Pipeline()).call(image: [name: 'x'])
}
test('trivyScan fails the build on findings and still archives reports') {
    def p = new Pipeline(statusByCommand: ['--exit-code': 1])
    expectError('Trivy found HIGH,CRITICAL issues') {
        loadStep('trivyScan', p).call(type: 'image', target: 'exampleuser/demo-app:42-abc1234')
    }
    assert p.commands.any { it.contains("'trivy' 'image' '--severity' 'HIGH,CRITICAL' '--no-progress' '--scanners' 'vuln,secret' '--ignore-unfixed' '--format' 'table'") }
    assert p.call('archiveArtifacts').artifacts == 'reports/trivy/trivy-image.*'
}
test('trivyScan can report without failing') {
    def p = new Pipeline(statusByCommand: ['--exit-code': 1])
    assert loadStep('trivyScan', p).call(type: 'fs', target: '.', failBuild: false) == 1
    assert p.call('unstable')
    expectError('unsupported type') { loadStep('trivyScan', new Pipeline()).call(type: 'sbom', target: '.') }
}
test('trivyScan config scan does not pass vulnerability-only flags') {
    def p = new Pipeline()
    loadStep('trivyScan', p).call(type: 'config', target: 'k8s')
    assert !p.commands.any { it.contains('--ignore-unfixed') || it.contains('--scanners') }
}
test('owaspDependencyCheck publishes reports and supports an NVD key') {
    def p = new Pipeline(env: [BUILD_NUMBER: '1', NVD_API_KEY: 'masked'])
    loadStep('owaspDependencyCheck', p).call(odcInstallation: 'dependency-check', nvdApiKeyCredentialsId: 'nvd')
    assert p.call('dependencyCheck').additionalArguments.startsWith('--scan . --out reports/dependency-check --format XML --format HTML --failOnCVSS 7')
    assert p.call('dependencyCheck').additionalArguments.endsWith('--nvdApiKey masked')
    assert p.call('dependencyCheckPublisher').pattern == 'reports/dependency-check/dependency-check-report.xml'
    expectError('scanPath') { loadStep('owaspDependencyCheck', new Pipeline()).call(odcInstallation: 'x', scanPath: '../etc') }
}
test('kubernetesDeploy renders the image into a copy, applies and waits') {
    def p = new Pipeline()
    loadStep('kubernetesDeploy', p).call(manifests: 'k8s', namespace: 'demo', deployment: 'demo-app',
                                         image: 'exampleuser/demo-app:42-abc1234',
                                         kubeconfigCredentialsId: 'kubeconfig')
    assert p.call('withCredentials').credentials[0].binding == 'file'
    assert p.commands[0] == "rm -rf .k8s-rendered && mkdir -p .k8s-rendered && cp -R 'k8s' .k8s-rendered/"
    assert p.commands[1].contains("sed -i 's|IMAGE_PLACEHOLDER|exampleuser/demo-app:42-abc1234|g'")
    assert p.commands[2] == 'kubectl apply -n demo --recursive -f .k8s-rendered'
    assert p.commands[3] == 'kubectl rollout status -n demo deployment/demo-app --timeout=180s'
}
test('kubernetesDeploy rejects unsafe images and paths') {
    Map base = [manifests: 'k8s', namespace: 'demo', deployment: 'demo-app']
    expectError('invalid image') { loadStep('kubernetesDeploy', new Pipeline()).call(base + [image: 'x|y; rm -rf /']) }
    expectError('manifests') { loadStep('kubernetesDeploy', new Pipeline()).call(base + [manifests: '../secrets', image: 'x/y:1']) }
}
test('kubernetesDeploy rolls back a failed rollout') {
    def p = new Pipeline(statusByCommand: ['rollout status': 1])
    expectError('rolled back') {
        loadStep('kubernetesDeploy', p).call(manifests: 'k8s', namespace: 'demo', deployment: 'demo-app', image: 'x/y:1')
    }
    assert p.commands.any { it == 'kubectl rollout undo -n demo deployment/demo-app || true' }
}

println "\n${passed} passed, ${failures.size()} failed"
if (failures) {
    System.exit(1)
}
