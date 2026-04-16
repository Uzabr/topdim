#!/usr/bin/env bash
# Запуск бота с системным Python 3.9 (где установлены зависимости)
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Находим правильный python3 (системный, где установлен aiohttp)
PYTHON="/usr/bin/python3"

if ! "$PYTHON" -c "import aiohttp" 2>/dev/null; then
    echo "ERROR: aiohttp не найден. Установите: $PYTHON -m pip install -r requirements.txt"
    exit 1
fi

echo "Using: $PYTHON ($($PYTHON --version 2>&1))"
exec "$PYTHON" -m src.main
