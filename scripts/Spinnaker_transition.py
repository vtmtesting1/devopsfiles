import urllib2
import urllib
import json
import pprint
import datetime, time
import sys
import base64
import os
import ssl

ssl._create_default_https_context = ssl._create_unverified_context

def get_request_jira(queryURL2):
	#print "Hello1"
	FqueryURL = serverURL + queryURL2
        #print FqueryURL

	request = urllib2.Request(FqueryURL)
	base64string = base64.encodestring('%s:%s' % (username, password)).replace('\n', '')
	request.add_header("Authorization", "Basic %s" % base64string) 
	request.add_header("Content-type", "application/json")
	request.add_header("Accept", "application/json")  
#	request.add_header('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; WOW64; rv:50.0) Gecko/20100101 Firefox/50.0')
	result = urllib2.urlopen(request)
	data = json.load(result)

	for majorkey, subdict in data.iteritems():
		if majorkey == "issues" :
			#print majorkey
			for subkey in subdict:
			#print subkey
				key = subkey['key']
				mod_key = '%s'%(key)
				summary = subkey['fields']['summary']
				u = summary.encode('utf-8') 
				#print '%s" : "%s'%(key, u)
				print mod_key
				


def nextTranstionStatus(jira_num):
        jira_num = jira_num.rstrip("\n")
        FqueryURL = serverURL + '/rest/api/2/issue/%s/transitions'%(jira_num)
        #FqueryURL = 'https://ecomjira.walgreens.com/rest/api/2/issue/%s/transitions'%(jira_num)
        print 'Fquery: %s'%(FqueryURL)
        #comment = "testing!"
        # Put on hold -1011 |  ready for assignment - 941  | SPHONE - 271 - "Ready for QA Testing"  |  MOBILEDEV - 721 - "Deploy to QA" 
        #get transition id - https://ecomjira.walgreens.com/rest/api/2/issue/EO-37650/transitions


        request = urllib2.Request(FqueryURL)
        base64string = base64.encodestring('%s:%s' % (username, password)).replace('\n', '')
        request.add_header("Authorization", "Basic %s" % base64string)
        request.add_header("Content-type", "application/json")
        request.add_header("Accept", "application/json")
        response = urllib2.urlopen(request)
        nextTransition = json.load(response)
        print nextTransition
        nextStatus =  nextTransition['transitions'][0]['name']
        print nextTransition['transitions'][0]['name']
        print nextStatus
        if spinnakerStage in "FTPassed":
                         transtion_state_id=21
			 move_data = json.dumps({"transition": {"id": transtion_state_id},"fields": {"customfield_16014": comment,"customfield_16010": comment,"customfield_15128": comment},"update": {"comment": [{"add": {"body": comment}}]}})
        if spinnakerStage == "ApprovalDevMgrAMDeploy":
                         transtion_state_id=41
			 move_data = json.dumps({"transition": {"id": transtion_state_id},"fields": {"customfield_16619": comment,"customfield_16310": {"id": "19801"}},"update": {"comment": [{"add": {"body": comment}}]}})
	if spinnakerStage == "ApprovalDevMgrPMDeploy":
                         transtion_state_id=41
                         move_data = json.dumps({"transition": {"id": transtion_state_id},"fields": {"customfield_16619": comment,"customfield_16310": {"id": "19800"}},"update": {"comment": [{"add": {"body": comment}}]}})
        if spinnakerStage == "ApprovalUATPROD":
                         transtion_state_id=71
			 move_data = json.dumps({"transition": {"id": transtion_state_id},"update": {"comment": [{"add": {"body": comment}}]}})
	if spinnakerStage == "ApprovalRMMASS":
                         transtion_state_id=101
			 move_data = json.dumps({"transition": {"id": transtion_state_id},"fields": { "customfield_16620": comment},"update": {"comment": [{"add": {"body": comment}}]}})
	if spinnakerStage == "UAT":
                if nextStatus in "Ready for Canary Deployment":
                        transtion_state_id=171
                else:
                        print ('No Canary Testing is required')
                        exit ()
	if spinnakerStage in  "DenyRequest":
		if statusName in "QA+Assured":
			transtion_state_id=291
		elif statusName in 'READY+FOR+PROD':
			transtion_state_id=301
	if spinnakerStage == "FTFailed":
			transtion_state_id=151	
	if spinnakerStage == "PERFFAILED":
			transtion_state_id=161
	if spinnakerStage ==  "UATFAILED":
			transtion_state_id=191
	if spinnakerStage ==  "CANROLLBACK":
                        transtion_state_id=241
        if spinnakerStage ==  "MASSROLLBACK":
                        transtion_state_id=311
        if spinnakerStage in "CANARY":
                        transtion_state_id=131
			move_data = json.dumps({"transition": {"id": transtion_state_id},"update": {"comment": [{"add": {"body": comment}}]}})
	if spinnakerStage == "PRODRMDeploy":
                        transtion_state_id=161
			move_data = json.dumps({"transition": {"id": transtion_state_id},"fields": {"customfield_16711": {"id":"20907"}},"update": {"comment": [{"add": {"body": comment}}]}})
        if spinnakerStage == "PROD":                
			transtion_state_id=181
			move_data = json.dumps({"transition": {"id": transtion_state_id},"update": {"comment": [{"add": {"body": comment}}]}})
        if spinnakerStage == "PRODDEVOPSDeploy":
                        transtion_state_id=161
                        move_data = json.dumps({"transition": {"id": transtion_state_id},"fields": {"customfield_16711": {"id":"20908"}},"update": {"comment": [{"add": {"body": comment}}]}})
	post_request_jira(jira_num,transtion_state_id,move_data)

