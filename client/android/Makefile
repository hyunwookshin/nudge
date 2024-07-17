config: ./app/src/main/java/com/hyunwookshin/nudge/ApiClient.kt

./app/src/main/java/com/hyunwookshin/nudge/ApiClient.kt:
	cp ApiClient.kt.tpt ./app/src/main/java/com/hyunwookshin/nudge/ApiClient.kt
	sed -i '' 's#FILL_IN_HOSTNAME#$(shell cat nudge_hostname)#g' ./app/src/main/java/com/hyunwookshin/nudge/ApiClient.kt

clean:
	rm -f ./app/src/main/java/com/hyunwookshin/nudge/ApiClient.kt
