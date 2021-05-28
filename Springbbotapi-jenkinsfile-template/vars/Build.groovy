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
				image: 'wagdigital.azurecr.io/baseimg/wag-dotcom-build-zulujdk12-nodejs6-bionic:v3',
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
			ws ('/home/jenkins/workspace/build'){
				container(label) {
					def application = 'dotcom';
					def module = config.MicroserviceName;
                    def url = config.scmurl;
					def tempImageTag = "wagdigital.azurecr.io/digital/temp/${module}";
					def imageTag = "wagdigital.azurecr.io/digital/dotcom/${module}:${env.BUILD_NUMBER}";
                    def timeStamp = Calendar.getInstance().getTime().format('YYYYMMdd-hhmmss',TimeZone.getTimeZone('CST'));
            		def tagName = "development-${module}-${timeStamp}-Build#${env.BUILD_NUMBER}";
                  	def RepoName = sh(script: "echo $url |  grep -o -P '(?<=https://).*(?=.git)'", returnStdout: true).trim()
                    def ScanTime = new Date()
					properties([
						[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
						disableConcurrentBuilds()
					])
					stage('Clone') {
						dir('scripts') {
								git branch: 'master', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/digdev/scripts.git'
						}
						dir(config.MicroserviceName) {
							try {
								git branch: 'development', credentialsId: 'wag_git_creds', url: config.scmurl
                                  withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'wag_git_creds', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
								  sh("git tag ${tagName}")
                                  sh("git push https://${env.USERNAME}:${env.PASSWORD}@'${RepoName}'.git --tags")
                                  }  
							} catch (exc) {
								currentBuild.result = "FAILURE"
								throw exc
							} finally {
							}
						}
					}
					stage ('Secrets Scan') {
				    try {
	  				  sh "python3 scripts/repo_scanner.py --regex --rules=scripts/regexes.json  --json ${module}"
						 }
	  				 catch(exc) {
				        // currentBuild.result = "FAILURE"
								if(config.TeamDL) {
									mail body: "${config.MicroserviceName} - Secrets Scan Status is here: ${env.BUILD_URL}" ,
									from: 'digital-ms-devops@walgreens.com',
									replyTo: 'digital-ms-devops@walgreens.com',
									subject: "${config.MicroserviceName} - Secret scan for repository failed: #${env.BUILD_NUMBER}",
									to: "${config.TeamDL}"
								}
								// throw exc
				     } finally {
				        publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, includes: '**/repo_scan.html', keepAll: true, reportDir: '.', reportFiles: 'repo_scan.html', reportName: 'Repository Secrets Scan Status', reportTitles: 'Repository Scan status for Secrets'])
				     }
					}
					stage('Unit Test - Java') {
						dir(config.MicroserviceName) {
							try {
								sh 'gradle test'
								if(config.cdcConsumer != '' && config.cdcConsumer == true) {
									sh 'gradle pactPublish -Dpactbrokerurl=http://dgtlpact-acs-qa.walgreens.com'
								}
							} catch (exc) {
								currentBuild.result = "FAILED"
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
								withSonarQubeEnv('sonarqube73-acs') {
                                sh 'gradle sonarqube -x test -Dsonar.host.url=http://dgtlsonar77-acs.walgreens.com:9000 -Dsonar.verbose=false --scan'
								}
                                sleep(10)
								def gate = waitForQualityGate()
								if (gate.status != 'OK') {
									error "Pipeline aborted due to quality gate failure: ${gate.status}"
								}
							} catch (exc) {
								currentBuild.result = "FAILED"
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
									sh 'gradle pactVerify -Dpactbrokerurl=http://dgtlpact-acs-qa.walgreens.com'
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
								withDockerRegistry([credentialsId: 'Docker_Cred', url: 'https://nonprodregistry.azurecr.io']) {
									sh "docker pull nonprodregistry.azurecr.io/baseimg/wag-dotcom-build-zulu10-nodejs6-bionic:v2"
								}
								withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
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
								withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
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
								sh "docker images ${imageTag} --format {{.Size}} > docker-img-size.txt"
								archiveArtifacts allowEmptyArchive: true, artifacts: 'docker-img-size.txt', defaultExcludes: false, onlyIfSuccessful: true
								withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
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
									sh "cp /home/jenkins/workspace/build/${config.MicroserviceName}/build/libs/${config.MicroserviceName}.jar /home/jenkins/workspace/build/${config.MicroserviceName}/"
                                    withCredentials([usernamePassword(credentialsId: 'wag_git_creds', passwordVariable: 'UPD', usernameVariable: 'UDI')]) {
									withCredentials([string(credentialsId: 'SRCCLR_API_TOKEN', variable: 'SRCCLR_API_TOKEN')]) {
                                        sh "git tag -l | xargs git tag -d"
                                     //   sh "git checkout -b development origin/development"                        
										sh "curl -sSL https://download.sourceclear.com/ci.sh | sh" 
                                        sh "git describe --all"
									veracode applicationName: "walgreens.com-${config.MicroserviceName}", copyRemoteFiles: true, criticality: 'VeryHigh', debug: true, fileNamePattern: '', pHost: '172.23.137.193', pPassword: '', pPort: '8080', pUser: '', replacementPattern: '', sandboxName: '', scanExcludesPattern: '', scanIncludesPattern: "${config.MicroserviceName}.jar", scanName: "walgreens-${config.MicroserviceName}-${ScanTime}-BuildNumber-${env.BUILD_NUMBER}", teams: '', uploadExcludesPattern: '', uploadIncludesPattern: "${config.MicroserviceName}.jar", useProxy: true, vid: '3211e33989bc12c273038b8fdb11d2c5', vkey: 'a006594cc8c577da971ac8a67953e2d4dbf4764db530db591839ae551f82fa13a58de3f166998b1d0f9fb75c5213178d20ab330f26b6f0ac0cf229abba74b076'	
                                    									}
								 }
          
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

