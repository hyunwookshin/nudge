config: web/script.js web/privacy.html web/terms.html config-web

config-web:
	sudo cp -r web $(WEB_PUBLISH_DIR)

web/script.js:
	cp script.js.tpt web/script.js
	sed -i 's#FILL_IN_HOSTNAME#$(shell cat nudge_hostname)#g' web/script.js

clean:
	rm -f web/script.js
