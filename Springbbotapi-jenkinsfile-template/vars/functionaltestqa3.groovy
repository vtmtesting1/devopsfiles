/* (c) Walgreen Co. All rights reserved.*/
def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def label = "functional-test-${UUID.randomUUID().toString()}"
	podTemplate(
		label: label,
		name: label,
        imagePullSecrets: ['nonprodregistry'],
		containers: [
			containerTemplate(
                name: label,
                image: 'nonprodregistry.azurecr.io/baseimg/wag-dotcom-functional-test-zulu12-nodejs6-bionic:v2',
                command: 'cat',
                ttyEnabled: true,
                alwaysPullImage: true
            )
		],
		volumes: [
			hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')
		]
	){
        properties([
            [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
            disableConcurrentBuilds()
        ])
        timeout(120) {
        node(label) {
	        container(label) {
                stage('Clone') {
                    try {
                        dir(config.MicroserviceName) {
                            FeatureBranch = sh(returnStdout: true, script: """
                            curl -u admin:776b3fa981c61046348a178df3074d55 "http://172.17.65.9:8080/view/Microservices/job/${config.MicroserviceName}/job/${config.MicroserviceName}-build-qa3/lastSuccessfulBuild/api/xml?xpath=.//action/parameter/value" | awk -F'>' '{print \$2}' | awk -F'<' '{print \$1}'
                            """).trim()
                            echo FeatureBranch
                            def scmVars = checkout([$class: 'GitSCM', branches: [[name: FeatureBranch ]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'wag_git_creds', url: config.scmurl]]])
                        }
                    } catch (exc) {
                        currentBuild.result = "FAILURE"
                        throw exc
                    } finally {

                    }
                }
                stage ('API Functional Test') {
                    if(config.apiFunctionalTest == true) {
                        dir(config.MicroserviceName) {
                            try {
                                retry(3) {
                                    sh 'gradle apifunctionaltest -DtestEnv=dev -Dintegrationurl=https://dotcom-acs-qa3.walgreens.com'                                 
                                }
                            } catch (exc) {
                                currentBuild.result = "FAILURE"
                                throw exc
                            } finally {
                                publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'build/reports/tests/functional-api', reportFiles: 'index.html', reportName: 'Functional Test Results', reportTitles: 'FunctionalTest'])
                            }
                        }
                    }
                    if(config.mwsApiFunctionalTest == true) {
                        dir(config.MicroserviceName) {
                            try {
                                retry(3) {
                                    sh 'gradle mwsApiFunctionalTest -DtestEnv=dev -Dintegrationurl=https://dotcom-acs-qa3.walgreens.com'
                                }
                            } catch (exc) {
                                currentBuild.result = "FAILURE"
                                throw exc
                            } finally {
                                publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'build/reports/tests/functional-api', reportFiles: '*.html', reportName: 'Functional Test Results', reportTitles: 'FunctionalTest'])
                            }
                        }
                    }
                    
                }
            }
        }
    }
    }
}

