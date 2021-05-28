#!/usr/local/bin/python3

###############################################################################################
# 1) Install the following packages:
#   pip3 install python-dotenv
#   pip3 install msrestazure
#   pip3 install azure
# 
# 2) Update the following environment variables using these Shell commands:
# export AZURE_TENANT_ID=92cb778e-8ba7-4f34-a011-4ba6e7366996
# export AZURE_CLIENT_ID=<Client ID here>
# export AZURE_CLIENT_SECRET=<Client Secret here>
# 
# 3) Update the following variables/expressions in the script with correct values
#   subscription_id = ''
#   resource_group = ''
#	vnet_resource_group = ''
# 	vnet_name = ''
# 	subnet_name = ''
# 
# 4) And then finally run the script:
#   python3 cleanup.py
###############################################################################################

import sys
import os
import time
from dotenv import load_dotenv, find_dotenv

from msrestazure.azure_exceptions import CloudError
from azure.common.credentials import ServicePrincipalCredentials

from azure.mgmt.resource.resources import ResourceManagementClient
from azure.mgmt.network import NetworkManagementClient
from azure.mgmt.compute import ComputeManagementClient
from azure.mgmt.network import NetworkManagementClient
from azure.mgmt.resource.resources.models import GenericResource

def get_provider_api(client, resource_provider, resource_type):
  provider = client.providers.get(resource_provider)
  rt = next((t for t in provider.resource_types if t.resource_type == resource_type), None)

  if rt and 'api_versions' in rt.__dict__:
    api_version = [v for v in rt.__dict__['api_versions'] if 'preview' not in v.lower()]
    return api_version[0] if api_version else rt.__dict__['api_versions'][0]


load_dotenv(find_dotenv())

credentials = ServicePrincipalCredentials(
  client_id=os.environ.get('AZURE_CLIENT_ID'),
  secret=os.environ.get('AZURE_CLIENT_SECRET'),
  tenant=os.environ.get('AZURE_TENANT_ID')
)

subscription_id = 'f865366b-adad-4e1d-855c-fce136ce95e2'
resource_group = 'digital_nsen_nprod_dcomdev_acs01_ncus_rg'
vnet_resource_group = 'digital_nsen_nprod_network_ncus_rg'
vnet_name = 'digital-northcentralus-nonsen-nonprod-vnet-01'
subnet_name = 'digital-dev-acs01'
excluded_disks = ['jenkins-data','pact-broker-dev-data','sonarqube64-data','sonarqube64-plugins','sonarqube73-data','sonarqube73-plugins']

resource_client = ResourceManagementClient(credentials, subscription_id)
compute_client = ComputeManagementClient(credentials, subscription_id)
network_client = NetworkManagementClient(credentials, subscription_id)

print('\n======================================================')
print('Listing VMs in Resource Group - ' + resource_group)
print('======================================================\n')

vm_array = compute_client.virtual_machines.list(resource_group)
vm_array_size = 0

for vm in vm_array:
	vm_array_size = vm_array_size + 1
	print('\t' + str(vm_array_size) + '. ' + vm.type + ' [ ' + vm.name + ' ]')

if vm_array_size == 0:
	print('None found')
else:
	response = None
	while response not in ("YES", "NO"):
		response = input("\nDo you want to delete these " + str(vm_array_size) + " VMs (yes/no): ")
		response = response.upper()
		if response == "YES":
			print('\n=====================================================')
			print('Deleting VMs in Resource Group - ' + resource_group)
			print('=====================================================\n')
			vm_array = compute_client.virtual_machines.list(resource_group)
			for vm in vm_array:
				print('\tDeleting VM [ ' + vm.name + ' ]')
				async_virtual_machine = compute_client.virtual_machines.delete(resource_group,vm.name)
				# async_virtual_machine.wait()
		elif response == "NO":
			print('\nYou have chosen ** NOT ** to delete the VMs in Resource Group - ' + resource_group + '\n')
		else:
			print("Please enter either yes or no")

######################################################################################################################

print('\n==============================================================')
print('Listing Route Tables in Resource Group - ' + resource_group)
print('==============================================================\n')

rt_array = network_client.route_tables.list(resource_group)
rt_array_size = 0

for rt in rt_array:
	rt_array_size = rt_array_size + 1
	print('\t' + str(rt_array_size) + '. ' + rt.type + ' [ ' + rt.name + ' ]')

if rt_array_size == 0:
	print('None found')
