 /* (c) Walgreen Co. All rights reserved.*/
def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def label = "load-test-${UUID.randomUUID().toString()}"
    podTemplate(
        label: label,
        name: label,
        imagePullSecrets: ['prodregistry'],
        containers: [
            containerTemplate(
                name: label,
                image: 'wagdigital.azurecr.io/baseimg/wag-dotcom-build-zulu12-nodejs12-bionic:v1',
                command: 'cat',
                ttyEnabled: true,
                alwaysPullImage: true,
                resourceRequestMemory: '2Gi',
                resourceLimitMemory: '6Gi',
                resourceRequestCpu: '500m',
                resourceLimitCpu: '1000m'
            )
        ],
        volumes: [
            hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')
        ]
    ){
        properties([
            parameters([
                booleanParam(defaultValue: false, description: 'Skip load test', name: 'skipLoadTest')
            ]),
            [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
            disableConcurrentBuilds()
        ])
        // timeout(120) {
            node(label) {
                container(label) {
                    if (params.skipLoadTest != true) {
                        stage('Clone') {
                            dir(config.MicroserviceName) {
                                try {
                                    git branch: 'development', credentialsId: 'wag_git_creds', url: config.scmurl
                                } catch (exc) {
                                    currentBuild.result = "FAILURE"
                                    throw exc
                                } finally {
                                }
                            }
                        }
                        stage ('Load Test') {
                            dir(config.MicroserviceName) {
                                try {
                                    sh '''npm config set user 0
                                        npm config set unsafe-perm true
                                        npm run load-test -- -e qa'''
                                    sh 'npm run load-test-report'
                                } catch (exc) {
                                    // currentBuild.result = "FAILURE"
                                    // throw exc
                                } finally {
                                    publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'test/load/reports', reportFiles: 'index.html', reportName: 'Load Test Result', reportTitles: 'Load Test Report'])
                                }
                            }
                        }
                    } else {
                        echo '****** Skipped Load Test ******'
                    }
                }
            }
        // }
    }
}

