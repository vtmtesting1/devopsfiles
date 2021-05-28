/* (c) Walgreen Co. All rights reserved.*/
def call(body) {
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
	properties([
		[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '10']],
        parameters([string(defaultValue: '', description: '', name: 'buildnumber')]),
		disableConcurrentBuilds()
	])
	timeout(120) {
	node {
		stage('Push to ACR') {
			dir(config.MicroserviceName) {
				def buildnumberreal = "${params.buildnumber}"
                sh "echo ${buildnumberreal}"
                DockerTag = "${buildnumberreal}"
                def application = 'dotcom';
				def module = config.MicroserviceName;
				def qaACRimage = "wagdigital.azurecr.io/digital/${application}/${module}:${DockerTag}";
				def prodACRimage = "wagdigitaldotcomprod.azurecr.io/digital/${application}/${module}:${DockerTag}";
              	def prodEUACRImage = "wagdigitaldotcomprodacr.azurecr.io/digital/${application}/${module}:${DockerTag}";
				try {
					withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigital.azurecr.io']) {
                      sh "docker pull ${qaACRimage}"
					}
					sh "docker tag ${qaACRimage} ${prodACRimage}"
					withDockerRegistry([credentialsId: 'acr_prod', url: 'https://wagdigitaldotcomprod.azurecr.io']) {
						sh "docker push ${prodACRimage}"
					}
                  	sh "docker tag ${qaACRimage} ${prodEUACRImage}"
                  	withDockerRegistry([credentialsId: 'dr_prod_acr', url: 'https://wagdigitaldotcomprodacr.azurecr.io']) {
                        sh "docker push ${prodEUACRImage}"
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
					sh "docker rmi ${qaACRimage} -f"
				}
			}
		}
	}
	}
}

