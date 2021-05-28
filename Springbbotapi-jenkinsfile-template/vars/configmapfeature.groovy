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
        idleMinutes: 2,
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
                            git branch: 'master', credentialsId: 'wag_git_creds', url: 'https://wagwiki.walgreens.com/stash/scm/digdev/wag-kubeconfig-dotcom.git'
                        } catch (exc) {
                            currentBuild.result = "FAILURE"
                            throw exc
                        } finally {
                        }
                    }
                    dir(config.MicroserviceName) {
                        try {
                            FeatureBranch = sh(returnStdout: true, script: """
                            curl -u admin:776b3fa981c61046348a178df3074d55 "http://172.17.65.9:8080/view/Microservices/job/${config.MicroserviceName}/job/${config.MicroserviceName}-buildfeature/lastSuccessfulBuild/api/xml?xpath=.//action/parameter/value" | awk -F'>' '{print \$2}' | awk -F'<' '{print \$1}'
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
                }
            }
        }
    }
    }
}

