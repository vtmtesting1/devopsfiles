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
					def tempImageTag = "nonprodregistry.azurecr.io/digital/temp/${module}";
                  def url = config.scmurl;
                  def RepoName = sh(script: "echo $url |  grep -o -P '(?<=https://).*(?=.git)'", returnStdout: true).trim()
                  	def ScanTime = new Date();
					def imageTag = "nonprodregistry.azurecr.io/digital/dotcom/${module}:${env.BUILD_NUMBER}";
					properties([
						parameters([
							string(defaultValue: '${ parameters.FeatureBranch }', description: 'Feature Branch Name', name: 'FeatureBranch')
						]),
						[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
						disableConcurrentBuilds()
					])
					stage('Clone') {
						dir(config.MicroserviceName) {
                        /*    def timeStamp = (Calendar.getInstance().getTime().format('H',TimeZone.getTimeZone('CST'))).toInteger();
                            sh "echo ${timeStamp}"
                            if (timeStamp >= 11 && timeStamp <= 17) {
                                mail body: "M-INT1 Build Triggered - ${config.MicroserviceName}",
                                from: 'digital-ms-devops@walgreens.com',
                                replyTo: 'digital-ms-devops@walgreens.com',
                                subject: "M-INT1 Build Triggered - ${config.MicroserviceName}",
                                to: "lohes.gopalsamy@walgreens.com sivakumar.arunachalam@walgreens.com balasubramanian.mahadevan@walgreens.com abhilash.cheenepalle@walgreens.com"
                            } */
							try {
								def scmVars = checkout([$class: 'GitSCM', branches: [[name: params.FeatureBranch]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'wag_git_creds', url: config.scmurl]]])
                              def branch = "${params.FeatureBranch}"
							  sh("git checkout ${branch}")
                              withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'wag_git_creds', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                              sh """if grep -q http://wagwiki "build.gradle"; then
							 
                             git config --global user.email 'digital-ms-devops@walgreens.com'
                             git config --global user.name 'digital-ms-devops'
							 sed -i \'s,http://wagwiki.walgreens.com,https://wagwiki.wba.com,g\' build.gradle
                             sed -i \'s,http://ecomm-internal-ro:Tk6x1zaFS0csRD8MYT@wagwiki.walgreens.com,https://ecomm-internal-ro:Tk6x1zaFS0csRD8MYT@wagwiki.wba.com,g\' build.gradle
                             
								git add build.gradle
							git commit -m "wagwiki url changes"
							git push https://${USERNAME}:${PASSWORD}@${RepoName}  """ + branch + """
                            else
                            echo "HTTP not found"
							fi"""
                              }
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
									sh 'gradle pactVerify --info -Dpactbrokerurl=http://pact-dev.walgreens.com'
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
					 if(config.veracodeAnalysis != '' && config.veracodeAnalysis == true) {
						stage ('Veracode Analysis') {
							dir(config.MicroserviceName) {
								try {
                                    //sh 'sleep 60'
                                    s//h "export https_proxy='https://wagcorppac.walgreens.com:8080' http_proxy='http://wagcorppac.walgreens.com:8080'" 
                                  	sh "cd build/libs/ && ls"                                   
									sh "cp build/libs/${config.MicroserviceName}.jar /home/jenkins/agent/workspace/build/pricing/"
                                    
                             //     withCredentials([usernamePassword(credentialsId: 'veracodecreds', passwordVariable: 'anjanpassowrd', usernameVariable: 'anjanusername')]) {
                                    veracode applicationName: "walgreens.com-${config.MicroserviceName}", copyRemoteFiles: true, canFailJob: true, criticality: 'High', debug: true, fileNamePattern: '', replacementPattern: '', sandboxName: '', scanExcludesPattern: '', scanIncludesPattern: "${config.MicroserviceName}.jar", scanName: "walgreens-${config.MicroserviceName}-${ScanTime}-BuildNumber-${env.BUILD_NUMBER}", teams: '', timeout: 60, uploadExcludesPattern: '', uploadIncludesPattern: "${config.MicroserviceName}.jar", useIDkey: true, vid: '3211e33989bc12c273038b8fdb11d2c5', vkey: 'a006594cc8c577da971ac8a67953e2d4dbf4764db530db591839ae551f82fa13a58de3f166998b1d0f9fb75c5213178d20ab330f26b6f0ac0cf229abba74b076', vpassword: '', vuser: ''

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
	}


