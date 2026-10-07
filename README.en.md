[中文](README.md) | [English](README.en.md)

<img src="frontend/public/brand/logo.jpg" height="48" alt="ZhiHua Technology logo">

# PortCall · Vessel Calls and Port Service Coordination

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/)

**0.1.0 · Public source for learning / non-commercial use. Commercial use requires prior written authorization.** Own code uses [LICENSE](LICENSE); third-party components/assets retain their licenses, listed in [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md).

## One call, several schedules that need reconfirmation

PortCall uses Java 21 / Spring Boot, Vue 3, MySQL and Flyway for vessel call plans, agency data scopes, provider confirmation and independent acceptance. Ship agents, internal coordinators and providers often maintain separate schedules. After an estimated arrival changes, they must check whether service windows remain valid, who reconfirmed and whether work actually finished. This single-organization application records those facts and lets each party handle authorized work.

ETA/ETD and ATA/ATD terminology references the [IMO Just in Time Portal](https://greenvoyage2050.imo.org/pdf/just-in-time-portal/). People enter estimated times/windows; authorized people report actual arrival/departure and work times from verified facts. The application does not connect to AIS, recommend navigation, reserve berths, implement a maritime single window, issue maritime/customs authorization or make statutory filings.

```text
Call draft → submit → independent approval → planned
Service request → bound provider confirms or declines
Time proposal → independent approval → new revision → unstarted services need replanning
             → new windows → provider reconfirmation
Actual arrival → provider starts → completion report → independent acceptance/dispute
               → corrected report and acceptance
Actual departure → resolve unfinished work → independent close
```

Actual departure can be recorded before tasks are closed. Closing requires every service to be accepted, declined or canceled; every uncanceled critical service accepted; at least one critical service accepted; and no unfinished plan change. A declined critical service needs a replacement before departure. Canceling all critical services cannot bypass the condition.

Approved changes put unstarted services into CHANGE_PENDING, retaining old windows/confirmation events. Coordinators replan and providers reconfirm. Estimated time changes cannot be approved after arrival. Actual arrival, departure and start milestones cannot be edited. A disputed completion report can correct the ending time while retaining old events. There is no milestone-correction workflow in this release.

## Business and administrative interfaces

| Module | Implemented operations |
|---|---|
| Calls | Draft/edit, submit, independent approve/reject, prearrival cancellation, actual arrival/departure, independent close, versions and evidence timeline |
| Plan changes | Current-revision proposal, draft edits, submission, independent review and cancellation; one unfinished proposal per call |
| Services | Draft/send, bound provider confirm/decline, window replanning, actual start/end reports, independent acceptance/dispute and cancellation before starting |
| Vessels and agencies | Internal references, departments, AGENT/PROVIDER types, enabled state and versions; unreferenced directories can be deleted, foreign keys protect historical references |
| Search and evidence | Search, state/type filters, bounded pagination/sorting, associated details, appended events and scope-filtered JSON download |
| Workspace | Visible call/service states, critical work, replanning, pending acceptance and disputes |
| Administration | Accounts, five initial roles, 18 registered permissions, 14 registered menus, departments, service dictionaries, settings and scoped audit |
| Identity and pages | Session login/logout, own password changes, live authorization, Chinese/English, narrow layouts and official logo |

Coordinators maintain directories, requests and actual arrival/departure. Independent reviewers approve calls/changes, accept services and close calls. Agents submit their own agency's calls; providers confirm and perform only their agency's services. Approval/closing requires a different account from the relevant creator; acceptance differs from both the service creator and reporter. Internal administrators cannot act as providers.

Internal roles use ALL, DEPARTMENT or SELF (created calls and associated work). **Agency bindings override ALL.** Agents see their calls; providers see their services and associated calls, excluding other providers' services, plan changes and internal administration. Accounts/agencies must share a department. Hiding navigation is not a substitute for API checks.

### Actual running pages

Isolated acceptance uses explicitly marked TEST records and random accounts, without real customer data. These records are not installed as business examples in an empty database.

| Login | Provider workspace |
|---|---|
| ![Login](docs/screenshots/login.jpg) | ![Provider workspace](docs/screenshots/provider-home.jpg) |

Login: session authentication. Provider workspace: only the bound agency's services and associated calls.

| Call plan and actual milestones | Service evidence |
|---|---|
| ![Call details](docs/screenshots/call.jpg) | ![Service details](docs/screenshots/service.jpg) |

Call details: estimated/actual times, plan revisions and appended events. Service details: window confirmation, work reports and independent acceptance.

| Accounts | Coordination statistics |
|---|---|
| ![Accounts](docs/screenshots/users.jpg) | ![Statistics](docs/screenshots/dashboard.jpg) |

Accounts: users, departments, roles and agency bindings. Statistics: authorized call/service states.

| Roles and permissions | System settings |
|---|---|
| ![Roles](docs/screenshots/roles.jpg) | ![Settings](docs/screenshots/settings.jpg) |

Roles: registered interface permissions and scopes. Settings: supported workspace name/capacity configuration.

## Architecture and storage

Browser → same-origin Nginx → Spring Boot → JPA/MySQL. Flyway manages schema versions; Hibernate validates only. A single-organization serial write lock, record versions, UUIDs and request-content fingerprints protect state. All changes append events; business records cannot be hard-deleted. See [Architecture](docs/架构说明.md). Detailed linked manuals are currently in Chinese.

| Layer | Environment/version |
|---|---|
| Backend | Java 21, Maven 3.9, Spring Boot 4.0.7, Security, JPA and Flyway; MariaDB JDBC accesses MySQL |
| Frontend | Vue 3.5.40, Vite 8.1.5, Node 24.19.0+, npm 11 and Lucide |
| Deployment | MySQL 8.4, Nginx 1.29, Docker Engine/Desktop and Compose v2 |
| Acceptance | Python 3.10+; H2 MySQL-mode backend tests plus actual MySQL deployment checks |

```text
backend/                  Domain, authorization, API, initialization, migrations and tests
frontend/                 Workspaces, business details, administration, forms and tests
  public/brand/           Official logo and original Chinese contact assets
scripts/                  Private configuration generation, actual HTTP acceptance, release checks
docs/                     Architecture, deployment, API, operations, screenshots and licenses
compose.yaml              Independent database, backend and frontend
.env.example              Names only; actual configuration ignored by Git/images
```

V1 creates identity/system directories. V2 creates agencies, vessels, calls, changes, services, command records and evidence, with agency bindings on accounts. Foreign keys and constraints protect relationships, time ordering and versions. Times use UTC microseconds; pages explicitly use Asia/Shanghai and convert inputs to UTC independently of computer timezone.

## Requirements and empty-database installation

Docker Engine/Desktop, Compose v2 and Python 3.10+ are required. Builds access public dependencies and official registries. Run at the repository root:

```sh
python3 scripts/init-env.py
docker compose -p portcall config --quiet
docker compose -p portcall up -d --build --wait
```

The generator creates `.env` with mode 0600 and refuses to overwrite it. If configuration already exists, start directly. Username is **admin**; read **ADMIN_PASSWORD** in your own generated, ignored `.env`. There is no public fixed demo password. An empty database initializes headquarters, five roles, 18 permissions, 14 menus, four service types and three settings. BCrypt cost 12 stores password hashes. Business tables stay empty; only explicitly authorized isolated acceptance writes TEST records.

- Interface/same-origin API: [http://127.0.0.1:8128/](http://127.0.0.1:8128/).
- Health: [http://127.0.0.1:8128/actuator/health](http://127.0.0.1:8128/actuator/health).
- Database/backend have no published host ports.
- Override: `WEB_PORT=18128 docker compose -p portcall up -d`, or edit your own local `.env`.

### Configuration

| Name | Purpose |
|---|---|
| `DATABASE_PASSWORD` / `MYSQL_ROOT_PASSWORD` | Independent application/administration database passwords, no weak defaults |
| `ADMIN_PASSWORD` | Empty-database initialization only; at least 12 characters with upper/lowercase letters and digits, at most 72 UTF-8 bytes; existing accounts use password change/reset |
| `WEB_PORT` / `BIND_ADDRESS` | Defaults 8128 / 127.0.0.1; external deployment requires trusted proxies/HTTPS |
| `COOKIE_SECURE` | false for local HTTP, true for HTTPS |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_CATALOG` | Backend process JDBC/account/catalog overrides; external databases in Compose need explicit environment mapping |
| `TEST_URL` | Acceptance target, default port 8128; isolated tests only |

[.env.example](.env.example) lists names. Do not commit secrets, actual configuration or production deployment overrides. Updating `ADMIN_PASSWORD` does not reset an existing account.

### Source development

Use Node 24.19.0+ / npm 11 in a frontend terminal:

```sh
cd frontend
npm ci
npm run dev
```

Vite port 5173 proxies backend port 8080. In a separate repository-root terminal, use Java 21 / Maven 3.9 with controlled `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `DATABASE_CATALOG` and `ADMIN_PASSWORD` environment values:

```sh
mvn -f backend/pom.xml spring-boot:run
```

An example local JDBC endpoint is `jdbc:mariadb://127.0.0.1:13308/zhuatech_portcall`. Use a separate Compose volume and private loopback-only database port override; see [Deployment](docs/部署说明.md). Do not type real passwords into command history. Host backend source execution does not automatically load `.env`.

## Testing

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose -p portcall config --quiet
git diff --check
python3 scripts/release-check.py
```

Backend tests cover time bounds, HTTP/JPA states, agency isolation, independent actors, concurrent changes and UUID retries. Integration tests use isolated H2 and a dynamically generated password. Frontend tests cover actions, fields, timezones and CSRF. Docker Maven builds run all tests. A fresh actual MySQL environment separately checks full workflows, permissions, migrations, health, restarts and independent recovery:

```sh
python3 scripts/smoke.py --allow-test-writes
python3 scripts/smoke.py --capture
python3 scripts/smoke.py --verify
```

Use explicit write mode only against this project's disposable isolated database. It creates TEST records/random accounts. Capture current state after page acceptance into ignored private `output/qa-state.json`; never upload it. Verify compares responses and reauthenticates every role after restart or recovery. `TEST_URL` selects the isolated target.

## Deployment, upgrades and backup recovery

See [Deployment, upgrades and recovery](docs/部署说明.md). Before upgrades, pause writes, back up the complete database, verify recovery and record the application/Flyway versions. Start backend migration/health before frontend traffic. Add migrations; never rewrite executed migrations, delete their history or delete actual business volumes to fix errors.

Keep backups in restricted private directories outside public source, with mode 0600. They contain account hashes and business evidence. Use authorized container environment values with consistent logical backups; do not print credentials or backup contents. Restore into a new project, new database volume and different web port using matching accepted images (or rebuild them). Start MySQL, import the full backup, then start applications and compare the same private acceptance state with `--verify`. Actual deployment recovery must also check its own data/accounts before switching.

External deployment requires a trusted HTTPS proxy, `COOKIE_SECURE=true`, least privilege, network isolation, restricted secrets and independent backups. MySQL volumes/strong passwords do not provide automatic backups or HA. See [SECURITY](SECURITY.md). `docker compose -p portcall down` retains the database volume; remove volumes only for explicitly disposable test resources after checking their project labels. Leave other environments running.

## Known limits and troubleshooting

Each primary business type has at most 1,000 records; `maxRecords` is adjustable from 100 to 1,000. Directory reads are bounded at 10,000 and pages at 100. Single-organization serial writes and application login limits support learning deployments. Large concurrency, multiple instances, HA and external authentication are unverified. Berth collision detection, official vessel registration, milestone corrections, attachments, notifications, billing, inventory, multi-tenancy and external integrations are not implemented. There is no AI/external account requirement or simulated-success demo mode.

Credentials are not persisted in browser storage. Writes require CSRF; permissions/disablement are checked every request and password changes invalidate existing sessions. Exports inherit detail scopes without promotional data. Keep actual `.env`, snapshots, backups and logs out of source control.

| Symptom | Check |
|---|---|
| Unhealthy service | This project's MySQL/backend logs, configuration, catalog and migrations; preserve the original volume |
| Occupied port | Override `WEB_PORT` rather than stopping other projects |
| Service cannot start | Actual arrival, current plan revision, provider binding and confirmation |
| CHANGE_PENDING service | Internal coordinator supplies a valid new window; provider reconfirms |
| Cannot close after departure | Reports/acceptances, unfinished changes and critical-service requirements |
| Rejected timestamp | Allowed range, UTC conversion, future actual facts and start/end ordering |
| Unavailable action | Role, state and independent actor; bindings override ALL |
| Existing password unchanged by `ADMIN_PASSWORD` | Initialization-only; use own change/authorized reset |
| Migration validation fails | Check trusted scripts/history and upgrade with new migrations; never delete history |

## License and feedback

Own code uses [ZhuaTech Non-Commercial Source License 1.0](LICENSE), permitting personal learning, technical research and non-commercial exchange only. **Commercial use requires prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd.** Enterprise private deployment, paid delivery, SaaS operation, source resale and paid services require separate authorization. Preserve attribution, website, copyright, license and licensing contacts. This is publicly readable non-commercial source, not an OSI-approved license. Third-party licenses apply separately. Software is provided as is; no unverified production-readiness claim is made.

See [User manual](docs/操作手册.md), [API](docs/接口说明.md) and [CONTRIBUTING](CONTRIBUTING.md). Share redacted reproducible issues only; report security concerns privately without posting credentials/customer records. This record system cannot prove actual vessel position, port clearance, navigation safety, service delivery or legal responsibility. The operating organization must verify facts, evidence and authority.

## Contact ZhiHua Technology

For commercial licensing, in-depth custom development, private deployment or system integration, contact **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
