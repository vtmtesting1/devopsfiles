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
                    envVar(key: 'SONAR_SCANNER_MIRROR', value: 'https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/')
                ],
		containers: [
			containerTemplate(
				name: label,
				image: 'nonprodregistry.azurecr.io/baseimg/wag-dotcom-build-zulu10-nodejs6-bionic:v6',
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
				string(defaultValue: 'test', description: 'Feature Branch Name', name: 'FeatureBranch')
			]),
			[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
			disableConcurrentBuilds()
		])
		timeout(120) {
		node(label) {
			container(label) {
				def application = 'dotcom';
				def module = config.MicroserviceName;
				def tempImageTag = "nonprodregistry.azurecr.io/digital/temp/${module}";
				def imageTag = "nonprodregistry.azurecr.io/digital/${application}/${module}:${env.BUILD_NUMBER}";
				stage('Clone') {
					try {
						dir('wag-common-ui') {
							git branch: 'develop_acs', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/ecomm/wag-common-ui.git'
						}
						if(config.jwtnode != '' && config.jwtnode == true) {
							dir('wag-cs-jwtnode') {
								git branch: 'feature/jwtV7', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/ecomm/wag-cs-jwtnode.git'
							}
						}
						if(config.voltagenode != '' && config.voltagenode == true) {
							dir('wag-cs-voltagenode') {
								git branch: 'feature/voltagenodelibrary', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/ecomm/wag-cs-voltagenode.git'
							}
						}
					} catch (exc) {
						currentBuild.result = "FAILURE"
						throw exc
					} finally {
					}
				}
				stage('npm download') {
                    dir('cartui') {
                    git branch: 'feature/cartui', credentialsId: params.wag_git_creds, url: ' https://wagwiki.wba.com/stash/scm/ecomm/wag-cac-cartui.git'
                    sh 'export http_proxy=wagcorppac.walgreens.com:8080'
                    sh 'export https_proxy=wagcorppac.walgreens.com:8080'
                    sh 'npm install -g npm-cli-login'
                    sh 'npm config set registry https://oneit.wba.com/artifactory/api/npm/digital_npm_repo/'
                    sh 'npm-cli-login -u digital-marketing-service -p m/Og7hgKfEMm -e syedabdul.raqib@walgreens.com -r https://oneit.wba.com/artifactory/api/npm/digital_npm'
                    sh 'npm install'
                }

				}
				
			}
		}
	  }
	}
}

