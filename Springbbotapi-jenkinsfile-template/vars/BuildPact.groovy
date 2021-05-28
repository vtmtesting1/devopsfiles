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
		imagePullSecrets: ['prodregistry'],
		containers: [
			containerTemplate(
				name: label,
				image: 'wagdigital.azurecr.io/baseimg/wag-dotcom-build-zulu10-nodejs6-xenial:v3',
				command: 'cat',
				ttyEnabled: true,
				alwaysPullImage: true
			)
		],
		volumes: [
			hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock'),
			configMapVolume(configMapName: 'pact-provider-config', mountPath: '/deployments/scripts')
		]
	){
	timeout(120) {
		node(label) {
			ws ('/home/jenkins/workspace/build'){
				container(label) {
					def application = 'dotcom';
					def module = config.MicroserviceName;
					def tempImageTag = "wagdigital.azurecr.io/digital/temp/${module}";
					def imageTag = "wagdigital.azurecr.io/digital/dotcom/${module}:${env.BUILD_NUMBER}";
					properties([
						[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
						disableConcurrentBuilds()
					])
					stage('Clone') {
						dir(config.MicroserviceName) {
							try {
								git branch: 'pact-testing', credentialsId: 'wag_git_creds', url: config.scmurl
							} catch (exc) {
								currentBuild.result = "FAILURE"
								throw exc
							} finally {
							}
						}
					}
					// stage('Unit Test - Java') {
					// 	dir(config.MicroserviceName) {
					// 		try {
					// 			sh 'gradle test'
					// 			if(config.cdcConsumer != '' && config.cdcConsumer == true) {
					// 				sh 'gradle pactPublish -Dpactbrokerurl=http://10.217.68.61'
					// 			}
					// 		} catch (exc) {
					// 			currentBuild.result = "FAILURE"
					// 			if(config.TeamDL) {
					// 				mail body: "${config.MicroserviceName} - Unit test java error is here: ${env.BUILD_URL}" ,
					// 				from: 'digital-ms-devops@walgreens.com',
					// 				replyTo: 'digital-ms-devops@walgreens.com',
					// 				subject: "${config.MicroserviceName} - Unit test java failed #${env.BUILD_NUMBER}",
					// 				to: "${config.TeamDL}"
					// 			}
					// 			throw exc
					// 		} finally {
					// 			jacoco()
					// 			publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'build/reports/tests/test', reportFiles: 'index.html', reportName: 'Report - Java Unit Test', reportTitles: 'Report - Java Unit Test'])
					// 		}
					// 	}
					// }
					// stage('SonarQube Analysis - Java') {
					// 	dir(config.MicroserviceName) {
					// 		try {
					// 			withSonarQubeEnv('sonarqube73-acs') {
					// 			sh 'gradle sonarqube -x test -Dsonar.host.url=http://10.217.68.55:9000 -Dsonar.verbose=false --scan'
					// 			}

					// 			def gate = waitForQualityGate()
					// 			if (gate.status != 'OK') {
					// 				error "Pipeline aborted due to quality gate failure: ${gate.status}"
					// 			}
					// 		} catch (exc) {
					// 			currentBuild.result = "FAILURE"
					// 			if(config.TeamDL) {
					// 				mail body: "${config.MicroserviceName} - SonarQube analysis java error is here: ${env.BUILD_URL}" ,
					// 				from: 'digital-ms-devops@walgreens.com',
					// 				replyTo: 'digital-ms-devops@walgreens.com',
					// 				subject: "${config.MicroserviceName} - SonarQube analysis java failed #${env.BUILD_NUMBER}",
					// 				to: "${config.TeamDL}"
					// 			}
					// 			throw exc
					// 		} finally {
					// 		}
					// 	}
					// }
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
							dir('wag-dotcom-secrets') {
								git branch: 'master', credentialsId: 'wag_git_creds', url: 'https://wagwiki.walgreens.com/stash/scm/digdev/wag-dotcom-secrets.git'
							}
							dir(config.MicroserviceName) {
								try {
									sh """mkdir ../pact-provider-scripts
									cp /deployments/scripts/* ../pact-provider-scripts
									chmod +x ../pact-provider-scripts/*.*
									export ENV_CONFIG_FILE_PATH='/deployments/config'
									bash ../pact-provider-scripts/setsecrets.sh ${config.MicroserviceName} springboot '${config.appStartUpScript}'"""
									sh 'gradle pactVerify -Dpactbrokerurl=http://10.217.68.61'
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
									sh 'sleep 60000'
								}
							}
						}
					}
					if(config.veracodeAnalysis != '' && config.veracodeAnalysis == true) {
						stage ('Veracode Analysis') {
							dir(config.MicroserviceName) {
								try {
									//TODO:Add veracode scanning script
								} catch (exc) {
									currentBuild.result = "FAILURE"
									if(config.TeamDL) {
										mail body: "${config.MicroserviceName} - Veracode analysis error is here: ${env.BUILD_URL}" ,
										from: 'digital-ms-devops@walgreens.com',
										replyTo: 'digital-ms-devops@walgreens.com',
										subject: "${config.MicroserviceName} - Veracode analysis failed #${env.BUILD_NUMBER}",
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
								withDockerRegistry([credentialsId: 'Docker_Cred', url: 'https://nonprodregistry.azurecr.io']) {
									sh "docker pull nonprodregistry.azurecr.io/baseimg/wag-dotcom-build-zulu10-nodejs6-bionic:v2"
								}
								withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
									sh "sudo docker build --no-cache -t ${module} ."
									sh "docker tag ${module} '${tempImageTag}'"
									sh "docker push '${tempImageTag}'"
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
					stage('TwistLock Scan') {
					    try {
						    build job: "${config.MicroserviceName}-twistlock-scan"
					    } catch (exc) {

					    } finally {

					    }
				    }
					// stage('Push to ACR') {
					// 	dir(config.MicroserviceName) {
					// 		try {
					// 			sh "docker tag ${module} '${imageTag}'"
					// 			withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
					// 				sh "docker push '${imageTag}'"
					// 			}
					// 		} catch (exc) {
					// 			currentBuild.result = "FAILURE"
					// 			if(config.TeamDL) {
					// 				mail body: "${config.MicroserviceName} - Push to ACR error is here: ${env.BUILD_URL}" ,
					// 				from: 'digital-ms-devops@walgreens.com',
					// 				replyTo: 'digital-ms-devops@walgreens.com',
					// 				subject: "${config.MicroserviceName} - Push to ACR failed #${env.BUILD_NUMBER}",
					// 				to: "${config.TeamDL}"
					// 			}
					// 			throw exc
					// 		} finally {
					// 			withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
					// 				sh "docker rmi '${imageTag}' -f"
					// 				sh "docker rmi '${module}' -f"
					// 			}
					// 		}
					// 	}
					// }
				}
			}
		}
	}
	}
}

