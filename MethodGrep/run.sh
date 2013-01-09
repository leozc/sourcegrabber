#Use mvn test to run.
#$KP=`find ~.m2/repository/com/zillow/|grep  -e 'jar$' |perl -p -e 's/\n/;/'`

mvn package |grep '^>>' |sort|uniq
