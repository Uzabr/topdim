# Рантбук: поднять topdim с нуля (rebuild после компрометации)

> ## ⚡ ВАРИАНТ: ЧИСТЫЙ ЛИСТ (решение 2026-06-25)
> Реальных данных на сервере **нет** → **Stage A (экспорт) и Stage F (восстановление
> данных) ПРОПУСКАЕМ.** БД создаём пустыми — схему построит **Flyway** при первом
> старте сервисов (`spring.flyway.enabled: true`, `ddl-auto: validate`).
> План: **Reinstall ОС этого же сервера** (тот же IP → DNS не трогаем) → база-хардненинг
> → Docker+EasyPanel → новые секреты → инфра (пустые БД) → app → проверка → локдаун.
> Все секреты — **НОВЫЕ**. Со старого сервера НИЧЕГО не копируем.

> Цель: чистый сервер вместо скомпрометированного (руткит). Принцип:
> **ничего со старого хоста не переносим; все секреты — НОВЫЕ.**
>
> Легенда: **[NEW]** = на переустановленном сервере, **[LOCAL/UI]** = у тебя.

## Инвентарь (снят 2026-06-25)
- **App (GHCR `ghcr.io/uzabr/topdim/<svc>`):** api-gateway, identity-service, coupon-service, order-service, payment-service, notification-service, media-service, discovery-server, web-app, admin-app, partner.
- **Инфра:** postgres:17, redis:7, rabbitmq:3-management, minio, pgweb.
- **БД:** topdim_identity, topdim_coupon, topdim_order, topdim_payment, topdim_notification, topdim_user.
- **Данные:** postgres bind `/etc/easypanel/projects/topdim/postgres/data`; minio volume `topdim_minio_data`.
- **Платформа:** EasyPanel + traefik 3.6.7 (ingress+TLS). Сети: `easypanel`, `easypanel-topdim`.
- **Отдельно:** `n8n` (n8nio/n8n) — решить, нужен ли (возможный вектор входа; если не нужен — НЕ переносить).
- **Секреты для ротации:** JWT_SECRET, DB (POSTGRES_PASSWORD + DB_PASSWORD), REDIS_PASSWORD/SPRING_DATA_REDIS_PASSWORD, RABBITMQ_DEFAULT_PASS, MINIO_ROOT_USER/PASSWORD + MINIO_ACCESS/SECRET_KEY, MAIL_PASSWORD, GHCR_PAT, root pw, SSH-ключи.

---

## STAGE A — ПРОПУЩЕНА (чистый лист, данных нет)
Экспорт БД/медиа не нужен. Со старого сервера ничего не сохраняем.
Перед reinstall: убедиться, что DNS A-записи указывают на этот IP (после reinstall
IP тот же, менять не придётся).

---

## STAGE B — Новый сервер + база хардненинга [NEW, root]
```bash
# Свежий Ubuntu 24.04 LTS (новый Contabo VPS или reinstall из чистого образа)
apt update && apt -y full-upgrade
apt -y install ufw fail2ban unattended-upgrades auditd aide rkhunter curl

# Админ-юзер + ключи (вставь свой публичный ключ)
adduser --disabled-password --gecos "" deploy
usermod -aG sudo deploy
mkdir -p /home/deploy/.ssh && chmod 700 /home/deploy/.ssh
echo "ssh-ed25519 AAAA...ТВОЙ_НОВЫЙ_КЛЮЧ..." > /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys && chown -R deploy:deploy /home/deploy/.ssh

# SSH хардненинг
sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin prohibit-password/' /etc/ssh/sshd_config
sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
echo -e "MaxAuthTries 3\nAllowUsers deploy" >> /etc/ssh/sshd_config
systemctl restart ssh

# Фаервол (default deny; SSH с твоего IP; сайт 80/443)
ufw default deny incoming; ufw default allow outgoing
ufw allow from 202.79.184.53 to any port 22 proto tcp
ufw allow 80/tcp; ufw allow 443/tcp
ufw enable

# Авто-патчи + базовый аудит
dpkg-reconfigure -plow unattended-upgrades
systemctl enable --now auditd
aideinit   # базлайн целостности
```

---

## STAGE C — Docker + EasyPanel [NEW, root]
```bash
# Docker CE
curl -fsSL https://get.docker.com | sh

# Хардненинг демона
cat >/etc/docker/daemon.json <<'JSON'
{ "no-new-privileges": true, "live-restore": true,
  "log-driver": "json-file", "log-opts": {"max-size":"10m","max-file":"3"},
  "default-ulimits": {"nofile":{"Name":"nofile","Hard":65536,"Soft":65536}} }
JSON
systemctl restart docker
docker swarm init   # если нужен swarm-режим как раньше

# EasyPanel (их инсталлятор)
docker run --rm -it -v /etc/easypanel:/etc/easypanel -v /var/run/docker.sock:/var/run/docker.sock \
  easypanel/easypanel setup
# → открыть https://NEW_IP:3000 (с твоего IP), создать админа панели
```

