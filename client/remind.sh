curl -k -X POST https://url/add_reminder \
     -H Content-Type:application/json \
     -d '{
           "Title": "Meeting with team",
           "Description": "Discuss project status",
           "Date": "2024-01-01",
           "Time": "17:00:00",
           "Link": "http://example.com",
           "Priority": 2
         }'

curl -k -X GET https://url/reminders -H Content-Type:application/json
