config: ./app/src/main/java/com/hyunwookshin/nudge/ApiClient.kt ./app/src/main/java/com/hyunwookshin/nudge/ApiKey.kt

./app/src/main/java/com/hyunwookshin/nudge/ApiClient.kt:
	cp ApiClient.kt.tpt ./app/src/main/java/com/hyunwookshin/nudge/ApiClient.kt
	sed -i '' 's#FILL_IN_HOSTNAME#$(shell cat nudge_hostname)#g' ./app/src/main/java/com/hyunwookshin/nudge/ApiClient.kt

./app/src/main/java/com/hyunwookshin/nudge/ApiKey.kt:
	cp ApiKey.kt.tpt ./app/src/main/java/com/hyunwookshin/nudge/ApiKey.kt
	sed -i '' 's#FILL_IN_API_KEY#$(shell cat nudge_api_key)#g' ./app/src/main/java/com/hyunwookshin/nudge/ApiKey.kt

clean:
	rm -f ./app/src/main/java/com/hyunwookshin/nudge/ApiClient.kt
	rm -f ./app/src/main/java/com/hyunwookshin/nudge/ApiKey.kt