---

## STAGE D — Сгенерировать НОВЫЕ секреты [LOCAL/NEW]
```bash
# Стойкие значения (в менеджер паролей!)
JWT_SECRET=$(openssl rand -base64 48)
DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
REDIS_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
RABBITMQ_PASS=$(openssl rand -base64 24 | tr -d '/+=')
MINIO_ROOT_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
echo "JWT=$JWT_SECRET"; echo "DB=$DB_PASSWORD"  # сохранить, потом очистить историю
# Также: новый root-пароль, новая SSH-пара для GitHub Actions, новый GHCR_PAT (в GitHub).
```

---

## STAGE E — Инфра-сервисы [UI EasyPanel или compose]
Поднять с **новыми** секретами и постоянными томами:
- **postgres:17** — `POSTGRES_USER=postgres`, `POSTGRES_PASSWORD=$DB_PASSWORD`, том на data.
  Создать БД: `for d in topdim_identity topdim_coupon topdim_order topdim_payment topdim_notification topdim_user; do docker exec <pg> createdb -U postgres $d; done`
- **redis:7** — `--requirepass $REDIS_PASSWORD`.
- **rabbitmq:3-management** — `RABBITMQ_DEFAULT_USER=topdim`, `RABBITMQ_DEFAULT_PASS=$RABBITMQ_PASS` (декларативно, чтобы юзер был durable — это ломалось раньше).
- **minio** — `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` новые, том `minio_data`.

---

## STAGE F — ПРОПУЩЕНА (данных нет)
БД остаются пустыми. **Flyway** построит схему автоматически при первом старте
сервисов (identity/coupon/order/... — у каждого свои миграции `db/migration`).
Ничего восстанавливать не нужно.

---

## STAGE G — App-сервисы [UI EasyPanel / CD]
- Залогиниться в GHCR новым PAT: `echo $NEW_GHCR_PAT | docker login ghcr.io -u uzabr --password-stdin`.
- Поднять (как раньше) с **новым** env:
  discovery-server → api-gateway → identity → coupon → order → payment → notification → media → web-app/admin-app/partner.
- Env по сервисам — из инвентаря (ключи известны), значения секретов — НОВЫЕ; не-секретные (CORS_ORIGIN_*, EUREKA_HOST, PORT, MINIO_BUCKET, MAIL_USERNAME) — из `env_reference.txt`.
- Обновить **GitHub Secrets**: `SSH_HOST`(новый IP), `SSH_PRIVATE_KEY`(новая пара), `GHCR_PAT`, `JWT_SECRET`, `TELEGRAM_*`.

---

## STAGE H — DNS + TLS + проверка [UI/LOCAL]
- Перевести A-записи `sizbiz.uz`, `api.sizbiz.uz` (+ при необходимости admin/partner) на **новый IP**.
- EasyPanel/traefik выпишет Let's Encrypt автоматически (нужен открытый 80/443).
- Проверить: главная 200, `/api/v1/categories` 200, логин, **M4 cookie-флоу** (Set-Cookie httpOnly), forged-JWT → 401, `/actuator/metrics` снаружи → 404.

---

## STAGE I — Локдаун нового + снос старого
```bash
# Жёсткий локдаун (как на старом): SSH только с твоего IP, всё лишнее закрыто, netfilter-persistent save.
# fail2ban активен, rkhunter по cron, алерты CPU/трафик (Фаза 5 плана).
```
- Только ПОСЛЕ полной проверки нового → снять снапшот старого (форензика) и **уничтожить** старый сервер.
- Сменить пароли/ключи везде, где использовались старые (GitHub, менеджер паролей).

---

## STAGE J — Фаза 3: авто-деплой (CI → EasyPanel webhook + пред-деплой бэкап)

Цель: мердж в `main` → CI собирает образы (Фаза 2) → авто-деплой **только изменённых**
сервисов. CI по SSH заходит deploy-юзером (порт 49222) → пред-деплой бэкап → дёргает
EasyPanel deploy-webhook (`localhost:3000/api/deploy/<token>`; порт 3000 закрыт снаружи).
Источник правды скриптов — git: `scripts/deploy/deploy.sh`, `scripts/deploy/webhook-tokens.env.example`.

### J0 — ПРЕРЕКВИЗИТ (блокер): рабочие GHCR-креды в EasyPanel
EasyPanel сейчас **не может** тянуть приватные образы GHCR (лог деплоя:
`(HTTP code 500) ... unauthorized`) — поэтому голый webhook НЕ задеплоит новый образ.
Починить один раз в UI: **EasyPanel → Settings → Registries → Add**: registry `ghcr.io`,
username `uzabr`, password = **новый GHCR PAT со scope `read:packages`**. Проверка: любой
сервис → «Развернуть» → в логе успешный `Pulling image …:latest` без `unauthorized`.
(Иначе fallback — публичные GHCR-пакеты; но для этого профиля лучше PAT.)

