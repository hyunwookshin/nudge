export GIT_ROOT := $(shell git rev-parse --show-toplevel )  
export PYTHONPATH := $(PYTHONPATH):$(GIT_ROOT)
export NUDGE_CONFIG_PATH := $(shell git rev-parse --show-toplevel )/config.yaml
export NUDGE_STORE_PATH := $(shell git rev-parse --show-toplevel )/store
