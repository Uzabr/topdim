#!/bin/bash
# ============================================================
# TopDim — Запуск всех сервисов одной командой
# Использование: ./start-all.sh [start|stop|restart|status]
# ============================================================

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
mkdir -p "$LOG_DIR"

# Загружаем .env файл, если он существует (чтобы Java-сервисы видели пароли)
if [ -f "$PROJECT_DIR/.env" ]; then
  echo "Загрузка переменных окружения из .env..."
  set -a
  source <(sed -e '/^\s*$/d' -e '/^\s*#/d' "$PROJECT_DIR/.env")
  set +a
else
  echo "ВНИМАНИЕ: Файл .env не найден. Сервисы могут не запуститься."
fi

# Цвета
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Порядок и порты сервисов
INFRA_SERVICES=(
  "infrastructure:discovery-server:8761"
  "infrastructure:api-gateway:8080"
)

BACKEND_SERVICES=(
  "services:identity-service:8081"
  "services:coupon-service:8083"
  "services:order-service:8084"
  "services:payment-service:8085"
  "services:bazaar-service:8086"
  "services:notification-service:8087"
  "services:media-service:8088"
)

get_name() { echo "$1" | cut -d: -f2; }
get_port() { echo "$1" | cut -d: -f3; }
get_gradle() { echo ":$(echo "$1" | cut -d: -f1):$(echo "$1" | cut -d: -f2)"; }

# Проверка порта: ss для Linux (не требует sudo), lsof для Mac
check_port() {
  local port=$1
  if command -v ss &>/dev/null; then
    ss -tlnp 2>/dev/null | grep -q ":${port} "
  else
    lsof -i ":$port" &>/dev/null
  fi
}

start_service() {
  local svc="$1"
  local name=$(get_name "$svc")
  local port=$(get_port "$svc")
  local gradle=$(get_gradle "$svc")
  local log_file="$LOG_DIR/$name.log"

  # Проверяем, не запущен ли уже
  if check_port "$port"; then
    echo -e "  ${YELLOW}⚡ $name${NC} уже запущен (порт $port)"
    return
  fi

  echo -e "  ${CYAN}🚀 Запуск $name${NC} (порт $port)..."
  cd "$PROJECT_DIR"
  nohup ./gradlew "$gradle:bootRun" --console=plain > "$log_file" 2>&1 &
  echo $! > "$LOG_DIR/$name.pid"
}

wait_for_port() {
  local port=$1
  local name=$2
  local max_wait=60
  local waited=0

  while ! check_port "$port"; do
    sleep 2
    waited=$((waited + 2))
    if [ $waited -ge $max_wait ]; then
      echo -e "  ${RED}✗ $name (порт $port) — таймаут ${max_wait}с${NC}"
      return 1
    fi
  done
  echo -e "  ${GREEN}✓ $name${NC} запущен (порт $port, ${waited}с)"
}

stop_all() {
  echo -e "\n${RED}🛑 Остановка всех сервисов...${NC}\n"
  
  # Останавливаем Java-сервисы
  for svc in "${BACKEND_SERVICES[@]}" "${INFRA_SERVICES[@]}"; do
    local name=$(get_name "$svc")
    local port=$(get_port "$svc")
    local pid_file="$LOG_DIR/$name.pid"
    
    if [ -f "$pid_file" ]; then
      local pid=$(cat "$pid_file")
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null || true
        echo -e "  ${RED}■ $name${NC} остановлен (PID $pid)"
      fi
      rm -f "$pid_file"
    fi

    # Также убиваем по порту на всякий случай
    local port_pid=$(lsof -ti ":$port" 2>/dev/null)
    if [ -n "$port_pid" ]; then
      kill $port_pid 2>/dev/null || true
    fi
  done

  # Останавливаем Docker-контейнеры (НЕ удаляем, НЕ закрываем Docker Desktop)
  echo -e "\n${CYAN}🐳 Остановка Docker-контейнеров...${NC}"
  cd "$PROJECT_DIR"
  docker compose stop 2>/dev/null && echo -e "  ${GREEN}✓ Контейнеры остановлены${NC}" || echo -e "  ${YELLOW}⚠ Docker не отвечает или контейнеры уже остановлены${NC}"

  echo -e "\n${GREEN}Все сервисы остановлены. Docker Desktop продолжает работать.${NC}"
}

