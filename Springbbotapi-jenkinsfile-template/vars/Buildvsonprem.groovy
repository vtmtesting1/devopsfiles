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
                image: 'wagdigital.azurecr.io/baseimg/wag-dotcom-build-zulujdk12-nodejs6-bionic:v3',
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
                    def application = 'dotcom';
                    def module = config.MicroserviceName;
                    def imageTagVS = "wagdigital.azurecr.io/digital/${application}/${module}-vs:latest";
                    stage('Clone') {
                        try {
                            dir(config.MicroserviceName) {
                                git branch: 'development', credentialsId: 'wag_git_creds', url: config.scmurl
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
                    if(config.dockerimage == true) {
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
                    if(config.iaasDeployment == true) {
                        stage('Deploy to IaaS') {
                            dir(config.MicroserviceName) {
                                try {
                                    //sh "/opt/maven/bin/mvn deploy:deploy-file -Dfile=/home/jenkins/workspace/${config.MicroserviceName}/${config.MicroserviceName}-build-vs/${config.MicroserviceName}/build/${config.MicroserviceName}-vs.zip -DgroupId=com.walgreens.microservice.regression -DartifactId=${config.MicroserviceName}-vs -Dversion=${env.BUILD_NUMBER} -Durl=http://ecomm-service:emf-pgE-Tdw-Wa4@wagwiki.walgreens.com/artifactory/ecomm-snapshot-libs"
                                    sh "cd /home/jenkins/workspace/${config.MicroserviceName}/${config.MicroserviceName}-build-vs/${config.MicroserviceName}/build/;mv ${config.MicroserviceName}-vs.zip ${config.MicroserviceName}-vs-${env.BUILD_NUMBER}.zip; curl -u ecomm-service:emf-pgE-Tdw-Wa4 --upload-file ${config.MicroserviceName}-vs-${env.BUILD_NUMBER}.zip https://wagwiki.walgreens.com/artifactory/ecomm-snapshot-libs/com/walgreens/microservice/regression/${config.MicroserviceName}-vs/${env.BUILD_NUMBER}/"
                                    sshagent(['uecbt03-sesadmin-ssh-key']) {
                                    sh "ssh -tt -oStrictHostKeyChecking=no sesadmin@uecbt03.walgreens.com 'rm -rf /usr/local/ecomm/app/rx/${config.MicroserviceName}/* ; mkdir /usr/local/ecomm/app/rx/${config.MicroserviceName}/;cd /usr/local/ecomm/app/rx/${config.MicroserviceName}/ ; wget http://ecomm-service:emf-pgE-Tdw-Wa4@wagwiki.walgreens.com/artifactory/ecomm-snapshot-libs/com/walgreens/microservice/regression/${config.MicroserviceName}-vs/${env.BUILD_NUMBER}/${config.MicroserviceName}-vs-${env.BUILD_NUMBER}.zip'"
                                    sh "ssh -tt -oStrictHostKeyChecking=no sesadmin@uecbt03.walgreens.com ' mkdir -p /usr/local/ecomm/app/rx/${config.MicroserviceName}/logs/'"
                                    // sh "ssh -tt -oStrictHostKeyChecking=no sesadmin@uecbt03.walgreens.com 'cd /usr/local/ecomm/app/rx/${config.MicroserviceName}/ ; wget http://ecomm-service:emf-pgE-Tdw-Wa4@wagwiki.walgreens.com/artifactory/ecomm-snapshot-libs/com/walgreens/microservice/regression/${config.MicroserviceName}-vs/${env.BUILD_NUMBER}/${config.MicroserviceName}-vs-${env.BUILD_NUMBER}.zip'"
                                    sh "ssh -tt -oStrictHostKeyChecking=no sesadmin@uecbt03.walgreens.com 'cd /usr/local/ecomm/app/rx/${config.MicroserviceName}/ ; mv ${config.MicroserviceName}-vs-${env.BUILD_NUMBER}.zip ${config.MicroserviceName}.zip'"
                                    sh "ssh -tt sesadmin@uecbt03.walgreens.com unzip -u -o '/usr/local/ecomm/app/rx/${config.MicroserviceName}/${config.MicroserviceName}.zip -d /usr/local/ecomm/app/rx/${config.MicroserviceName}/'"
                                    sh "ssh -tt sesadmin@uecbt03.walgreens.com chmod -R 777 '/usr/local/ecomm/app/rx/${config.MicroserviceName}'"
                                    sh "ssh -tt sesadmin@uecbt03.walgreens.com '/usr/sbin/lsof -t -i:2525 | xargs kill -9 ; /usr/local/ecomm/app/rx/mountebank/runmb.sh ${config.MicroserviceName}'"  
                                    sh "ssh sesadmin@uecbt03.walgreens.com '/usr/local/ecomm/app/rx/mountebank/runmb.sh ${config.MicroserviceName}'"
                                    /* sh "ssh -tt -oStrictHostKeyChecking=no desadmin@decbt01.walgreens.com 'sudo chef-client -j build.json -o 'recipe[tender_deploy::tender]'' " */
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