def post_request_jira(jira_num,transtion_state_id,move_data):
	
	#jira_num = 'ENV-4878'
	jira_num = jira_num.rstrip("\n")
	#FqueryURL = serverURL + '/rest/api/2/issue/%s/transitions'%(jira_num)
	FqueryURL = 'https://ecomjira.walgreens.com/rest/api/2/issue/%s/transitions'%(jira_num)
	print 'Fquery: %s'%(FqueryURL)
	#comment = "testing!"
	# Put on hold -1011 |  ready for assignment - 941  | SPHONE - 271 - "Ready for QA Testing"  |  MOBILEDEV - 721 - "Deploy to QA" 
	#get transition id - https://ecomjira.walgreens.com/rest/api/2/issue/EO-37650/transitions


	#transtion_state_id = 941
	print "hello1"
	#move_data = json.dumps({"transition": {"id": transtion_state_id},"fields": {"customfield_16111": "Postman test April 25-1","customfield_16010": "Postman test April 25-1","customfield_15128": "Postman test April 25-3"},"update": {"comment": [{"add": {"body": comment}}]}})
	#move_data = json.dumps({"transition": {"id": transtion_state_id}, "update": {"comment": [{"add": {"body": comment}}]}})
	#move_data=json.dumps({"transition": {"id": 261}, "update": {"comment": [{"add": {"body": "development4"}}]}})
	print 'Comment: %s'%(comment)
	print 'Fquery: %s'%(FqueryURL)
	print move_data
	request = urllib2.Request(FqueryURL, move_data)
	#request = urllib2.Request('https://ecomjira.walgreens.com/rest/api/2/issue/BRS-3/transitions', move_data)
	base64string = base64.encodestring('%s:%s' % (username, password)).replace('\n', '')
	request.add_header("Authorization", "Basic %s" % base64string)   
	request.add_header("Content-type", "application/json")
	request.add_header("Accept", "application/json")
	response = urllib2.urlopen(request)
	print response
				
def move_jiras():

	file = open(Rfile, 'r')

	for line in file:
		temp = line.split(output_parser)
		#print "|" + test[0] + "|"
		print ('Moving -- %s'%(temp[0]))
		if 'No ticket in status' in temp[0]:
			print ('No ticket in status')
			exit ()
		#post_request_jira(temp[0])
		nextTranstionStatus(temp[0])
		
def get_fix_versions(branch):

	file = open('map_fixV.txt', 'r')
	fixV = []

	for line in file:
		if branch in line:
			fixV.append(line.split(' = ')[1].strip())
	return fixV


if __name__ == '__main__':
    
	output_parser = " : "
	serverURL = 'https://ecomjira.walgreens.com'
	username = 'siterelease-jirabot'
	password = 'Jir@b0t'
        #projectName = 'BRS'
	queryoptions = '&startAt=0&maxResults=50&fields=id,assignee,comment,status,summary'
	#Rfile = 'release_notes.txt' 
	actionType = sys.argv[1]   #  relNotes    or  moveJiras
	#appType = sys.argv[2]	#appType = 'iphone'
	spinnakerStage = sys.argv[2]  #  this is branch name when you use relNotes and will be comment when you use moveJiras
	projectName = sys.argv[3]
	BuildNumber = sys.argv[4]
	#microserviceName = "Product+Search+API"
	microserviceName = sys.argv[5]
	statusName = sys.argv[6]
	comment = spinnakerStage + BuildNumber
	print microserviceName
	#Rfile = param3 + '_release_notes.txt'
	curtDir = os.getcwd()
	Rfile = os.getcwd()+'/relNotes/'+microserviceName + '_' + BuildNumber + '_release_notes.txt'	
	#Rfile = '/usr/local/ecomm/build/spinnaker_jira/relNotes/'+microserviceName + '_' + BuildNumber + '_release_notes.txt'
 
	
	BRSquery = ('/rest/api/2/search?jql=project=%s+and+cf[16410]="%s"+and+type="deployment"+and+status="%s"+%s'%(projectName,microserviceName,statusName,queryoptions))
        print BRSquery
        print "hello"
	#MOBILECOEquery = ('/rest/api/2/search?jql=project="Mobile+COE"+and+fixVersion="REP-FIX-VERSION"+and+status="Open"%s'%(queryoptions))                  
        #FqueryURL = serverURL + BRSquery 
#	print actionType
	
	if actionType == 'relNotes':
		curtDir = os.getcwd()
        	os.chdir(curtDir)
        	os.mkdir("relNotes")
		#final_fixV = get_fix_versions(param3)
		old_stdout = sys.stdout
		sys.stdout = open(Rfile, 'w')
	#	for current_fixV in final_fixV:

#			print ('Current fixversion being called - %s'%(current_fixV))
		get_request_jira(BRSquery)
			
		sys.stdout = old_stdout
		
		## Aug18 changes start
		
		clean = open(Rfile).read().replace('\n', '\n')
		print clean
		with open(Rfile, "w") as f:
			f.write(clean)
			
		## Aug 18 changes end	
	
		old1_stdout = sys.stdout
        	sys.stdout = open(Rfile, 'r')	
		if os.stat(Rfile).st_size==0:
			sys.stdout = open(Rfile, 'w')	
			print "No ticket in status"
			exit(1)
        	sys.stdout = old1_stdout	
		
	elif actionType == 'moveJiras':
	
		move_jiras()

	
	
		


