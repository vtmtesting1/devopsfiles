#!/bin/bash

############################################################################
#
# NAME: 		acs_dotcom_dev_nc_primary_setup.sh
#
# PURPOSE: 		Installs the ACS Dotcom DEV Kubernetes Cluster from scratch
#				using the Standard deployment template in the DevOps code
#				repository. This also installs the following:
#
#				1) Heptio Ark
#				2) Nginx Ingress Controller
#				3) Istio Ingress Controller
#				4) Filebeat
#				5) Cluster Autoscaler
#
# DEPENDENCIES: This script requires the following to work:
#
#				1) 	Walgreens Stash User ID and Password, which has
#					read/write access to the DevOps repo
#				2) 	ssh-keygen
#				3) 	uuencode
#				4) 	mail
#				5) 	git
#
# INPUT:		None in command line. Currently interactive.
#
# OUTPUT:		None.
#
# AUTHOR:		Jayanth Purushothaman (jayanth.purushothaman@walgreens.com)
#
############################################################################

############################################################################
# Load all the functions required for this script
############################################################################

source ../../../../../_modules/Colors
source ../../../../../_modules/Vars
source ../../../../../_modules/ACSDeployment
source ../../../../../_modules/AzureLogout
source ../../../../../_modules/CheckAzureLogin
source ../../../../../_modules/CheckBinaries
source ../../../../../_modules/CheckChefBootstrap
source ../../../../../_modules/CheckClientID
source ../../../../../_modules/CheckClientSecret
source ../../../../../_modules/CheckIP
source ../../../../../_modules/CheckResourceGroup
source ../../../../../_modules/CheckSubnet
source ../../../../../_modules/CheckVnet
source ../../../../../_modules/FixAdminGroup
source ../../../../../_modules/FixDNS
source ../../../../../_modules/FixHeapster
source ../../../../../_modules/GenerateDeploymentJSON
source ../../../../../_modules/GenerateSSHKeys
source ../../../../../_modules/GetKubeconfig
source ../../../../../_modules/GetUserInputs
source ../../../../../_modules/InstallClusterAutoscaler
source ../../../../../_modules/InstallFilebeat
source ../../../../../_modules/InstallHeptioArk
source ../../../../../_modules/InstallIstio11Controller
source ../../../../../_modules/InstallNginxController
source ../../../../../_modules/InstallTwistlock
source ../../../../../_modules/InstallKubePrometheus
source ../../../../../_modules/AddNginxReplyURLtoAADApp
source ../../../../../_modules/InstallStandardConfig
source ../../../../../_modules/LoadConfig
source ../../../../../_modules/PrintConfig
source ../../../../../_modules/MaskClientSecret
source ../../../../../_modules/RemoveWebProxy
source ../../../../../_modules/RepoCommit
source ../../../../../_modules/ResetWebProxy
source ../../../../../_modules/SetRouteTable
source ../../../../../_modules/SetSubscriptionID
source ../../../../../_modules/SetWebProxy
source ../../../../../_modules/SyncRepo
source ../../../../../_modules/Usage
source ../../../../../_modules/VPNClient
source ../../../../../_modules/VerifyKubeconfig

############################################################################
# Define Configuration Parameters here if .config file is not present
# DON'T DO BOTH!
############################################################################


############################################################################
# Core Functions
############################################################################

Bootstrap() {
	CheckBinaries
	SyncRepo
}

PreflightChecks() {
	VPNClient "ON"
	SetWebProxy
	CheckClientSecret
	CheckAzureLogin
	SetSubscriptionID
	CheckVnet
	CheckSubnet
	CheckIP
	CheckClientID
	CheckResourceGroup
}

DeployCluster() {
	GenerateSSHKeys
	GenerateDeploymentJSON
	ACSDeployment
}

PostDeployment() {
	SetRouteTable
	AddNginxReplyURLtoAADApp
}

InstallAddOns() {
	CheckChefBootstrap
	VPNClient "ON"
	GetKubeconfig
	SetWebProxy
	VerifyKubeconfig
	FixDNS
	FixHeapster
	FixAdminGroup
	InstallStandardConfig
	InstallHeptioArk
	InstallIstioController
	InstallNginxController
	InstallFilebeat
	InstallClusterAutoscaler
	InstallKubePrometheus
	# ResetWebProxy
	# InstallTwistlock
}

