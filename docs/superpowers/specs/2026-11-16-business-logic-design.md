# Бизнес-логика Gym Booking API — дизайн

Дата: 2026-11-16 · Ветка: `backend-rewrite`

## Зачем

Сейчас контроллеры в `kz.gymbooking.controller` отдают захардкоженный `text/plain`. Нужно заменить заглушки на
продовую реализацию по контракту `src/main/resources/static/gym-booking-api.yaml`: JSON, JWT-аутентификация, роли,
все бизнес-правила. SQL-схема ещё не готова, поэтому слой данных — только интерфейсы. Реализации репозиториев
появятся отдельным запросом, когда будет схема; сервисы и тесты при этом меняться не должны.

## Цели

- Все эндпоинты из контракта (34 + `GET /`) работают по контракту: тела ответов, коды, коды ошибок `Problem.code`.
- Бизнес-логика живёт в отдельном слое `service`; контроллеры только принимают запрос, берут текущего пользователя
  и вызывают сервис.
- Репозитории — интерфейсы без реализации.
- Интеграционные тесты на общий функционал: `@SpringBootTest` + MockMvc, репозитории замоканы.

## Не цели

- Реализации репозиториев, SQL, миграции, начальные данные (админ, тарифы) — придут вместе со схемой.
- Сплошное покрытие юнит-тестами.
- Фронтенд — не трогаем.
- Реальные провайдеры устройств (Garmin, Fitbit) и реальная оплата.

## Анти-цели

- Отступления от контракта ради простоты (например, HTTP Basic вместо Bearer или текст вместо JSON).
- Логика в контроллерах или в репозиториях: при подключении БД её не должно понадобиться переносить.

## Ограничения

- Spring Boot 4.0.1, Java 21, Maven (локально — `~/.m2/wrapper/dists/apache-maven-3.9.16`).
- Base path `/api/v1` (уже в `application.yml`), время — UTC.
- Пока нет реализаций репозиториев, приложение не стартует через `spring-boot:run` (нет бинов). Это ожидаемо.

## Подходы

**Аутентификация.**
1. *Spring Security + встроенный `oauth2-resource-server` (JWT HS256) — выбран.* Стандартные бины
   `NimbusJwtEncoder`/`NimbusJwtDecoder`, никаких сторонних библиотек, полностью совпадает с контрактом.
2. HTTP Basic (чистый дефолт Spring) — меньше кода, но ломает контракт: нет `TokenPair`, refresh/logout пустые,
   фронт хранит пароль. Отклонён.
3. Свой фильтр + jjwt — больше кода без выигрыша. Отклонён.

**Слой данных.**
1. *Интерфейсы репозиториев без реализации, в тестах — `@MockitoBean` — выбран* (решение заказчика: ждём SQL-схему).
2. In-memory реализации на `ConcurrentHashMap` — отклонено заказчиком: лишний код, который выбросим.
3. Spring Data JPA + H2 — отклонено: работа с БД отложена до схемы.

## Структура пакетов

```
kz.gymbooking
├── controller   — существующие контроллеры, переписаны на JSON и вызовы сервисов
├── dto          — request-records (существующие) + response-records (новые) + PageResponse<T>{items,total}
├── domain       — модели: User, Trainer, GymClass, Booking, MembershipPlan, Membership, Visit, Device,
│                  Metric, RefreshToken, UsedQrNonce + enum'ы (Role, ClassStatus, BookingStatus,
│                  MembershipStatus, DeviceStatus, MetricType, CheckInDecision, DenyReason)
├── repository   — интерфейсы
├── service      — бизнес-логика
├── security     — SecurityConfig, TokenService (выпуск access/refresh/QR), CurrentUser, обработчики 401/403
├── job          — плановые задачи (FINISHED / NO_SHOW / EXPIRED)
└── error        — ApiException, ErrorHandler → application/problem+json
```

