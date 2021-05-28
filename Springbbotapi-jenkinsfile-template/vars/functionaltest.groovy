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
        imagePullSecrets: ['prodregistry'],
		containers: [
			containerTemplate(
                name: label,
                image: 'wagdigital.azurecr.io/baseimg/wag-dotcom-functional-test-zulu12-nodejs6-bionic:v2',
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
                stage('Get Image Tag') {
                   BuildTag = sh(returnStdout: true, script: """
                            curl "http://dgtljenkins-acs.walgreens.com:8080/view/Microservices/job/${config.MicroserviceName}/job/${config.MicroserviceName}-build/lastSuccessfulBuild/buildNumber"
			    """).trim()
                          sh "echo '${BuildTag}'" 
                      createDynatraceDeploymentEvent(customProperties: [[key: 'Team Name', value: config.TeamName], [key: 'Service Name', value: config.MicroserviceName], [key: 'Dev Manager', value: config.TeamManager], [key: 'Team Email', value: config.TeamEmail], [key: 'Deployment ImageTag', value: "${BuildTag}"] ], envId: 'dynatrace', 
                      tagMatchRules: [[meTypes: [[meType: 'SERVICE']], tags: [[context: 'KUBERNETES', key: 'app', value: config.MicroserviceName], [context: 'KUBERNETES', key: 'environment', value: 'QA']]]])
                     {
                       stage ('Dynatrace Event API') {
                       sh 'echo Dynatrace API'
                     }
                   }
                }
                stage('Clone') {
                    try {
                        dir(config.MicroserviceName) {
                            git branch: 'development', credentialsId: 'wag_git_creds', url: config.scmurl
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
                                    sh 'gradle apifunctionaltest -DtestEnv=qa -Dintegrationurl=https://m-qa2.walgreens.com' 
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
                                    sh 'gradle mwsApiFunctionalTest -DtestEnv=qa -Dintegrationurl=https://m-qa2.walgreens.com --debug'
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

