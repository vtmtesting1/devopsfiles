#!/usr/bin/env python3

import sys
import yaml
from jinja2 import Environment, FileSystemLoader

def main(argv):
    
    ENV = Environment(loader=FileSystemLoader('.'))
    baseline = ENV.get_template("cron-template.jinja2")

    if argv == 'dev':
        pipelinevars = devvars()
    elif (argv == 'qa'):
        pipelinevars = qavars()
    elif argv == 'prod':
        pipelinevars = prodvars()
    else:
        print("Invalid environment")
        return

    #with open(r'pipeline-config.yaml') as file:
    #    config = yaml.safe_load(file)
    f = open('../springbatch/config/cron/config-cron-%s.yaml'%argv, 'w')
    cronConfig = baseline.render(pipelinevars=pipelinevars)
    f.write(cronConfig)
    f.close

def devvars():

    with open(r'../springbatch/config/spinnaker/pipeline-config.yaml') as file:
        config = yaml.safe_load(file)
    pipelineConfig = config['pipeline']['variables']
    
    cronVars = {
        'microserviceName': pipelineConfig['microserviceName'],
        'env': 'dev',
        'minCPU': pipelineConfig['minCPU'],
        'maxCPU': pipelineConfig['maxCPU'],
        'minMemory': pipelineConfig['minMemory'],
        'maxMemory': pipelineConfig['maxMemory'],
        'parallelReplicas': pipelineConfig['parallelReplicas'],
        'TeamName': pipelineConfig['TeamName'],
        'nameSpace': pipelineConfig['nameSpace'],
        'cronschedule': pipelineConfig['cronschedule'],
        'args': pipelineConfig['args'],
        'secrets': pipelineConfig['secrets'],
        'acr': 'nonprodregistry',
        'imagePullSecrets': 'nonprodregistry'
    }
    
    if 'nodeSelector' in pipelineConfig:
        cronVars['nodeSelector'] = pipelineConfig['nodeSelector']
    if 'envVariables' in pipelineConfig:
        cronVars['envVariables'] = pipelineConfig['envVariables']
    if 'persistentVolumeClaims' in pipelineConfig:
        cronVars['persistentVolumeClaims'] = pipelineConfig['persistentVolumeClaims']
    if 'ssd' in pipelineConfig:
        cronVars['ssd'] = pipelineConfig['ssd'] 
    return cronVars

def qavars():

    with open(r'../springbatch/config/spinnaker/develop-pipeline-config.yaml') as file:
        config = yaml.safe_load(file)
    pipelineConfig = config['pipeline']['variables']
    
    cronVars = {
        'microserviceName': pipelineConfig['microserviceName'],
        'env': 'qa',
        'minCPU': pipelineConfig['minCPUQA'],
        'maxCPU': pipelineConfig['maxCPUQA'],
        'minMemory': pipelineConfig['minMemoryQA'],
        'maxMemory': pipelineConfig['maxMemoryQA'],
        'parallelReplicas': pipelineConfig['parallelReplicasQA'],
        'TeamName': pipelineConfig['TeamName'],
        'nameSpace': pipelineConfig['nameSpace'],
        'cronschedule': pipelineConfig['cronscheduleQA'],
        'args': pipelineConfig['argsQA'],
        'secrets': pipelineConfig['secrets'],
        'acr': 'wagdigital',
        'imagePullSecrets': 'prodregistry'
    }

    if 'nodeSelectorQA' in pipelineConfig:
        cronVars['nodeSelector'] = pipelineConfig['nodeSelectorQA']
    if 'envVariables' in pipelineConfig:
        cronVars['envVariables'] = pipelineConfig['envVariables']
    if 'persistentVolumeClaims' in pipelineConfig:
        cronVars['persistentVolumeClaims'] = pipelineConfig['persistentVolumeClaims']
    if 'ssd' in pipelineConfig:
        cronVars['ssd'] = pipelineConfig['ssd']    
    return cronVars

def prodvars():

    with open(r'../springbatch/config/spinnaker/develop-pipeline-config.yaml') as file:
        config = yaml.safe_load(file)
    pipelineConfig = config['pipeline']['variables']
    
    cronVars = {
        'microserviceName': pipelineConfig['microserviceName'],
        'env': 'prod',
        'minCPU': pipelineConfig['minCPUProd'],
        'maxCPU': pipelineConfig['maxCPUProd'],
        'minMemory': pipelineConfig['minMemoryProd'],
        'maxMemory': pipelineConfig['maxMemoryProd'],
        'parallelReplicas': pipelineConfig['parallelReplicasProd'],
        'TeamName': pipelineConfig['TeamName'],
        'nameSpace': pipelineConfig['nameSpace'],
        'cronschedule': pipelineConfig['cronscheduleProd'],
        'args': pipelineConfig['argsProd'],
        'secrets': pipelineConfig['secrets'],
        'acr': 'wagdigitaldotcomprod',
        'imagePullSecrets': 'dotcomprodregistry'
    }

    if 'nodeSelectorProd' in pipelineConfig:
        cronVars['nodeSelector'] = pipelineConfig['nodeSelectorProd']
    if 'envVariables' in pipelineConfig:
        cronVars['envVariables'] = pipelineConfig['envVariables']
    if 'persistentVolumeClaims' in pipelineConfig:
        cronVars['persistentVolumeClaims'] = pipelineConfig['persistentVolumeClaims']
    if 'ssd' in pipelineConfig:
        cronVars['ssd'] = pipelineConfig['ssd'] 
    return cronVars


if __name__ == '__main__':
    main(sys.argv[1])
    