- Модели — обычные изменяемые классы с геттерами/сеттерами и `Long id` (проще потом превратить в `@Entity`).
- Enum'ы `Role`, `BookingStatus`, `MetricType` переезжают из `dto` в `domain`.
- Маппинг domain → response — в сервисах или маленьких статических `from(...)` в response-records (на усмотрение).
- `bookedCount` занятия не хранится, а считается: `bookingRepository.countActive(classId)`, активные — BOOKED и ATTENDED.

## Репозитории (интерфейсы)

Сигнатуры в стиле Spring Data, чтобы потом легко реализовать: `Optional<T> findById(Long)`, `T save(T)`
(возвращает сохранённый объект с id), плюс запросы, которые нужны сервисам. Списки с фильтрами и пагинацией
принимают filter-record + `limit`/`offset` и возвращают `Page<T>(List<T> items, long total)`. Точный набор методов —
на усмотрение исполнителя, по потребностям сервисов. Обязательные «хуки» под будущую БД:

- `GymClassRepository.findByIdForUpdate(id)` — для записи на занятие (в БД — `SELECT … FOR UPDATE`);
- `findOverlapping(trainerId | room, start, end, excludeClassId)` — для проверки конфликтов расписания;
- `BookingRepository.findByClientIdAndIdempotencyKey(...)`.

`@Transactional` пока не ставим (нет `PlatformTransactionManager`); места, где транзакция обязательна (запись на
занятие, отмена занятия, check-in), помечаем комментарием `// TODO(db): transaction`.

## Безопасность

