#!/usr/bin/env bash
set -euo pipefail
# Trusted Linux worker only. Never mount Docker socket into API or learner containers.
: "${JUDGE_CLASSPATH:?Set JUDGE_CLASSPATH to target/classes plus resolved runtime dependency jars}"
exec java -cp "$JUDGE_CLASSPATH" com.algolearner.platform.judge.JudgeWorkerMain