else:
	response = None
	while response not in ("YES", "NO"):
		response = input("\nDo you want to remove this/these " + str(rt_array_size) + " Route Table(s) (yes/no): ")
		response = response.upper()
		if response == "YES":
			print('\n=======================================================')
			print('Removing Route Tables (if any) from the Subnet - ' + subnet_name)
			print('=======================================================')
			subnet_info = network_client.subnets.get(vnet_resource_group, vnet_name, subnet_name)
			async_route_table = network_client.subnets.create_or_update(vnet_resource_group, vnet_name, subnet_name, { 'address_prefix': subnet_info.address_prefix, 'route_table': None, 'network_security_group': subnet_info.network_security_group, 'service_endpoints': subnet_info.service_endpoints })
			async_route_table.wait()

			print('===============================================================')
			print('Deleting Route Tables in Resource Group - ' + resource_group)
			print('===============================================================\n')

			rt_array = network_client.route_tables.list(resource_group)
			for rt in rt_array:
				print('\tDeleting Route Table [ ' + rt.name + ' ]')
				async_route_table = network_client.route_tables.delete(resource_group,rt.name)
				async_route_table.wait()
				print('\nSleeping for 90 seconds to allow deletion of resources..')
				time.sleep(90)
		elif response == "NO":
			print('\nYou have chosen ** NOT ** to remove the above Route Tables\n')
		else:
			print("Please enter either yes or no")


######################################################################################################################

resource_array = resource_client.resources.list_by_resource_group(resource_group)

for resource in resource_array:
	if resource.type == 'Microsoft.Compute/virtualMachines':
		print('\n=============================================================================')
		print('Other Azure resources cannot be removed until all VMs are removed. Exiting.')
		print('=============================================================================\n')
		sys.exit(10)

	if resource.type == 'Microsoft.Network/routeTables':
		print('\n==================================================================================')
		print('Other Azure resources cannot be removed until all Route Tables are removed. Exiting.')
		print('==================================================================================\n')
		sys.exit(9)

print('\n========================================================================')
print('Listing remaining Azure resources in Resource Group - ' + resource_group)
print('========================================================================\n')

resource_array = resource_client.resources.list_by_resource_group(resource_group)
resource_array_size = 0

for resource in resource_array:
	resource_array_size = resource_array_size + 1
	if resource.type == 'Microsoft.Compute/disks' and resource.name in excluded_disks:
		print('\n\t' + str(resource_array_size) + '. '  + resource.type + ' [ ' + resource.name + ' ] - NOTE: EXCLUDED FROM DELETION\n')
	else:
		if resource.type == 'Microsoft.Storage/storageAccounts' or resource.type == 'Microsoft.Compute/snapshots':
			print('\n\t' + str(resource_array_size) + '. '  + resource.type + ' [ ' + resource.name + ' ] - NOTE: THIS WILL NOT BE DELETED\n')
		else:
			print('\t' + str(resource_array_size) + '. ' + resource.type + ' [ ' + resource.name + ' ]')

if resource_array_size == 0:
	print('None found')
else:
	response = None
	while response not in ("YES", "NO"):
		response = input("\nDo you want to delete this/these " + str(resource_array_size) + " resource(s) (yes/no): ")
		response = response.upper()
		if response == "YES":
			print('\n====================================================================')
			print('Deleting other Azure resources in Resource Group - ' + resource_group)
			print('====================================================================\n')
			resource_array = resource_client.resources.list_by_resource_group(resource_group)
			for resource in resource_array:
				if resource.type == 'Microsoft.Compute/disks' and resource.name in excluded_disks:
					print('\n\t' + resource.name + ' is excluded from deletion\n')
				else:
					if resource.type == 'Microsoft.Storage/storageAccounts' or resource.type == 'Microsoft.Compute/snapshots':
						print('\n\t' + resource.name +' is a Storage Account / Snapshot. It will not be deleted\n')
					else:
						resource_provider = resource.type.split('/')[0]
						resource_type = resource.type.split('/')[1]
						if len(resource.type.split('/')) > 2:
							resource_type = resource_type + '/' + resource.type.split('/')[2]
						api_version = get_provider_api(resource_client, resource_provider, resource_type)
						print('\tDeleting ' + resource.type + ' [ ' + resource.name + ' ]')
						async_resource = resource_client.resources.delete(resource_group, resource_provider, '', resource_type, resource.name, api_version)
						# async_resource.wait()
		elif response == "NO":
			print('\nYou have chosen ** NOT ** to delete any other Azure resource in Resource Group - ' + resource_group + '\n')
		else:
			print("Please enter either yes or no")

print('\n')

