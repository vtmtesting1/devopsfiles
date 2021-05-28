/* (c) Walgreen Co. All rights reserved.*/
def call(body) {
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	def label = "gradle-build-${UUID.randomUUID().toString()}"
	podTemplate(
		label: label,
		name: label,
		imagePullSecrets: ['nonprodregistry'],
		containers: [
			containerTemplate(
				name: label,
				image: 'nonprodregistry.azurecr.io/baseimg/wag-dotcom-build-zulujdk12-nodejs6-bionic:v3',
				command: 'cat',
				ttyEnabled: true,
				alwaysPullImage: true
			)
		],
		volumes: [
			hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')
		]
	){
	timeout(60) {
		node(label) {
			ws ('/home/jenkins/agent/workspace/build'){
				container(label) {
					def application = 'dotcom';
					def module = config.MicroserviceName;
					def tempImageTag = "nonprodregistry.azurecr.io/digital/temp/qa3/${module}";
					def imageTag = "nonprodregistry.azurecr.io/digital/dotcom/qa3/${module}:${env.BUILD_NUMBER}";
					properties([
						parameters([
							string(defaultValue: '${ parameters.FeatureBranch }', description: 'Feature Branch Name', name: 'FeatureBranch')
						]),
						[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
						disableConcurrentBuilds()
					])
					stage('Clone') {
						dir(config.MicroserviceName) {
							try {
								def scmVars = checkout([$class: 'GitSCM', branches: [[name: params.FeatureBranch]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'wag_git_creds', url: config.scmurl]]])
							} catch (exc) {
								currentBuild.result = "FAILURE"
								throw exc
							} finally {
							}
						}
					}
					stage('Unit Test - Java') {
						dir(config.MicroserviceName) {
							try {
								sh 'gradle test -i'
								if(config.cdcConsumer != '' && config.cdcConsumer == true) {
									sh 'gradle pactPublish -Dpactbrokerurl=http://pact-dev.walgreens.com'
								}
							} catch (exc) {
								currentBuild.result = "FAILURE"
								if(config.TeamDL) {
									mail body: "${config.MicroserviceName} - Unit test java error is here: ${env.BUILD_URL}" ,
									from: 'digital-ms-devops@walgreens.com',
									replyTo: 'digital-ms-devops@walgreens.com',
									subject: "${config.MicroserviceName} - Unit test java failed #${env.BUILD_NUMBER}",
									to: "${config.TeamDL}"
								}
								throw exc
							} finally {
								jacoco()
								publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'build/reports/tests/test', reportFiles: 'index.html', reportName: 'Report - Java Unit Test', reportTitles: 'Report - Java Unit Test'])
							}
						}
					}
					stage('SonarQube Analysis - Java') {
						dir(config.MicroserviceName) {
							try {
								withSonarQubeEnv('sonarqube77-dev') {
								sh 'gradle sonarqube -x test -Dsonar.host.url=http://172.17.65.15 -Dsonar.verbose=false'
								}
                                sleep(10)
								def gate = waitForQualityGate()
								if (gate.status != 'OK') {
									error "Pipeline aborted due to quality gate failure: ${gate.status}"
								}

							} catch (exc) {
								currentBuild.result = "FAILURE"
								if(config.TeamDL) {
									mail body: "${config.MicroserviceName} - SonarQube analysis java error is here: ${env.BUILD_URL}" ,
									from: 'digital-ms-devops@walgreens.com',
									replyTo: 'digital-ms-devops@walgreens.com',
									subject: "${config.MicroserviceName} - SonarQube analysis java failed #${env.BUILD_NUMBER}",
									to: "${config.TeamDL}"
								}
								throw exc
							} finally {
							}
						}
					}
					stage('Build - Java') {
						dir(config.MicroserviceName) {
							try {
								sh 'gradle -b build.gradle build -x test'
							} catch (exc) {
								currentBuild.result = "FAILURE"
								if(config.TeamDL) {
									mail body: "${config.MicroserviceName} - Build java error is here: ${env.BUILD_URL}" ,
									from: 'digital-ms-devops@walgreens.com',
									replyTo: 'digital-ms-devops@walgreens.com',
									subject: "${config.MicroserviceName} - Build java failed #${env.BUILD_NUMBER}",
									to: "${config.TeamDL}"
								}
								throw exc
							} finally {
							}
						}
					}
					if(config.cdcProvider != '' && config.cdcProvider == true) {
						stage ('CDC - Verify Provider') {
							dir(config.MicroserviceName) {
								try {
									sh 'gradle pactVerify -Dpactbrokerurl=http://pact-dev.walgreens.com'
								} catch (exc) {
									currentBuild.result = "FAILURE"
									if(config.TeamDL) {
										mail body: "${config.MicroserviceName} - CDC verify provide error is here: ${env.BUILD_URL}" ,
										from: 'digital-ms-devops@walgreens.com',
										replyTo: 'digital-ms-devops@walgreens.com',
										subject: "${config.MicroserviceName} - CDC verify provide failed #${env.BUILD_NUMBER}",
										to: "${config.TeamDL}"
									}
									throw exc
								} finally {
								}
							}
						}
					}
					stage('Docker Build') {
						dir(config.MicroserviceName) {
							try {
								sh 'gradle -b build.gradle createDockerfile'
								withCredentials([usernamePassword(credentialsId: 'Docker_Cred', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
									sh "docker login -u $USERNAME -p $PASSWORD nonprodregistry.azurecr.io"
								}
								withCredentials([usernamePassword(credentialsId: 'acr_prod', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
									sh "docker login -u $USERNAME -p $PASSWORD wagdigital.azurecr.io"
								}
								withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
									sh "docker pull wagdigital.azurecr.io/baseimg/zulu-openjdk_8u181_ubuntu18.04:v1"
								}
								withDockerRegistry([credentialsId: 'Docker_Cred', url: 'https://nonprodregistry.azurecr.io']) {
									sh "sudo docker build --no-cache -t ${module} ."
									//sh "docker tag ${module} '${tempImageTag}'"
									//sh "docker push '${tempImageTag}'"
								}
							} catch (exc) {
								currentBuild.result = "FAILURE"
								if(config.TeamDL) {
									mail body: "${config.MicroserviceName} - Docker build error is here: ${env.BUILD_URL}" ,
									from: 'digital-ms-devops@walgreens.com',
									replyTo: 'digital-ms-devops@walgreens.com',
									subject: "${config.MicroserviceName} - Docker build failed #${env.BUILD_NUMBER}",
									to: "${config.TeamDL}"
								}
								throw exc
							} finally {
							}
						}
					}

					stage('Push to ACR') {
						dir(config.MicroserviceName) {
							try {
								sh "docker tag ${module} '${imageTag}'"
								withDockerRegistry([credentialsId: 'Docker_Cred', url: 'https://nonprodregistry.azurecr.io']) {
									sh "docker push '${imageTag}'"
								}
							} catch (exc) {
								currentBuild.result = "FAILURE"
								if(config.TeamDL) {
									mail body: "${config.MicroserviceName} - Push to ACR error is here: ${env.BUILD_URL}" ,
									from: 'digital-ms-devops@walgreens.com',
									replyTo: 'digital-ms-devops@walgreens.com',
									subject: "${config.MicroserviceName} - Push to ACR failed #${env.BUILD_NUMBER}",
									to: "${config.TeamDL}"
								}
								throw exc
							} finally {
								withDockerRegistry([credentialsId: 'Docker_Cred', url: 'https://nonprodregistry.azurecr.io']) {
									sh "docker rmi '${imageTag}' -f"
									sh "docker rmi '${module}' -f"
								}
							}
						}
					}
				}
			}
		}
	}
	}
}