show_status() {
  echo -e "\n${CYAN}📊 Статус сервисов TopDim${NC}\n"
  echo "────────────────────────────────────────"
  printf "  %-25s %-8s %s\n" "СЕРВИС" "ПОРТ" "СТАТУС"
  echo "────────────────────────────────────────"
  
  for svc in "${INFRA_SERVICES[@]}" "${BACKEND_SERVICES[@]}"; do
    local name=$(get_name "$svc")
    local port=$(get_port "$svc")
    
    if check_port "$port"; then
      printf "  ${GREEN}%-25s %-8s ✓ Работает${NC}\n" "$name" "$port"
    else
      printf "  ${RED}%-25s %-8s ✗ Не запущен${NC}\n" "$name" "$port"
    fi
  done
  
  echo "────────────────────────────────────────"
  
  # Docker
  echo ""
  echo -e "${CYAN}🐳 Docker контейнеры${NC}"
  echo "────────────────────────────────────────"
  docker compose -f "$PROJECT_DIR/docker-compose.yml" ps --format "table {{.Name}}\t{{.Status}}" 2>/dev/null || echo "  Docker Compose не активен"
  echo ""
}

start_all() {
  echo -e "\n${GREEN}╔══════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║     🚀 TopDim — Запуск платформы     ║${NC}"
  echo -e "${GREEN}╚══════════════════════════════════════╝${NC}\n"

  # 1. Docker
  echo -e "${CYAN}[1/3] 🐳 Docker инфраструктура${NC}"
  cd "$PROJECT_DIR"
  # Всегда выполняем up -d, чтобы поднять остановленные контейнеры
  docker compose up -d 2>/dev/null
  echo -e "  ${GREEN}✓ PostgreSQL, Redis, RabbitMQ, MinIO${NC}"
  sleep 2

  # Ждём готовности инфраструктуры (чтобы Flyway/AMQP не падали на старте)
  wait_for_port 5433 "postgres"
  wait_for_port 6380 "redis"
  wait_for_port 5673 "rabbitmq"
  wait_for_port 9000 "minio"

  # 2. Infrastructure
  echo -e "\n${CYAN}[2/3] 🏗️  Инфраструктурные сервисы${NC}"
  for svc in "${INFRA_SERVICES[@]}"; do
    start_service "$svc"
  done

  # Ждём Eureka
  echo -e "\n  ⏳ Ожидание Eureka..."
  wait_for_port 8761 "discovery-server"
  sleep 3

  # Ждём API Gateway
  wait_for_port 8080 "api-gateway"

  # 3. Backend
  echo -e "\n${CYAN}[3/3] ⚙️  Бекенд-сервисы${NC}"
  for svc in "${BACKEND_SERVICES[@]}"; do
    start_service "$svc"
  done

  # Ждём все сервисы
  echo -e "\n  ⏳ Ожидание запуска сервисов..."
  for svc in "${BACKEND_SERVICES[@]}"; do
    wait_for_port "$(get_port "$svc")" "$(get_name "$svc")"
  done

  echo -e "\n${GREEN}╔══════════════════════════════════════╗${NC}"
  echo -e "${GREEN}║     ✅ Все сервисы запущены!          ║${NC}"
  echo -e "${GREEN}╚══════════════════════════════════════╝${NC}"
  echo ""
  echo -e "  📊 Eureka:    ${CYAN}http://localhost:8761${NC}"
  echo -e "  🌐 Gateway:   ${CYAN}http://localhost:8080${NC}"
  echo -e "  🖥️  Frontend:  ${CYAN}http://localhost:5173${NC}"
  echo -e "  🐰 RabbitMQ:  ${CYAN}http://localhost:15673${NC}"
  echo -e "  📦 MinIO:     ${CYAN}http://localhost:9001${NC}"
  echo ""
  echo -e "  Логи: ${YELLOW}$LOG_DIR/<service>.log${NC}"
  echo -e "  Стоп: ${RED}./start-all.sh stop${NC}"
  echo ""
}

restart_all() {
  echo -e "\n${YELLOW}🔄 Перезапуск всей платформы...${NC}\n"
  stop_all
  sleep 2
  start_all
}

# Main
case "${1:-start}" in
  start)   start_all ;;
  stop)    stop_all ;;
  restart) restart_all ;;
  status)  show_status ;;
  *)
    echo "Использование: $0 [start|stop|restart|status]"
    exit 1
    ;;
esac
