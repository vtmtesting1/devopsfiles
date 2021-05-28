/* (c) Walgreen Co. All rights reserved.*/
def call(body) {
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	properties([
		[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
		disableConcurrentBuilds()
	])
	timeout(120) {
	node {
		def module = config.MicroserviceName;
		def tempImageTag = "wagdigital.azurecr.io/digital/temp/${module}";
		stage('TwistLock Scan') {
			try {
				withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
					sh "docker pull '${tempImageTag}'"
				}
				twistlockScan ca: '', cert: '', compliancePolicy: 'warn', dockerAddress: "unix:///var/run/docker.sock", ignoreImageBuildTime: true, image: "${tempImageTag}", key: '', logLevel: 'true', policy: 'warn', requirePackageUpdate: false, timeout: 10
				twistlockPublish ca: '', cert: '', dockerAddress: "unix:///var/run/docker.sock", ignoreImageBuildTime: true, image: "${tempImageTag}", key: '', logLevel: 'true', timeout: 10
			} catch (exc) {
				currentBuild.result = "FAILURE"
				if(config.TeamDL) {
					mail body: "${config.MicroserviceName} - Twistlock scan error is here: ${env.BUILD_URL}" ,
					from: 'digital-ms-devops@walgreens.com',
					replyTo: 'digital-ms-devops@walgreens.com',
					subject: "${config.MicroserviceName} - Twistlock scan failed #${env.BUILD_NUMBER}",
					to: "${config.TeamDL}"
				}
				throw exc
			} finally {
				withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
					sh "docker rmi '${tempImageTag}' -f"
				}
			}
		}
	}
	}
}

