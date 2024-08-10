include common.mk
include secure.mk

.PHONY: test deps job all

all: config.yaml
	make -C store reminders.yaml

config.yaml:
	cp config.yaml.tpt config.yaml

test:
	PYTHONPATH=$(PYTHONPATH) make -C data test
	PYTHONPATH=$(PYTHONPATH) make -C actions test
	PYTHONPATH=$(PYTHONPATH) make -C job test
	PYTHONPATH=$(PYTHONPATH) make -C spell test

clean:
	PYTHONPATH=$(PYTHONPATH) make -C data clean
	PYTHONPATH=$(PYTHONPATH) make -C actions clean
	PYTHONPATH=$(PYTHONPATH) make -C job clean
	PYTHONPATH=$(PYTHONPATH) make -C spell clean
	PYTHONPATH=$(PYTHONPATH) make -C client/android clean

job:
	NUDGE_STORE_PATH=$(NUDGE_STORE_PATH) NUDGE_CONFIG_PATH=$(NUDGE_CONFIG_PATH) NUDGE_EMAIL_PASSWD=$(NUDGE_EMAIL_PASSWD) PYTHONPATH=$(PYTHONPATH) make -C job run

job-all:
	NUDGE_STORE_PATH=$(NUDGE_STORE_PATH) NUDGE_CONFIG_PATH=$(NUDGE_CONFIG_PATH) NUDGE_EMAIL_PASSWD=$(NUDGE_EMAIL_PASSWD) PYTHONPATH=$(PYTHONPATH) make -C job run-all

job-dry:
	NUDGE_STORE_PATH=$(NUDGE_STORE_PATH) NUDGE_CONFIG_PATH=$(NUDGE_CONFIG_PATH) NUDGE_EMAIL_PASSWD=$(NUDGE_EMAIL_PASSWD) PYTHONPATH=$(PYTHONPATH) make -C job dryrun

job-dry-all:
	NUDGE_STORE_PATH=$(NUDGE_STORE_PATH) NUDGE_CONFIG_PATH=$(NUDGE_CONFIG_PATH) NUDGE_EMAIL_PASSWD=$(NUDGE_EMAIL_PASSWD) PYTHONPATH=$(PYTHONPATH) make -C job dryrun-all

config:
	touch /tmp/pass
	make -C client/android config

backup:
	./backup.sh # create one
