#!/usr/bin/env bash
set -euo pipefail

backend_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$backend_dir"

exec "${MVN_BIN:-mvn}" --batch-mode --no-transfer-progress spring-boot:run \
  -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.profiles=mock,dev,integration \
  "-Dspring-boot.run.arguments=--server.port=${ALGOLEARN_INTEGRATION_PORT:-18080} --spring.autoconfigure.exclude= --spring.datasource.url=jdbc:h2:mem:integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE --spring.datasource.username=sa --spring.datasource.password= --spring.jpa.hibernate.ddl-auto=create-drop --spring.flyway.enabled=false --spring.jpa.defer-datasource-initialization=true --spring.sql.init.mode=always --spring.sql.init.data-locations=file:$backend_dir/src/test/resources/integration-seed.sql"
