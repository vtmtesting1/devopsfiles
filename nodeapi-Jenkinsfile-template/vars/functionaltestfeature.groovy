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
				image: 'nonprodregistry.azurecr.io/baseimg/wag-dotcom-functional-test-zulu12-nodejs12-bionic:v1',
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
			hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock'),
			hostPathVolume(hostPath: '/dev/shm', mountPath: '/dev/shm')
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
							curl "http://172.17.65.9:8080/view/Microservices/job/${config.MicroserviceName}/job/${config.MicroserviceName}-buildfeature/lastSuccessfulBuild/api/xml?xpath=.//action/parameter\\[1\\]/value" | awk -F'>' '{print \$2}' | awk -F'<' '{print \$1}'
							""").trim()
							echo FeatureBranch
							def scmVars = checkout([$class: 'GitSCM', branches: [[name: FeatureBranch ]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'wag_git_creds', url: config.scmurl]]])
							sh '''
								npm config set @wba:registry=https://wagwiki.wba.com/artifactory/api/npm/digital-npm/
								npm config set //wagwiki.wba.com/artifactory/api/npm/digital-npm/:_authToken=eyJ2ZXIiOiIyIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYiLCJraWQiOiI1WkhKUjVaMDgtQkd1emE0RW9hNUdieXdBdDgzNm14SlJFbS1RMENZUU9vIn0.eyJzdWIiOiJqZnJ0QDAxY21rcHJlNHIyamQwMTl4NWMyYWQwYjhyXC91c2Vyc1wvZWNvbW0taW50ZXJuYWwtcm8iLCJzY3AiOiJtZW1iZXItb2YtZ3JvdXBzOiogYXBpOioiLCJhdWQiOiJqZnJ0QDAxY21rcHJlNHIyamQwMTl4NWMyYWQwYjhyIiwiaXNzIjoiamZydEAwMWNta3ByZTRyMmpkMDE5eDVjMmFkMGI4ciIsImlhdCI6MTU2NDA0NTQyNiwianRpIjoiNTVmMDg0ZmQtYzg2NS00NTkwLTkzYjgtNDVlYTU2ZjNhZDkzIn0.D7bwdCg24SSCZy55oAo5wiwFYgetvdnID6_F8qQ4GLzbgRFzfsG2-ijZ9jjx4L2JJzhriGkYVIo2LAWJY4aTl-sW02MuoOhnJBeujTv6lpb2eGh2yuyqEQTN1FKpzUW1W5Nb8XtVoLmo5c1O2A0bGZAmmgJNLW6-IF0BTAIPNn_P96iETLyMeR2zzSAo5OYfRxsn9-u70Ikr3BZuktQCJi_sHyRbN2dEnTF3jI13BlOOZNjJtecCHAZ6U5hp1mu-wZqQ79D0tvusThqE63jwYV3K673cJ4TTFjY2_RWsDUa3Mow3yepk-m1VX0FPKJq5iwqK97JdajE2JS_YBQNW4A
							'''
							sh 'npm install'
						} catch (exc) {
							currentBuild.result = "FAILURE"
							throw exc
						} finally {
						}
					}
				}
				stage ('API Functional Test') {
					if(config.apiFunctionalTest == true) {
						dir(config.MicroserviceName) {
							try {
								def functionalTestHost = 'https://m-int1.walgreens.com'
								if (config.functionalTestHost != '') {
									functionalTestHost = config.functionalTestHost
								}
                                retry(3) {
								   	sh "ENV_FUNCTIONAL_TEST_URL=${functionalTestHost} npm run apifunctional-test"
                                }
							} catch (exc) {
								currentBuild.result = "FAILURE"
								throw exc
							} finally {
								publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'test/functional/reports/json', reportFiles: 'index.html', reportName: 'Functional Test Results', reportTitles: 'FunctionalTest'])
							}
						}
					}
				}
			}
		}
	}
	}
}

