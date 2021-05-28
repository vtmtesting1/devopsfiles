/* (c) Walgreen Co. All rights reserved.*/
def call(body) {
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	def envNamespace = config.namespace ?: "dotcom-app"
	def label = "nodejs-build-${UUID.randomUUID().toString()}"
	podTemplate(
		label: label,
		name: label,
		imagePullSecrets: ['prodregistry'],
		containers: [
			containerTemplate(
				name: label,
				image: 'wagdigital.azurecr.io/baseimg/wag-dotcom-build-zulu12-nodejs12-bionic:v1',
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
			[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
			disableConcurrentBuilds()
		])
		timeout(120) {
			node(label) {
				container(label) {
					stage ('Configure') {
						dir('wagkubeconfigdotcom') {
							try {
								git branch: 'master', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/digdev/wag-kubeconfig-dotcom.git'
							} catch (exc) {
								currentBuild.result = "FAILURE"
								throw exc
							} finally {
							}
						}
						dir(config.MicroserviceName) {
							try {
								git branch: 'development', credentialsId: 'wag_git_creds', url: config.scmurl
								sh "kubectl --kubeconfig ../wagkubeconfigdotcom/${config.cluster} -n ${envNamespace} apply -f '${config.fromfile}'"
								BuildTag = sh(returnStdout: true, script: """
								curl "http://dgtljenkins-acs.walgreens.com:8080/view/Microservices/job/${config.MicroserviceName}/job/${config.MicroserviceName}-build/lastSuccessfulBuild/buildNumber"
								""").trim()
								sh "echo 'MicroserviceName : ${config.MicroserviceName}' >> build.properties"
								sh "echo 'BuildTag:${BuildTag}' >> build.properties"
								archive "build.properties"
							} catch (exc) {
								currentBuild.result = "FAILURE"
								throw exc
							} finally {
							}
						}
						if(config.jwtnode != '' && config.jwtnode == true) {
							dir('wag-cs-jwtnode') {
								try {
									git branch: 'develop', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/ecomm/wag-cs-jwtnode.git'
									sh "kubectl --kubeconfig ../wagkubeconfigdotcom/${config.cluster} -n ${envNamespace} apply -f '${config.fromfile}'"
								} catch (exc) {
									currentBuild.result = "FAILURE"
									throw exc
								} finally {
								}
							}
						}
						if(config.voltagenode != '' && config.voltagenode == true) {
							dir('wag-cs-voltagenode') {
								try {
									git branch: 'feature/voltagev3.0', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/ecomm/wag-cs-voltagenode.git'
									sh "kubectl --kubeconfig ../wagkubeconfigdotcom/${config.cluster} -n ${envNamespace} apply -f '${config.fromfile}'"
								} catch (exc) {
									currentBuild.result = "FAILURE"
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

