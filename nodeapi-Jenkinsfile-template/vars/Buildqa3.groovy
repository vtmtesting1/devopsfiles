/* (c) Walgreen Co. All rights reserved.*/
def call(body) {
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	def label = "nodejs-build-${UUID.randomUUID().toString()}"
	podTemplate(
		label: label,
		name: label,
		imagePullSecrets: ['nonprodregistry'],
        envVars: [
            envVar(key: 'SONAR_SCANNER_MIRROR', value: 'https://repo1.maven.org/maven2/org/sonarsource/scanner/cli/sonar-scanner-cli/4.0.0.1744/'),
            envVar(key: 'SONAR_SCANNER_VERSION', value: '4.0.0.1744')
        ],
		containers: [
			containerTemplate(
				name: label,
				image: 'nonprodregistry.azurecr.io/baseimg/wag-dotcom-build-zulu12-nodejs12-bionic:v1',
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
				string(defaultValue: '${ parameters.FeatureBranch }', description: 'Feature Branch Name', name: 'FeatureBranch'),
                booleanParam(defaultValue: false, description: 'Select to Publish to Artifactory', name: 'NodePublish')
			]),
			[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
			disableConcurrentBuilds()
		])
		timeout(120) {
			node(label) {
				container(label) {
					def application = 'dotcom';
					def module = config.MicroserviceName;
					def imageTag = "nonprodregistry.azurecr.io/digital/${application}/qa3/${module}:${env.BUILD_NUMBER}";
					def ScanTime = new Date()
					stage('Clone') {
						try {
							dir('scripts') {
								git branch: 'master', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/digdev/scripts.git'
							}
							if(config.jwtnode != '' && config.jwtnode == true) {
								dir('wag-cs-jwtnode') {
									git branch: 'feature/jwtV7', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/ecomm/wag-cs-jwtnode.git'
								}
							}
							if(config.voltagenode != '' && config.voltagenode == true) {
								dir('wag-cs-voltagenode') {
									git branch: 'feature/voltagev2', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/ecomm/wag-cs-voltagenode.git'
								}
							}
							dir(config.MicroserviceName) {
								git branch: params.FeatureBranch, credentialsId: 'wag_git_creds', url: config.scmurl
								if(params.NodePublish != true) {
									sh '''
										npm config set @wba:registry=https://wagwiki.wba.com/artifactory/api/npm/digital-npm/
										npm config set //wagwiki.wba.com/artifactory/api/npm/digital-npm/:_authToken=eyJ2ZXIiOiIyIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYiLCJraWQiOiI1WkhKUjVaMDgtQkd1emE0RW9hNUdieXdBdDgzNm14SlJFbS1RMENZUU9vIn0.eyJzdWIiOiJqZnJ0QDAxY21rcHJlNHIyamQwMTl4NWMyYWQwYjhyXC91c2Vyc1wvZWNvbW0taW50ZXJuYWwtcm8iLCJzY3AiOiJtZW1iZXItb2YtZ3JvdXBzOiogYXBpOioiLCJhdWQiOiJqZnJ0QDAxY21rcHJlNHIyamQwMTl4NWMyYWQwYjhyIiwiaXNzIjoiamZydEAwMWNta3ByZTRyMmpkMDE5eDVjMmFkMGI4ciIsImlhdCI6MTU2NDA0NTQyNiwianRpIjoiNTVmMDg0ZmQtYzg2NS00NTkwLTkzYjgtNDVlYTU2ZjNhZDkzIn0.D7bwdCg24SSCZy55oAo5wiwFYgetvdnID6_F8qQ4GLzbgRFzfsG2-ijZ9jjx4L2JJzhriGkYVIo2LAWJY4aTl-sW02MuoOhnJBeujTv6lpb2eGh2yuyqEQTN1FKpzUW1W5Nb8XtVoLmo5c1O2A0bGZAmmgJNLW6-IF0BTAIPNn_P96iETLyMeR2zzSAo5OYfRxsn9-u70Ikr3BZuktQCJi_sHyRbN2dEnTF3jI13BlOOZNjJtecCHAZ6U5hp1mu-wZqQ79D0tvusThqE63jwYV3K673cJ4TTFjY2_RWsDUa3Mow3yepk-m1VX0FPKJq5iwqK97JdajE2JS_YBQNW4A
									'''
								}
								if(config.jwtnode != '' && config.jwtnode == true) {
									sh 'npm run install-jwtnode'
								}
								if(config.voltagenode != '' && config.voltagenode == true) {
									sh 'npm run install-voltagenode'
								}
								sh 'npm install'
							}
						} catch (exc) {
							currentBuild.result = "FAILURE"
							throw exc
						} finally {
						}
					}
					stage('Dependency Analysis') {
						dir(config.MicroserviceName) {
							try {
								sh 'npm install -g depcheck@0.9.0'
								sh 'mkdir dependency-analysis-report && depcheck --json > dependency-analysis-report/report.json'
								sh "cd ../scripts && npm install json2html && ENV_APP_DIR_NAME=${config.MicroserviceName} node DependencyAnalysisReport.js"
							} catch (exc) {
								currentBuild.result = "FAILURE"
								if(config.TeamDL) {
									mail body: "${config.MicroserviceName} - Dependency Analysis error is here: ${env.BUILD_URL}" ,
									from: 'digital-ms-devops@walgreens.com',
									replyTo: 'digital-ms-devops@walgreens.com',
									subject: "${config.MicroserviceName} - Dependency Analysis failed #${env.BUILD_NUMBER}",
									to: "${config.TeamDL}"
								}
								throw exc
							} finally {
								publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: './dependency-analysis-report', reportFiles: 'report.html', reportName: 'NPM Dependencies Analysis Report', reportTitles: 'NPM Dependencies Analysis Report'])
							}
						}
					}
					stage('Unit Test') {
						dir(config.MicroserviceName) {
							try {
								sh 'npm run unit-test'
							//sh 'npm run coverage'
								if(config.cdcConsumer != '' && config.cdcConsumer == true) {
									sh 'ENV_PACT_BROKER_URL=http://pact-dev.walgreens.com npm run pact-publish'
								}
							} catch (exc) {
								currentBuild.result = "FAILURE"
								if(config.TeamDL) {
									mail body: "${config.MicroserviceName} - Unit test error is here: ${env.BUILD_URL}" ,
									from: 'digital-ms-devops@walgreens.com',
									replyTo: 'digital-ms-devops@walgreens.com',
									subject: "${config.MicroserviceName} - Unit test failed #${env.BUILD_NUMBER}",
									to: "${config.TeamDL}"
								}
								throw exc
							} finally {
								// sh 'npm run unit-test-report'
								archiveArtifacts allowEmptyArchive: true, artifacts: 'test/unit/reports/json/*.json', defaultExcludes: false, onlyIfSuccessful: true
								publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'test/unit/reports/coverage/lcov-report', reportFiles: 'index.html', reportName: 'Unit Test Coverage', reportTitles: 'Coverage'])
								publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'test/unit/reports/status', reportFiles: '**/index.html', reportName: 'Unit Test Status', reportTitles: 'Status'])
							}
						}
					}
					stage('SonarQube Analysis') {
						dir(config.MicroserviceName) {
							try {
                              	sh 'npm install -D sonarqube-scanner@2.6.0'
								withSonarQubeEnv('sonarqube73-dev') {
									sh 'npm run sonarqube-scanner -- -Dsonar.host.url=http://172.20.7.182:8022/sonarqube -Dsonar.buildbreaker.skip=false'
								}
								def gate = waitForQualityGate()
								if (gate.status != 'OK') {
									error "Pipeline aborted due to quality gate failure: ${gate.status}"
								}
							} catch (exc) {
								currentBuild.result = "FAILURE"
								if(config.TeamDL) {
									mail body: "${config.MicroserviceName} - SonarQube analysis error is here: ${env.BUILD_URL}" ,
									from: 'digital-ms-devops@walgreens.com',
									replyTo: 'digital-ms-devops@walgreens.com',
									subject: "${config.MicroserviceName} - SonarQube analysis failed #${env.BUILD_NUMBER}",
									to: "${config.TeamDL}"
								}
								throw exc
							}
						}
					}
					stage ('Build') {
						dir(config.MicroserviceName) {
							try {
								sh 'npm run build-prod'
							} catch (exc) {
								currentBuild.result = "FAILURE"
								if(config.TeamDL) {
									mail body: "${config.MicroserviceName} - Build error is here: ${env.BUILD_URL}" ,
									from: 'digital-ms-devops@walgreens.com',
									replyTo: 'digital-ms-devops@walgreens.com',
									subject: "${config.MicroserviceName} - Build failed #${env.BUILD_NUMBER}",
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
									sh '''ENV_SERVER=local npm run start &
										sleep 5s
										ENV_PACT_BROKER_URL=http://pact-dev.walgreens.com npm run pact-verify-provider'''
								} catch (exc) {
									currentBuild.result = "FAILURE"
									if(config.TeamDL) {
										mail body: "${config.MicroserviceName} - CDC verify provider error is here: ${env.BUILD_URL}" ,
										from: 'digital-ms-devops@walgreens.com',
										replyTo: 'digital-ms-devops@walgreens.com',
										subject: "${config.MicroserviceName} - CDC verify provider failed #${env.BUILD_NUMBER}",
										to: "${config.TeamDL}"
									}
									throw exc
								} finally {
								}
							}
						}
					}
					if (params.NodePublish != true) {
						stage('Docker Build') {
							dir(config.MicroserviceName) {
								try {
									sh 'npm run pack'
									withCredentials([usernamePassword(credentialsId: 'Docker_Cred', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
										sh "docker login -u $USERNAME -p $PASSWORD nonprodregistry.azurecr.io"
									}
									withCredentials([usernamePassword(credentialsId: 'acr_prod', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
										sh "docker login -u $USERNAME -p $PASSWORD wagdigital.azurecr.io"
									}
                                    withCredentials([usernamePassword(credentialsId: 'baseimg_acr_cred', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
										sh "docker login -u $USERNAME -p $PASSWORD digitalnonprodregistry.azurecr.io"
									}
									withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
										sh "docker pull wagdigital.azurecr.io/baseimg/nodejs6_microservices_bionic:v2"
									}
									withDockerRegistry([credentialsId: 'Docker_Cred', url: 'https://nonprodregistry.azurecr.io']) {
										sh "sudo docker build --no-cache --pull -f deployment/Dockerfile -t ${module} ."
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
					}
					if(config.publish != '' && config.publish == true) {
						stage('NPM JFrog Publish'){
							dir(config.MicroserviceName) {
								try {
									sh '''#!/bin/bash +x
										echo -e "\n_auth = ZGlnaXRhbC1tYXJrZXRpbmctc2VydmljZTptL09nN2hnS2ZFTW0=\nemail = digital-ms-devops@walgreens.com\nalways-auth = true" >> /home/jenkins/.npmrc
										chmod 600 /home/jenkins/.npmrc
									'''
									sh 'npm publish --registry https://wagwiki.wba.com/artifactory/api/npm/digital-npm/'
								} catch (exc) {
									currentBuild.result = "FAILURE"
									if(config.TeamDL) {
										mail body: "${config.MicroserviceName} - Publish failure: ${env.BUILD_URL}" ,
										from: 'digital-ms-devops@walgreens.com',
										replyTo: 'digital-ms-devops@walgreens.com',
										subject: "${config.MicroserviceName} - Unit test failed #${env.BUILD_NUMBER}",
										to: "${config.TeamDL}"
									}
									throw exc
								} 
							}
						}
					}
					if (params.NodePublish != true) {
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
					if(config.veracodeAnalysis != '' && config.veracodeAnalysis == true) {
						stage ('Veracode Analysis') {
							dir(config.MicroserviceName) {
								try {
									//sh 'npm run pack-src ;  pwd ; ls ; du -sh '
									sh "zip -r ${config.MicroserviceName}.zip src/ package.json ; pwd ; ls ; du -sh ${config.MicroserviceName}.zip"
									//sleep 500
									//veracode applicationName: "walgreens.com-${config.MicroserviceName}", copyRemoteFiles: true, canFailJob: true, criticality: 'High', debug: true, fileNamePattern: '', replacementPattern: '', sandboxName: '', scanExcludesPattern: '', scanIncludesPattern: "${config.MicroserviceName}.zip", scanName: "walgreens-${config.MicroserviceName}-${Time}-BuildNumber-${env.BUILD_NUMBER}", teams: '', timeout: 60, uploadExcludesPattern: '', uploadIncludesPattern: "${config.MicroserviceName}.zip", vid: '', vkey: '', vpassword: 'yAFaEvKOz|7+3#*=c*p"cNr84', vuser: 'ecomjenkins2'

									veracode applicationName: "walgreens.com-${config.MicroserviceName}", copyRemoteFiles: true, canFailJob: true, criticality: 'High', debug: true, fileNamePattern: '', replacementPattern: '', sandboxName: '', scanExcludesPattern: '', scanIncludesPattern: "${config.MicroserviceName}.zip", scanName: "walgreens-${config.MicroserviceName}-${ScanTime}-BuildNumber-${env.BUILD_NUMBER}", teams: '', timeout: 60, uploadExcludesPattern: '', uploadIncludesPattern: "${config.MicroserviceName}.zip", useIDkey: true, vid: 'd0bbe2cdc52ff509c6f243f916c29047', vkey: '8bc1c3f6cee9281bc1b3880b57d43a1202cd730a177822e810ba7012aea949e70492f3a7f18638965244ebaf46364994990e1a402191502c7f7ec35357d60442', vpassword: '', vuser: ''

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
				}
			}
		}
	}
}

