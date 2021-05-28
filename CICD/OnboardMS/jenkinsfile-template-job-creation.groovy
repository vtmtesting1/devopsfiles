/*
 * Execute shell script start
 */
#!/bin/bash

echo "$Job"
echo "$TemplateType"
  if [ "$TemplateType" = "Springboot-UI" ] 
     then

     cd /var/jenkins_home/workspace/
     rm -rf springboot-ui-boilerplate

     git config --global user.email "senthilkumar.palaniappan@walgreens.com"
     git config --global user.name "spalanpv"

     git clone -b master https://ecomm-stash-int:EC0mPh0t0@wagwiki.wba.com/stash/scm/ecomm/springboot-ui-boilerplate.git


     cd springboot-ui-boilerplate/deployment
     sed -i.bak 's/springbootuiboilerplate/'$MicroserviceName'/g' /var/jenkins_home/workspace/springboot-ui-boilerplate/deployment/*
     sed -i.bak 's/springboot-ui-boilerplate/'$RepoName'/g' /var/jenkins_home/workspace/springboot-ui-boilerplate/deployment/*
     rm -rf *.bak
     
     #cd /var/jenkins_home/workspace/springboot-ui-boilerplate/config/env/qa
     #sed -i.bak 's/springbootuiboilerplate/'$MicroserviceName'/g' /var/jenkins_home/workspace/springboot-ui-boilerplate/config/env/qa/config.yaml
     #rm -rf *.bak

     cd /var/jenkins_home/workspace/
     git clone -b configdevops https://ecomm-stash-int:EC0mPh0t0@wagwiki.wba.com/stash/scm/ecomm/$RepoName.git

     cd /var/jenkins_home/workspace/$RepoName/deployment
     for i in ${TemplateFile//,/$IFS} 
	 do 
     cp /var/jenkins_home/workspace/springboot-ui-boilerplate/deployment/jenkinsfile-$i .
	 done

     git add .
     git commit -m "Adding Jenkinsfile for $TemplateFile"
     git push 
     
     cd /var/jenkins_home/workspace/
     rm -rf springboot-ui-boilerplate
     rm -rf $RepoName

  elif [ "$TemplateType" = "ReactJS-UI" ] 
     then

     cd /var/jenkins_home/workspace/
     rm -rf react-ui-boilerplate

     git config --global user.email "senthilkumar.palaniappan@walgreens.com"
     git config --global user.name "spalanpv"

     git clone -b master https://ecomm-stash-int:EC0mPh0t0@wagwiki.wba.com/stash/scm/ecomm/react-ui-boilerplate.git

     cd react-ui-boilerplate/deployment
     sed -i.bak 's/reactuiboilerplate/'$MicroserviceName'/g' /var/jenkins_home/workspace/react-ui-boilerplate/deployment/*
     sed -i.bak 's/react-ui-boilerplate/'$RepoName'/g' /var/jenkins_home/workspace/react-ui-boilerplate/deployment/*
     rm -rf *.bak
     
     #cd /var/jenkins_home/workspace/react-ui-boilerplate/config/env/qa
     #sed -i.bak 's/reactuiboilerplate/'$MicroserviceName'/g' /var/jenkins_home/workspace/react-ui-boilerplate/config/env/qa/config.yaml
     #rm -rf *.bak

     cd /var/jenkins_home/workspace/
     git clone -b configdevops https://ecomm-stash-int:EC0mPh0t0@wagwiki.wba.com/stash/scm/ecomm/$RepoName.git

     cd /var/jenkins_home/workspace/$RepoName/deployment
     for i in ${TemplateFile//,/$IFS} 
	 do 
     cp /var/jenkins_home/workspace/react-ui-boilerplate/deployment/jenkinsfile-$i .
	 done
     
     git add .
     git commit -m "Adding Jenkinsfile for $TemplateFile"
     git push 
     
     cd /var/jenkins_home/workspace/
     rm -rf react-ui-boilerplate
     rm -rf $RepoName
     
  elif [ "$TemplateType" = "Springboot-API" ] 
     then

     cd /var/jenkins_home/workspace/
     rm -rf springboot-api-boilerplate

     git config --global user.email "senthilkumar.palaniappan@walgreens.com"
     git config --global user.name "spalanpv"

     git clone -b master https://ecomm-stash-int:EC0mPh0t0@wagwiki.wba.com/stash/scm/ecomm/springboot-api-boilerplate.git

     cd springboot-api-boilerplate/deployment
     sed -i.bak 's/springbootapiboilerplate/'$MicroserviceName'/g' /var/jenkins_home/workspace/springboot-api-boilerplate/deployment/*
     sed -i.bak 's/springboot-api-boilerplate/'$RepoName'/g' /var/jenkins_home/workspace/springboot-api-boilerplate/deployment/*
     rm -rf *.bak
     
     #cd /var/jenkins_home/workspace/react-ui-boilerplate/config/env/qa
     #sed -i.bak 's/reactuiboilerplate/'$MicroserviceName'/g' /var/jenkins_home/workspace/react-ui-boilerplate/config/env/qa/config.yaml
     #rm -rf *.bak

     cd /var/jenkins_home/workspace/
     git clone -b configdevops https://ecomm-stash-int:EC0mPh0t0@wagwiki.wba.com/stash/scm/ecomm/$RepoName.git

     cd /var/jenkins_home/workspace/$RepoName/deployment
     for i in ${TemplateFile//,/$IFS} 
	 do 
     cp /var/jenkins_home/workspace/springboot-api-boilerplate/deployment/jenkinsfile-$i .
	 done

     git add .
     git commit -m "Adding Jenkinsfile for $TemplateFile"
     git push 
     
     cd /var/jenkins_home/workspace/
     rm -rf springboot-api-boilerplate
     rm -rf $RepoName
     
  elif [ "$TemplateType" = "ReactJS-API" ] 
     then

     cd /var/jenkins_home/workspace/
     rm -rf node-api-boilerplate

     git config --global user.email "senthilkumar.palaniappan@walgreens.com"
     git config --global user.name "spalanpv"

     git clone -b master https://ecomm-stash-int:EC0mPh0t0@wagwiki.wba.com/stash/scm/ecomm/node-api-boilerplate.git

     cd node-api-boilerplate/deployment
     sed -i.bak 's/nodeapiboilerplate/'$MicroserviceName'/g' /var/jenkins_home/workspace/node-api-boilerplate/deployment/*
     sed -i.bak 's/node-api-boilerplate/'$RepoName'/g' /var/jenkins_home/workspace/node-api-boilerplate/deployment/*
     rm -rf *.bak

     cd /var/jenkins_home/workspace/
     git clone -b configdevops https://ecomm-stash-int:EC0mPh0t0@wagwiki.wba.com/stash/scm/ecomm/$RepoName.git

     cd /var/jenkins_home/workspace/$RepoName/deployment
     for i in ${TemplateFile//,/$IFS} 
	 do 
     cp /var/jenkins_home/workspace/node-api-boilerplate/deployment/jenkinsfile-$i .
     done

     git add .
     git commit -m "Adding Jenkinsfile for $TemplateFile"
     git push 
     
     cd /var/jenkins_home/workspace/
     rm -rf node-api-boilerplate
     rm -rf $RepoName
     
  else
      echo "Thank you" 
  fi
/*
 * Execute shell script end
 */



