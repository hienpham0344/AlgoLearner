#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

trap 'printf "Local email backend failed at line %s (exit %s).\n" "$LINENO" "$?" >&2' ERR

backend_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$backend_dir"

# Read supported settings literally; never execute the contents of .env.
if [[ -f .env ]]; then
  dotenv_line_number=0
  while IFS= read -r dotenv_line || [[ -n "$dotenv_line" ]]; do
    dotenv_line_number=$((dotenv_line_number + 1))
    dotenv_line="${dotenv_line%$'\r'}"
    if [[ "$dotenv_line" =~ ^[[:space:]]*(#|$) ]]; then
      continue
    fi
    if [[ ! "$dotenv_line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=(.*)$ ]]; then
      printf 'Invalid .env line %s; expected KEY=VALUE (contents hidden).\n' "$dotenv_line_number" >&2
      exit 2
    fi
    dotenv_key="${BASH_REMATCH[1]}"
    dotenv_value="${BASH_REMATCH[2]}"
    case "$dotenv_key" in
      MAIL_HOST|MAIL_PORT|MAIL_USERNAME|MAIL_PASSWORD|MAIL_FROM|JWT_SECRET|OTP_HMAC_SECRET|INTERNAL_JWT_ISSUER|ADMIN_BOOTSTRAP_EMAIL|ADMIN_BOOTSTRAP_USERNAME|ADMIN_BOOTSTRAP_PASSWORD|ALGOLEARN_EMAIL_PORT|DB_URL|DB_USERNAME|DB_PASSWORD|AVATAR_DIRECTORY) ;;
      *) continue ;;
    esac
    dotenv_value="${dotenv_value#"${dotenv_value%%[![:space:]]*}"}"
    dotenv_value="${dotenv_value%"${dotenv_value##*[![:space:]]}"}"
    if [[ ${#dotenv_value} -ge 2 && ( "$dotenv_value" == \"*\" || "$dotenv_value" == \'*\' ) ]]; then
      dotenv_value="${dotenv_value:1:${#dotenv_value}-2}"
    fi
    if [[ ! -v "$dotenv_key" ]]; then
      export "$dotenv_key=$dotenv_value"
    fi
  done < .env
fi

if ! command -v java >/dev/null 2>&1; then
  for java_candidate in "$HOME"/.local/opt/jdk-*/bin/java "$HOME"/.jdks/jdk-*/bin/java; do
    if [[ -x "$java_candidate" ]]; then
      JAVA_HOME="${java_candidate%/bin/java}"
      export JAVA_HOME
      PATH="$JAVA_HOME/bin:$PATH"
      export PATH
      break
    fi
  done
fi
if ! command -v java >/dev/null 2>&1; then
  printf 'Java 21 is required; set JAVA_HOME or install a JDK.\n' >&2
  exit 2
fi

mvn_bin="${MVN_BIN:-mvn}"
if ! command -v "$mvn_bin" >/dev/null 2>&1; then
  for maven_candidate in "$HOME"/.m2/wrapper/dists/apache-maven-*/*/bin/mvn; do
    if [[ -x "$maven_candidate" ]]; then
      mvn_bin="$maven_candidate"
      break
    fi
  done
fi
if ! command -v "$mvn_bin" >/dev/null 2>&1; then
  printf 'Maven 3.9 is required; set MVN_BIN or install Maven.\n' >&2
  exit 2
fi

if [[ -z "${MAIL_USERNAME:-}" ]]; then
  if [[ ! -t 0 ]]; then
    printf 'Set MAIL_USERNAME to your Gmail sender address.\n' >&2
    exit 2
  fi
  read -r -p 'Gmail sender address: ' MAIL_USERNAME
  export MAIL_USERNAME
fi
if [[ -z "${MAIL_PASSWORD:-}" ]]; then
  if [[ ! -t 0 ]]; then
    printf 'Set MAIL_PASSWORD to a Gmail App Password.\n' >&2
    exit 2
  fi
  read -r -s -p 'Gmail App Password: ' MAIL_PASSWORD
  printf '\n'
  export MAIL_PASSWORD
fi
if [[ -z "$MAIL_USERNAME" || -z "$MAIL_PASSWORD" ]]; then
  printf 'Gmail sender address and App Password are required.\n' >&2
  exit 2
fi

MAIL_FROM="${MAIL_FROM:-$MAIL_USERNAME}"
export MAIL_FROM
if [[ -z "${JWT_SECRET:-}" ]]; then
  printf 'Set a persistent JWT_SECRET in backend/.env.\n' >&2
  exit 2
fi
if [[ -z "${OTP_HMAC_SECRET:-}" ]]; then
  printf 'Set a persistent OTP_HMAC_SECRET in backend/.env.\n' >&2
  exit 2
fi

for required_setting in DB_URL DB_USERNAME DB_PASSWORD; do
  if [[ -z "${!required_setting:-}" ]]; then
    printf 'Set %s in backend/.env.\n' "$required_setting" >&2
    exit 2
  fi
done
# Validate before invoking Maven; never display connection details.
python3 - <<'CHECK_SUPABASE'
import os
from urllib.parse import urlsplit, parse_qs
url = os.environ['DB_URL']
parsed = urlsplit(url.removeprefix('jdbc:'))
host = parsed.hostname or ''
if (not url.startswith('jdbc:postgresql://') or not host.endswith(('.supabase.co', '.pooler.supabase.com'))
        or parsed.username is not None or parsed.password is not None
        or parse_qs(parsed.query).get('sslmode') not in [['require'], ['verify-ca'], ['verify-full']]
        or any(value in url for value in ['PROJECT_REF', 'HOST', 'REGION'])
        or os.environ['DB_PASSWORD'] == 'YOUR_DATABASE_PASSWORD'):
    raise SystemExit('Set real Supabase JDBC settings with sslmode=require in backend/.env; connection details hidden.')
CHECK_SUPABASE

# Keep credentials in the environment, not process arguments. Flyway owns schema changes.
export SPRING_DATASOURCE_URL="$DB_URL" SPRING_DATASOURCE_USERNAME="$DB_USERNAME" SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate SPRING_FLYWAY_ENABLED=true
export SERVER_PORT="${ALGOLEARN_EMAIL_PORT:-8080}"
printf 'Starting backend on port %s with Supabase PostgreSQL data.\n' "$SERVER_PORT"
exec "$mvn_bin" --batch-mode --no-transfer-progress spring-boot:run \
  -Dspring-boot.run.profiles=internal-auth