WrapUp() {
	MaskClientSecret
	RepoCommit
	ResetWebProxy
	AzureLogout
}


############################################################################
# Main Script starts here
############################################################################

while getopts ":h-:" opt; do
	case ${opt} in
		h )
			Usage
			exit 0
			;;
		- )
			CONFIGFILE=$(basename $0 | awk 'BEGIN{FS=OFS="."}{ $NF=""; print }')"config"
			LoadConfig "$CONFIGFILE"
			PrintConfig
			LONG_OPTARG="${OPTARG#*=}"
			AZ=$(which az)
			KUBECTL=$(which kubectl)
			ACSENGINE=$(which aks-engine)
			CLUSTER_NAME=${LONG_OPTARG}

			case $OPTARG in
				install-addons=?* )
					InstallAddOns
					WrapUp
					exit 0
					;;
				install-addons* )
					echo -e "No arguments specified for --$OPTARG. Needs an ACS cluster name"
					exit 0
					;;
				reinstall-istio=?* )
					VPNClient "ON"
					SetWebProxy
					VerifyKubeconfig
					InstallIstioController
					exit 0
					;;
				reinstall-istio* )
					echo -e "No arguments specified for --$OPTARG. Needs an ACS cluster name"
					exit 0
					;;
				reinstall-nginx=?* )
					AddNginxReplyURLtoAADApp
					VPNClient "ON"
					SetWebProxy
					VerifyKubeconfig
					InstallNginxController
					exit 0
					;;
				reinstall-nginx* )
					echo -e "No arguments specified for --$OPTARG. Needs an ACS cluster name"
					exit 0
					;;
				reinstall-autoscaler=?* )
					VPNClient "ON"
					SetWebProxy
					VerifyKubeconfig
					InstallClusterAutoscaler
					exit 0
					;;
				reinstall-autoscaler* )
					echo -e "No arguments specified for --$OPTARG. Needs an ACS cluster name"
					exit 0
					;;
				reinstall-kube-prometheus=?* )
					VPNClient "ON"
					SetWebProxy
					VerifyKubeconfig
					InstallKubePrometheus
					exit 0
					;;
				reinstall-kube-prometheus* )
					echo -e "No arguments specified for --$OPTARG. Needs an ACS cluster name"
					exit 0
					;;
				upgrade-twistlock=?* )
					SetWebProxy
					VerifyKubeconfig
					InstallTwistlock
					exit 0
					;;
				upgrade-twistlock* )
					echo -e "No arguments specified for --$OPTARG. Needs an ACS cluster name"
					exit 0
					;;
				apply-config=?* )
					SetWebProxy
					VerifyKubeconfig
					InstallStandardConfig
					exit 0
					;;
				apply-config* )
					echo -e "No arguments specified for --$OPTARG. Needs an ACS cluster name"
					exit 0
					;;
				fix-dns=?* )
					SetWebProxy
					VerifyKubeconfig
					FixDNS
					exit 0
					;;
				fix-dns* )
					echo -e "No arguments specified for --$OPTARG. Needs an ACS cluster name"
					exit 0
					;;
				fix-heapster=?* )
					SetWebProxy
					VerifyKubeconfig
					FixHeapster
					exit 0
					;;
				fix-heapster* )
					echo -e "No arguments specified for --$OPTARG. Needs an ACS cluster name"
					exit 0
					;;
				fix-admingroup=?* )
					CheckAzureLogin
					SetWebProxy
					VerifyKubeconfig
					FixAdminGroup
					exit 0
					;;
				fix-admingroup* )
					echo -e "No arguments specified for --$OPTARG. Needs an ACS cluster name"
					exit 0
					;;
				help )
					Usage
					exit 0
					;;
				* )
					echo -e "\nInvalid Option: '--$OPTARG'" >&2
					Usage
					exit 2
					;;
			esac
			exit 0
			;;
		\? )
			echo
			echo -e "\nInvalid Option: '-$OPTARG'" 1>&2
			Usage
			exit 1
			;;
	esac
done
shift $((OPTIND -1))

CONFIGFILE=$(basename $0 | awk 'BEGIN{FS=OFS="."}{ $NF=""; print }')"config"
LoadConfig "$CONFIGFILE"
PrintConfig

Bootstrap
GetUserInputs
PreflightChecks
DeployCluster
PostDeployment
InstallAddOns
WrapUp

############################################################################
# Main Script ends here
############################################################################

