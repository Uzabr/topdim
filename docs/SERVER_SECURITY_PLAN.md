# План обеспечения безопасности сервера topdim

> Подготовлен 2026-06-25 после root-компрометации (криптомайнер + руткит
> `libprocesshider`, вход через слабый root-пароль + `PermitRootLogin yes`).
> Containment уже выполнен (см. ниже). **Этот документ — план; выполнять по фазам
> после ревью.**
>
> Методология на основе cybersecurity-скилов: `eradicating-malware-from-infected-systems`
> (NIST RS.MA/RC.RP), `hardening-linux-endpoint-with-cis-benchmark`,
> `hardening-docker-daemon-configuration`, `building-incident-response-playbook`.

## Контекст инцидента (кратко)
- Вход: SSH-брутфорс root (пароль `Passw0rd`, `PermitRootLogin yes`, парольный вход), ~18 июня.
- Нагрузка: майнер `[cryptd]`/`/root/.16` → пулы `141.98.11.51`, `5.189.149.171`; ~5 ядер + ~104 Mbit/s (поймали по счёту провайдера).
- Маскировка: руткит `libprocesshider` через `/etc/ld.so.preload`.
- Персистентность: root-crontab (`/var/tmp/snap`, `~/.X0-lock` каждую минуту).
- **Вывод: root + руткит 7 дней → сервер недоверенный → нужен rebuild, не «чистка».**

---

## ✅ Фаза 0 — Containment (СДЕЛАНО 2026-06-25)
- Убит майнер, снят руткит (`/etc/ld.so.preload` + `libprocesshider.so`), удалён root-crontab и payload'ы.
- C2 заблокированы (OUTPUT DROP `141.98.11.51`, `5.189.149.171`).
- **Локдаун:** SSH(22) только с `202.79.184.53`; 80/443 закрыты для публики (только наш IP); 5672/2377/7946/3000/4789 DROP; IPv6 SSH DROP. Правила сохранены (`netfilter-persistent`). Подтверждено внешне (check-host: timeout со всех узлов).
- `load 10.9 → 0.74`.

---

## 🔴 Фаза 1 — Eradication + Rebuild (приоритет 1, настоящий фикс)
Руткит-уровень компромисса нельзя надёжно вычистить in-place. Пересоздаём с нуля.

1. **Поднять НОВЫЙ сервер** (новый Contabo VPS или переустановка ОС из чистого образа Ubuntu LTS). Старый — не переиспользовать как есть.
2. **Сохранить только ДАННЫЕ, не бинари/конфиг хоста:**
   - `pg_dump` всех БД (`topdim_identity`, `topdim_coupon`, ...) со старого сервера.
   - MinIO-данные (бакеты медиа) — `mc mirror` в безопасное место.
   - ⚠️ Дампы данных — низкий риск, но **проверить** размеры/целостность; НЕ копировать исполняемые файлы, cron, systemd, /root, /home со старого хоста.
3. **Развернуть стек заново из git/образов** (чистые источники): EasyPanel/Docker + образы из GHCR (по digby-sha, не `:latest`), приложение из `main`.
4. **Восстановить данные** из проверенных дампов.
5. **Декоммишн старого сервера** только после успешного переезда (снапшот для форензики при желании).
6. Перед публикацией — пройти Фазы 2-4 на новом сервере.

---

## 🟠 Фаза 2 — Хардненинг ОС (CIS-baseline)
На новом (или текущем до rebuild) сервере:

**SSH (корневая причина инцидента):**
```
# /etc/ssh/sshd_config
PermitRootLogin prohibit-password   # только по ключу, не паролем
PasswordAuthentication no           # убить брутфорс паролей
PubkeyAuthentication yes
MaxAuthTries 3
AllowUsers abror                    # whitelist
```
+ SSH (22) только с доверенного IP на фаерволе (уже сделано), `fail2ban` (уже活).

**Фаервол — default-deny:**
```
iptables -P INPUT DROP   (с ACCEPT lo + established + 22-с-trust + 80/443-публично-через-traefik)
```
Публично наружу — **только 80/443** (сайт), 22 — с админ-IP, всё остальное DROP. Сохранить (`netfilter-persistent`), проверить переживание reboot.

**Учётки и пароли:**
- Сменить root-пароль на стойкий (24+ симв.), хранить в менеджере паролей.
- Удалить/залочить неиспользуемого `ubuntu` (uid 1001), проверить `/etc/passwd` на лишних.
- Никаких слабых/переиспользуемых паролей.