/*
 * Job DSL script start
 */
folder("$MicroserviceName") {
    description('Folder containing all jobs for Microservices')
}

def list = TemplateFile
list.split(',').each {
  
    def stagename = it
    println stagename
  
  pipelineJob("$MicroserviceName/${MicroserviceName}-${stagename}") {
  description()
     keepDependencies(false)
     parameters {
          credentialsParam("wag_git_creds") {
               description()
               defaultValue("cf3f6ed3-76b8-4933-9e0f-cfbbe6c00d68")
               type("com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl")
               required(false)
          }
         credentialsParam("Docker_Cred") {
               description()
               defaultValue("66cc0ce1-e462-4d90-8e38-64505c05e4d7")
               type("com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl")
               required(false)
          }      
       }
     definition {
          cpsScm {
               scm {
                    git {
                         remote {
                              name('Headers')
                         url("https://wagwiki.wba.com/stash/scm/ecomm/${RepoName}.git")
                              credentials("cf3f6ed3-76b8-4933-9e0f-cfbbe6c00d68")
                         }
                         branch("*/configdevops")
                    }
               }
               scriptPath("deployment/jenkinsfile-${stagename}")
          }
     }
}
}  


listView("Microservices") {
  
    filterBuildQueue()
    filterExecutors()
  
     jobs {
          names("dpf*|rx*|sas*|*|brs*|cac*|cs*|doc*|oms*|ref*|dch*")
        regex('dpf.*|rx.*|sas.*|brs.*|cac.*|cs.*|doc.*|oms.*|ref.*|dch.*')
      
     }
     columns {
          status()
          weather()
          name()
          lastSuccess()
          lastFailure()
          lastDuration()
          buildButton()
     }
}
/*
 * Job DSL script start
 */

