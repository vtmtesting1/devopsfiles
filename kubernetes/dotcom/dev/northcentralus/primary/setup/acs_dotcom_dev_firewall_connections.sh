############################################################################
#
# NAME:
#
# PURPOSE:
#
# DEPENDENCIES: This script requires the following to work:
#
# INPUT:
#
# OUTPUT:
#
# AUTHOR:		Jayanth Purushothaman (jayanth.purushothaman@walgreens.com)
#
############################################################################

ENDPOINT_LIST=$1
[ $# -ne 1 ] && echo -e "\nPass a CSV file with 3 columns (Hostname, IP, Port) as argument. Exiting...\n" && exit 99

WORKING_LIST="$0.working"
FAILED_LIST="$0.failed"
REFUSED_LIST="$0.refused"

cat /dev/null > $WORKING_LIST
cat /dev/null > $FAILED_LIST
cat /dev/null > $REFUSED_LIST

cat $ENDPOINT_LIST |  while read -r CONNECTION; do
	_DESCRIPTION=$(echo $CONNECTION | awk -F',' '{ print $1 }')
	_IP=$(echo $CONNECTION | awk -F',' '{ print $2 }')
	_PORT=$(echo $CONNECTION | awk -F',' '{ print $3 }')

	echo "Checking $_IP:$_PORT"

	if [ ${_PORT} -eq 43 ]; then
		nc -vz -u -w 1 ${_IP} ${_PORT} > /dev/null 2> error.out
	else
		nc -vz -w 1 ${_IP} ${_PORT} > /dev/null 2> error.out
	fi

	if [ $? -ne 0 ]; then
		if grep -q 'Connection refused' error.out; then
			echo -e "`hostname -i` --> ${_DESCRIPTION} (${_IP}) : ${_PORT} ===> `cat error.out`" >> $REFUSED_LIST
		else
			echo -e "`hostname -i` --> ${_DESCRIPTION} (${_IP}) : ${_PORT} ===> `cat error.out`" >> $FAILED_LIST
		fi
	else
		echo -e "`hostname -i` --> ${_DESCRIPTION} (${_IP}) : ${_PORT}" >> $WORKING_LIST
	fi
done

echo -e "\nThe following connections are working\n"
cat $WORKING_LIST
echo -e "\nThe following connections were allowed but have been refused by the destination service\n"
cat $REFUSED_LIST
echo -e "\nThe following connections have failed\n"
cat $FAILED_LIST

