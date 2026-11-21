#!/usr/bin/env bash
set -euo pipefail
# Integration UI uses the same real Supabase backend. No temporary database or seed data.
backend_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export ALGOLEARN_EMAIL_PORT="${ALGOLEARN_INTEGRATION_PORT:-8080}"
exec bash "$backend_dir/scripts/run-local-email.sh"
