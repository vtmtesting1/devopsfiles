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
		imagePullSecrets: ['nonprodregistry'],
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
			[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
			disableConcurrentBuilds()
		])
		timeout(120) {
			node(label) {
				container(label) {
					stage ('Configure Dev') {
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
								FeatureBranch = sh(returnStdout: true, script: """
								curl "http://172.17.65.9:8080/view/Microservices/job/${config.MicroserviceName}/job/${config.MicroserviceName}-buildfeature/lastSuccessfulBuild/api/xml?xpath=.//action/parameter\\[1\\]/value" | awk -F'>' '{print \$2}' | awk -F'<' '{print \$1}'
								""").trim()
								echo FeatureBranch
								def scmVars = checkout([$class: 'GitSCM', branches: [[name: FeatureBranch ]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'wag_git_creds', url: config.scmurl]]])
								sh "kubectl --kubeconfig ../wagkubeconfigdotcom/${config.cluster} -n ${envNamespace} apply -f '${config.fromfile}'"
							} catch (exc) {
								currentBuild.result = "FAILURE"
								throw exc
							} finally {
							}
						}
						if(config.jwtnode != '' && config.jwtnode == true) {
							dir('wag-cs-jwtnode') {
								try {
									git branch: 'feature/jwtV7', credentialsId: 'wag_git_creds', url: 'https://wagwiki.wba.com/stash/scm/ecomm/wag-cs-jwtnode.git'
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