### J1 — deploy-юзер (минимальный, key-only) [NEW, root]
```bash
adduser --disabled-password --gecos "" deploy
mkdir -p /home/deploy/.ssh && chmod 700 /home/deploy/.ssh && chown deploy:deploy /home/deploy/.ssh
```

### J2 — скрипт + токены на сервере [NEW, root]
```bash
# Скопировать из репо (git = источник правды), не редактировать на месте:
install -o deploy -g deploy -m 700 scripts/deploy/deploy.sh /home/deploy/deploy.sh
# Токены: заполнить реальными из EasyPanel (service → «Развёртывание» → «Триггер развёртывания»).
cp scripts/deploy/webhook-tokens.env.example /home/deploy/webhook-tokens.env
#   ⚠️ Сначала «обновить токен» тому сервису, чей токен светился в чате.
chown deploy:deploy /home/deploy/webhook-tokens.env && chmod 600 /home/deploy/webhook-tokens.env
```

### J3 — sudoers: разрешить deploy-юзеру ТОЛЬКО backup-скрипт [NEW, root]
```bash
echo 'deploy ALL=(root) NOPASSWD: /root/backup/topdim-backup.sh' > /etc/sudoers.d/deploy-backup
chmod 440 /etc/sudoers.d/deploy-backup && visudo -cf /etc/sudoers.d/deploy-backup
```

### J4 — forced-command CI-ключ [NEW, root/deploy]
Сгенерировать НОВУЮ пару для CI (локально): `ssh-keygen -t ed25519 -C github-actions-deploy -f deploy_ci`.
Публичный ключ — в authorized_keys deploy-юзера с forced command (ключ может ТОЛЬКО
триггерить деплой, не shell):
```bash
# /home/deploy/.ssh/authorized_keys  (owner deploy, chmod 600):
command="/home/deploy/deploy.sh",no-port-forwarding,no-x11-forwarding,no-agent-forwarding,no-pty ssh-ed25519 AAAA...deploy_ci.pub...
```
`AllowUsers` в SSH-хардненинге должен включать `deploy` (добавить рядом с `abror`).

### J5 — GitHub Secrets (repo) [LOCAL/UI]
- `DEPLOY_SSH_KEY` = приватный `deploy_ci` (весь файл).
- `DEPLOY_SSH_HOST` = `167.86.107.204` · `DEPLOY_SSH_PORT` = `49222` · `DEPLOY_SSH_USER` = `deploy`.
- `DEPLOY_KNOWN_HOSTS` = вывод `ssh-keyscan -p 49222 167.86.107.204` (**сверить** фингерпринт
  с реальным ключом сервера, иначе пиннишь MITM).
- Дозаполнить `TELEGRAM_BOT_TOKEN` + `TELEGRAM_CHAT_ID` (для нотификаций деплоя).
- Токены webhook в GitHub **НЕ кладём** — они только на сервере.

### J6 — проверка end-to-end
```bash
# Ручной триггер (форс-команда → deploy.sh):
ssh -i deploy_ci -o IdentitiesOnly=yes -p 49222 deploy@167.86.107.204 "web-app"
#   → бэкап в S3 + EasyPanel передеплоил ТОЛЬКО web-app; неизвестное имя → отказ (exit 3).
```
Затем: мелкая правка одного сервиса → PR → merge → в Actions job `deploy` показывает список
из одного сервиса; docs-only пуш → `detect-deploy` пуст → `deploy` пропущен. Сайт живой
(`curl -I https://sizbiz.uz` 200), forged-JWT → 401. Telegram — нотификация со списком+статусом.

---

## Чек-лист ротации (отметить при выполнении)
- [ ] root-пароль (новый сервер) · [ ] SSH deploy-пара (+ GitHub `SSH_PRIVATE_KEY`)
- [ ] JWT_SECRET · [ ] DB_PASSWORD/POSTGRES_PASSWORD · [ ] REDIS_PASSWORD
- [ ] RABBITMQ_DEFAULT_PASS · [ ] MINIO root + access/secret · [ ] MAIL_PASSWORD
- [ ] GHCR_PAT · [ ] удалить `env_reference.txt` · [ ] 2GIS-ключ по домену
- [ ] почистить локальную историю shell от значений секретов

> Порядок крупно (чистый лист): **Reinstall ОС → B,C (база+Docker/EasyPanel) → D (новые
> секреты) → E (инфра, пустые БД) → G (app, Flyway строит схему) → H (TLS/проверка) →
> I (локдаун)**. Stage A и F пропущены (данных нет). IP тот же — DNS не трогаем.
