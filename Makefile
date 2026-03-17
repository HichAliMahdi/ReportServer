SHELL := /bin/bash

APP_NAME := report-server
APP_VERSION := 1.0.0
APP_JAR := target/$(APP_NAME)-$(APP_VERSION).jar
MVN ?= mvn
DOCKER_COMPOSE := $(shell if docker compose version >/dev/null 2>&1; then echo "docker compose"; elif command -v docker-compose >/dev/null 2>&1; then echo "docker-compose"; fi)

.DEFAULT_GOAL := help

.PHONY: help prepare-dirs clean compile build package test verify run run-postgresql run-mysql run-jar rebuild docker-up docker-up-prod docker-down docker-logs docker-ps

help: ## Show available Make targets
	@awk 'BEGIN {FS = ":.*## "; printf "Available targets:\n\n"} /^[a-zA-Z0-9_.-]+:.*## / {printf "  %-16s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

prepare-dirs: ## Create local data directories used by the app
	@mkdir -p data/reports data/datasource_files data/thumbnails data/exports data/scheduled_output

clean: ## Remove Maven build output and reset local H2 credentials data
	$(MVN) clean
	@rm -f data/reportserver.mv.db data/reportserver.trace.db
	@echo "Local H2 database files removed (credentials/passwords reset)."

compile: prepare-dirs ## Compile the application without packaging
	$(MVN) compile

build: prepare-dirs ## Build the runnable jar
	$(MVN) clean package

package: build ## Alias for build

test: prepare-dirs ## Run the test suite
	$(MVN) test

verify: prepare-dirs ## Run the full Maven verification lifecycle
	$(MVN) clean verify

run: prepare-dirs ## Run the application with Spring Boot Maven plugin
	$(MVN) spring-boot:run

run-postgresql: prepare-dirs ## Run app with PostgreSQL profile (expects PostgreSQL env vars)
	SPRING_PROFILES_ACTIVE=postgresql $(MVN) spring-boot:run

run-mysql: prepare-dirs ## Run app with MySQL profile (expects MySQL env vars)
	SPRING_PROFILES_ACTIVE=mysql $(MVN) spring-boot:run

run-jar: prepare-dirs ## Run the packaged jar from target/
	@test -f $(APP_JAR) || (echo "Jar not found: $(APP_JAR). Run 'make build' first." && exit 1)
	java -jar $(APP_JAR)

rebuild: clean build ## Clean and rebuild the project

docker-up: prepare-dirs ## Start the Docker Compose development stack
	@test -n "$(DOCKER_COMPOSE)" || (echo "Docker Compose is not available." && exit 1)
	$(DOCKER_COMPOSE) up -d

docker-up-prod: prepare-dirs ## Start the Docker Compose production stack
	@test -n "$(DOCKER_COMPOSE)" || (echo "Docker Compose is not available." && exit 1)
	$(DOCKER_COMPOSE) -f docker-compose.yml -f docker-compose.prod.yml up -d

docker-down: ## Stop the Docker Compose stack
	@test -n "$(DOCKER_COMPOSE)" || (echo "Docker Compose is not available." && exit 1)
	$(DOCKER_COMPOSE) down

docker-logs: ## Follow Docker Compose logs
	@test -n "$(DOCKER_COMPOSE)" || (echo "Docker Compose is not available." && exit 1)
	$(DOCKER_COMPOSE) logs -f

docker-ps: ## Show Docker Compose container status
	@test -n "$(DOCKER_COMPOSE)" || (echo "Docker Compose is not available." && exit 1)
	$(DOCKER_COMPOSE) ps