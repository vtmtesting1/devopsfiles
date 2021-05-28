/* (c) Walgreen Co. All rights reserved.*/

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def envNamespace = config.namespace ?: "dotcom-app"
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
                            git branch: 'master', credentialsId: 'wag_git_creds', url: 'https://wagwiki.walgreens.com/stash/scm/digdev/wag-kubeconfig-dotcom.git'
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