- Зависимости: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`,
  тесты — `spring-boot-starter-security-test` (имена стартеров проверить для Boot 4).
- Пароли — `BCryptPasswordEncoder`.
- Access-токен: JWT HS256, секрет `app.jwt.secret` в `application.yml` (dev-значение; на проде — через env),
  `sub` = userId, claim `role`, `typ=access`, срок 15 мин (`app.jwt.access-ttl`). Декодер принимает только `typ=access`,
  чтобы QR-токен нельзя было использовать как Bearer.
- Claim `role` → authority `ROLE_<role>`; проверки ролей — `@PreAuthorize`, проверки владельца — в сервисах.
- Refresh-токен: случайная строка (32 байта, base64url), в репозитории хранится её SHA-256 хеш, срок 30 дней.
  `/auth/refresh` — ротация (старый отзывается, выдаётся новая пара). `/auth/logout` (тела нет) — отзывает все
  refresh-токены текущего пользователя.
- Публичные (`security: []` в контракте): `POST /auth/register|login|refresh`, `GET /trainers`, `GET /trainers/{id}`,
  `GET /classes`, `GET /classes/{id}`, `GET /membership-plans`, `GET /device-providers`, `GET /`.
- 401 и 403 от Spring Security тоже отдаются как `Problem` (свои `AuthenticationEntryPoint` и `AccessDeniedHandler`).
- CSRF выключен, сессии `STATELESS`.

## Ошибки

`ErrorHandler` переписывается на `application/problem+json`:
`{status, code, title, detail?, errors?: [{field, message}]}`. Коды — только из enum `Problem.code` контракта.
Ошибки валидации тела/параметров → 422 `VALIDATION_FAILED` с `errors`.

## Время и случайность

Все проверки времени — через бин `Clock` (UTC). Генерация метрик stub-устройства — через инжектируемый
`RandomGenerator`. В тестах оба подменяются.

## Бизнес-правила по разделам

**Auth.** register: email уникален без учёта регистра → иначе 409 `EMAIL_TAKEN`; роль CLIENT; ответ 201 `TokenPair`
с `user`. login: неверный email или пароль → 401 `UNAUTHORIZED` (одинаково для обоих случаев). refresh: токен не
найден / отозван / истёк → 401.

**Users.** `/users/me` GET/PATCH — любой залогиненный. `GET /users` и `PATCH /users/{id}/role` — ADMIN; поиск `q`
по имени/email/телефону без учёта регистра; несуществующий пользователь → 404.

**Trainers.** Создание — ADMIN: пользователь должен существовать (404), профиля тренера ещё нет (409 `ALREADY_EXISTS`),
роль пользователя становится TRAINER. Изменение — ADMIN или сам тренер (`trainer.userId == current`), иначе 403.

**Classes.**
- Список: фильтры `from`, `to`, `trainerId`, `type`; `onlyAvailable` скрывает не-SCHEDULED и заполненные; сортировка
  по `startTime`.
- Создание: ADMIN или TRAINER только со своим `trainerId` (иначе 403). Тренер должен существовать, `startTime` — в
  будущем (иначе 422). Пересечение по времени с другим SCHEDULED-занятием того же тренера или в том же зале →
  409 `SCHEDULE_CONFLICT`.
- Изменение: ADMIN или тренер этого занятия. Только для SCHEDULED (иначе 409 `INVALID_STATE`); `capacity` ниже
  числа активных записей → 409 `INVALID_STATE`; новое время/зал/тренер проверяются на `SCHEDULE_CONFLICT`.
- Отмена: ADMIN или тренер занятия; только SCHEDULED (иначе 409 `INVALID_STATE`); все BOOKED-записи → CANCELLED с
  `cancelledAt`.
- Список записей на занятие: тренер этого занятия, STAFF, ADMIN.

**Bookings.**
- Создание — только CLIENT. Нет занятия → 404. Затем строго по порядку контракта:
  1. SCHEDULED и не началось → иначе 409 `CLASS_NOT_BOOKABLE`;
  2. есть ACTIVE-абонемент, действующий на дату занятия (по датам), и у лимитного остались визиты → иначе
     403 `NO_ACTIVE_MEMBERSHIP`;
  3. нет активной записи на это занятие → иначе 409 `ALREADY_BOOKED`;
  4. активных записей меньше `capacity` → иначе 409 `CLASS_FULL`.
- `Idempotency-Key`: если у клиента уже есть запись с этим ключом — вернуть её (201), правила не проверять.
- Мои записи: фильтры `status`, `upcoming` (занятие в будущем), пагинация.
- Просмотр записи: владелец, STAFF, ADMIN, тренер занятия; иначе 403.
- Отмена: владелец или ADMIN (иначе 403); только BOOKED (иначе 409 `INVALID_STATE`); владелец — не позже чем за
  2 часа до начала (иначе 409 `CANCEL_DEADLINE_PASSED`), ADMIN — всегда.

**Memberships.** Покупка — любой залогиненный (по смыслу клиент). Тариф не найден → 404. `startDate` по умолчанию
сегодня, в прошлом → 422. `endDate = startDate + durationDays − 1`. Пересечение периода с ACTIVE- или
FROZEN-абонементом → 409 `MEMBERSHIP_OVERLAP`. `visitsLeft = plan.visitLimit`, статус сразу ACTIVE. Мои абонементы —
все, по убыванию `startDate`.

**Access.**
- QR-токен — CLIENT: нужен абонемент, действующий сегодня, с визитами (иначе 403 `NO_ACTIVE_MEMBERSHIP`). Токен —
  JWT того же ключа с `typ=qr`, `sub`=userId, `jti`=nonce, срок 60 с.
- Check-in — STAFF. Всегда 200 с решением. Порядок: подпись/формат/тип неверны → `TOKEN_INVALID`; истёк →
  `TOKEN_EXPIRED`; nonce уже использован → `TOKEN_ALREADY_USED`; нет абонемента, действующего сегодня →
  `NO_ACTIVE_MEMBERSHIP`; `visitsLeft == 0` → `NO_VISITS_LEFT`. При GRANTED: nonce сохраняется (до истечения),
  у лимитного абонемента `visitsLeft − 1`, создаётся `Visit` с `gate`, BOOKED-запись клиента на занятие, начинающееся
  в пределах ±30 мин, становится ATTENDED с `checkedInAt`. Если подходит несколько абонементов — берётся тот, что
  заканчивается раньше.
- Журнал визитов: STAFF и ADMIN — все, с необязательным `userId`; остальные — только свои (`userId=me` или свой id),
  иначе 403.

**Devices.** Провайдеры — константный список: `stub` (включён), `garmin`, `fitbit` (выключены). Подключение:
неизвестный или выключенный провайдер → 422 `VALIDATION_FAILED` (поле `provider`); `stub` → сразу CONNECTED,
`name` по умолчанию — имя провайдера. Отключение — статус DISCONNECTED, метрики остаются; чужое/несуществующее → 404.
Список моих устройств — без DISCONNECTED. Синхронизация — только своего CONNECTED-устройства (иначе 404): генерирует
правдоподобные метрики за период с `lastSyncAt` (или последние 24 ч) — пульс, шаги, калории по часам — и обновляет
`lastSyncAt`. Метрики: фильтры `type`, `from`, `to`; `bookingId` — только своя запись (иначе 404), берутся метрики
в окне `[start, start + durationMin]`.

**Плановые задачи** (`@Scheduled`, отключаемы свойством, в тестах выключены):
- каждые 5 мин: SCHEDULED-занятия, которые уже закончились → FINISHED, их BOOKED-записи → NO_SHOW;
- раз в сутки: ACTIVE-абонементы с `endDate` < сегодня → EXPIRED.

## Решения исполнителя по итогам ревью (проверить при утверждении)

1. **Израсходованный лимитный абонемент.** Когда check-in списывает последний визит (`visitsLeft` → 0), абонемент
   сразу становится EXPIRED. Поэтому он не блокирует покупку нового (`MEMBERSHIP_OVERLAP`) и не мешает входу.
2. **Каждый check-in списывает визит**, даже повторный в тот же день (контракт не говорит о «визите в день»).
3. **Роль берётся из JWT.** До 15 минут после смены роли действуют старые права — стандартный компромисс JWT.
   Чтобы не продлевать это окно, `PATCH /users/{id}/role` отзывает все refresh-токены пользователя. Проверки
   «тренер этого занятия» дополнительно требуют роль TRAINER, так что разжалованный тренер свои занятия больше
   не правит; профиль тренера при этом не удаляется.
4. **Изменение занятия.** `startTime` можно перенести только в будущее (иначе 422). Сменить тренера может только
   ADMIN; TRAINER, указавший чужой `trainerId`, получает 403. Существующие записи при переносе не трогаются
   и не перепроверяются.
5. **Часовой пояс для дат.** Временные метки — UTC, как в контракте. «Сегодня» для дат абонементов (покупка,
   «действует сегодня», задача EXPIRED) считается в зоне `app.time-zone` (по умолчанию `Asia/Almaty`).

## Тесты

- Удалить `StubApiTest`. Новые тесты — `@SpringBootTest` + MockMvc + spring-security-test, все репозитории —
  `@MockitoBean`, `Clock` — фиксированный.
- Аутентификация в тестах: один сквозной сценарий через настоящий `/auth/login` → Bearer; в остальных — `jwt()` из
  spring-security-test с нужными `sub`/`role`.
- Тестовые классы по разделам контракта, сценарии «общего функционала»: регистрация/логин/refresh/401;
  запись на занятие — успех и каждая из ошибок правил 1–4, идемпотентность; отмена записи и дедлайн; создание
  занятия с конфликтом расписания и правами тренера; отмена занятия каскадом; QR → check-in GRANTED/DENIED и
  ATTENDED; покупка абонемента и пересечение; устройства — подключение/синк/метрики; 403 по ролям; формат `Problem`
  и 422 валидации.
- Прогон: `mvn test` должен быть зелёным.

## Оставлено на исполнителя

Точные сигнатуры репозиториев, имена классов сервисов, разбиение тестов по файлам, формат маппинга, детали
генерации метрик, обновление README (раздел API/тесты).
