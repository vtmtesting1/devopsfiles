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
        imagePullSecrets: ['nonprodregistry'],
		containers: [
			containerTemplate(
                name: label,
                image: 'nonprodregistry.azurecr.io/baseimg/wag-dotcom-build-zulu10-nodejs8-bionic:v1',
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
                    dir(config.MicroserviceName) {
						try {
							FeatureBranch = sh(returnStdout: true, script: """
							curl -u admin:776b3fa981c61046348a178df3074d55 "http://172.17.65.9:8080/view/Microservices/job/${config.MicroserviceName}/job/${config.MicroserviceName}-buildfeature/lastSuccessfulBuild/api/xml?xpath=.//action/parameter/value" | awk -F'>' '{print \$2}' | awk -F'<' '{print \$1}'
							""").trim()
							echo FeatureBranch
							def scmVars = checkout([$class: 'GitSCM', branches: [[name: FeatureBranch ]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'wag_git_creds', url: config.scmurl]]])
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
                                npm install -g artillery@1.6.0-25 && npm install csv-array'''
                            sh "artillery run -k -e dev -o test/load/report.json test/load/${config.MicroserviceName}-load-test.yml"
                            sh "artillery report -o test/load/index.html test/load/report.json"
                        } catch (exc) {
                            // currentBuild.result = "FAILURE"
                            // throw exc
                        } finally {
                            publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'test/load', reportFiles: 'index.html', reportName: 'Load Test Result', reportTitles: 'Load Test Report'])
                        }
                    }
                }
            }
        }
    }
    }
}