**Автообновления + базовый аудит (CIS):**
```
apt install unattended-upgrades auditd aide rkhunter
```
- `unattended-upgrades` — авто security-патчи.
- `auditd` — журналирование (CIS section 4).
- `AIDE` — контроль целостности файлов (ловит подмену бинарей).
- `rkhunter`/`chkrootkit` — периодический скан руткитов (этот инцидент бы поймался).
- sysctl-хардненинг (CIS section 3): отключить ip_forward где не нужно, rp_filter, ignore ICMP redirects и т.п.

---

## 🟠 Фаза 3 — Хардненинг Docker / Swarm
- **Docker API не выставлять** наружу (никаких 2375/2376; только unix-socket, права `root:docker`).
- **`/etc/docker/daemon.json`:**
```json
{
  "userns-remap": "default",
  "no-new-privileges": true,
  "live-restore": true,
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" },
  "icc": false
}
```
- **DOCKER-USER firewall**: правила DROP/ACCEPT для published-портов (Docker иначе обходит INPUT) — у нас уже так для 5672/3000/80/443.
- **Контейнеры**: non-root user, `read_only` где можно, `cap_drop: ALL` + только нужные cap, `mem_limit`/`cpus` лимиты (лимит CPU не дал бы майнеру сожрать 5 ядер).
- **Образы**: пин по digest, доверенный GHCR, dependabot (уже есть), Trivy-скан образов в CI (опц.).

---

## 🔑 Фаза 4 — Ротация ВСЕХ секретов (обязательно)
root читал весь env → всё скомпрометировано:
- [ ] root-пароль сервера
- [ ] SSH-ключи (перевыпустить пары; обновить GitHub Actions `SSH_PRIVATE_KEY` + authorized_keys)
- [ ] `JWT_SECRET` (gateway + identity) → новый стойкий
- [ ] `DB_PASSWORD` postgres (все сервисы) + при rebuild единый юзер/пароль
- [ ] `GHCR_PAT` (перевыпустить токен)
- [ ] Redis / RabbitMQ / MinIO креды
- [ ] 2GIS-ключ — ограничить по домену (давно в списке)
- [ ] GitHub Secrets обновить под новые значения
- Хранение: значения — в EasyPanel env / Docker secrets, **не в git** (gitleaks уже стоит).

---

## 🟡 Фаза 5 — Мониторинг и детект (чтобы поймать РАНЬШЕ провайдера)
Этот инцидент 7 дней никто не видел — поймал счёт Contabo. Нужно:
- **Алерты по CPU и трафику** (Prometheus/Grafana уже есть — добавить node-exporter + правила: load > N, исходящий трафик > X, новый процесс с высоким CPU).
- **Telegram-алерты** на аномалии (инфра TG уже готова — доделать секреты `TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID`).
- **fail2ban** + мониторинг `auth.log` (неудачные SSH).
- **AIDE/rkhunter** по расписанию (cron) с отчётом в TG.
- Периодическая проверка `/etc/ld.so.preload`, root-crontab, `/etc/passwd`, authorized_keys на изменения.

---

## 🟡 Фаза 6 — Процесс и устойчивость
- **Бэкапы**: регулярные `pg_dump` + MinIO в офсайт; **проверять восстановление**.
- **Патчи**: `unattended-upgrades` + ручной апдейт Docker/ядра по графику.
- **Least privilege**: минимум открытых портов, минимум прав контейнеров/юзеров.
- **Post-incident lessons learned** (скил `conducting-post-incident-lessons-learned`): задокументировать, обновить рантбук.
- **Связь с деплой-дрейфом**: значения секретов и конфиг — управляемо (EasyPanel env / Docker secrets), чтобы rebuild был воспроизводим.

---

## Порядок выполнения (рекомендуемый)
1. **Фаза 1 (rebuild)** + **Фаза 4 (ротация секретов)** — вместе, это критично.
2. **Фаза 2 + 3** (хардненинг ОС + Docker) — на новом сервере до публикации.
3. **Фаза 5** (мониторинг) — сразу после запуска.
4. **Фаза 6** (процесс) — постоянно.

До rebuild сервер остаётся в локдауне (Фаза 0): сайт офлайн для публики, доступ только с `202.79.184.53`.
