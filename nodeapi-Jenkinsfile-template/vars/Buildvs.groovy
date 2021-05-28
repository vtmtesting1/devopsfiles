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
		imagePullSecrets: ['prodregistry'],
		containers: [
			containerTemplate(
				name: label,
				image: 'wagdigital.azurecr.io/baseimg/wag-dotcom-build-zulu10-nodejs6-bionic:v7',
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
				string(defaultValue: '${ parameters.FeatureBranch }', description: 'Feature Branch Name', name: 'FeatureBranch')
			]),
			[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
			disableConcurrentBuilds()
		])
		timeout(120) {
		node(label) {
			container(label) {
				def application = 'dotcom';
				def module = config.MicroserviceName;
				def imageTagVS = "wagdigital.azurecr.io/digital/${application}/${module}-vs:latest";
				stage('Clone') {
					try {
						dir(config.MicroserviceName) {
							git branch: 'development', credentialsId: 'wag_git_creds', url: config.scmurl
							sh '''
								npm config set @wba:registry=https://wagwiki.wba.com/artifactory/api/npm/digital-npm/
								npm config set //wagwiki.wba.com/artifactory/api/npm/digital-npm/:_authToken=eyJ2ZXIiOiIyIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYiLCJraWQiOiI1WkhKUjVaMDgtQkd1emE0RW9hNUdieXdBdDgzNm14SlJFbS1RMENZUU9vIn0.eyJzdWIiOiJqZnJ0QDAxY21rcHJlNHIyamQwMTl4NWMyYWQwYjhyXC91c2Vyc1wvZWNvbW0taW50ZXJuYWwtcm8iLCJzY3AiOiJtZW1iZXItb2YtZ3JvdXBzOiogYXBpOioiLCJhdWQiOiJqZnJ0QDAxY21rcHJlNHIyamQwMTl4NWMyYWQwYjhyIiwiaXNzIjoiamZydEAwMWNta3ByZTRyMmpkMDE5eDVjMmFkMGI4ciIsImlhdCI6MTU2NDA0NTQyNiwianRpIjoiNTVmMDg0ZmQtYzg2NS00NTkwLTkzYjgtNDVlYTU2ZjNhZDkzIn0.D7bwdCg24SSCZy55oAo5wiwFYgetvdnID6_F8qQ4GLzbgRFzfsG2-ijZ9jjx4L2JJzhriGkYVIo2LAWJY4aTl-sW02MuoOhnJBeujTv6lpb2eGh2yuyqEQTN1FKpzUW1W5Nb8XtVoLmo5c1O2A0bGZAmmgJNLW6-IF0BTAIPNn_P96iETLyMeR2zzSAo5OYfRxsn9-u70Ikr3BZuktQCJi_sHyRbN2dEnTF3jI13BlOOZNjJtecCHAZ6U5hp1mu-wZqQ79D0tvusThqE63jwYV3K673cJ4TTFjY2_RWsDUa3Mow3yepk-m1VX0FPKJq5iwqK97JdajE2JS_YBQNW4A
							'''
							sh 'npm install'
						}
					} catch (exc) {
						currentBuild.result = "FAILURE"
						throw exc
					} finally {
					}
				}
				stage('Build VS') {
					dir(config.MicroserviceName) {
						try {
							sh 'npm run pack-vs'
						} catch (exc) {
							currentBuild.result = "FAILURE"
							if(config.TeamDL) {
								mail body: "${config.MicroserviceName} - Build VS error is here: ${env.BUILD_URL}" ,
								from: 'digital-ms-devops@walgreens.com',
								replyTo: 'digital-ms-devops@walgreens.com',
								subject: "${config.MicroserviceName} - Build VS failed #${env.BUILD_NUMBER}",
								to: "${config.TeamDL}"
							}
							throw exc
						} finally {
						}
					}
				}
				stage('Docker Build VS') {
					dir(config.MicroserviceName) {
						try {
							withDockerRegistry([credentialsId: 'Docker_Cred', url: 'https://nonprodregistry.azurecr.io']) {
                                sh "docker pull nonprodregistry.azurecr.io/baseimg/nodejs6_microservices_bionic:v2"
                            }
							withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
								sh "sudo docker build --no-cache -f deployment/Dockerfile-VS -t ${module}-vs ."
								sh "docker tag ${module}-vs '${imageTagVS}'"
								sh "docker push '${imageTagVS}'"
							}
						} catch (exc) {
							currentBuild.result = "FAILURE"
							if(config.TeamDL) {
								mail body: "${config.MicroserviceName} - Docker Build VS error is here: ${env.BUILD_URL}" ,
								from: 'digital-ms-devops@walgreens.com',
								replyTo: 'digital-ms-devops@walgreens.com',
								subject: "${config.MicroserviceName} - Docker Build VS failed #${env.BUILD_NUMBER}",
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

