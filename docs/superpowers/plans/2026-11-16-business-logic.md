# Gym Booking Business Logic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the text/plain stub controllers with a contract-compliant JSON API backed by a service layer, JWT auth and repository interfaces (no implementations), covered by MockMvc integration tests with mocked repositories.

**Architecture:** controller → service → repository interface. Controllers only bind requests, take the caller from `@AuthenticationPrincipal Jwt` and call services. Services own every business rule and map domain → response records through one `ResponseMapper`. Repositories are interfaces only; tests replace them with `@MockitoBean`.

**Tech Stack:** Spring Boot 4.0.1 (webmvc, validation, security, security-oauth2-resource-server), Java 21, Jackson 3, JUnit 5, Mockito, spring-security-test.

**Spec:** `docs/superpowers/specs/2026-11-16-business-logic-design.md`

## Global Constraints

- Maven: `MVN=~/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn`, run with `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- Base path `/api/v1` (already `server.servlet.context-path`); MockMvc paths omit it.
- Responses `application/json`; errors `application/problem+json` `{status, code, title, detail?, errors?:[{field,message}]}`; `code` only from: VALIDATION_FAILED, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, EMAIL_TAKEN, NO_ACTIVE_MEMBERSHIP, CLASS_NOT_BOOKABLE, CLASS_FULL, ALREADY_BOOKED, CANCEL_DEADLINE_PASSED, SCHEDULE_CONFLICT, MEMBERSHIP_OVERLAP, ALREADY_EXISTS, INVALID_STATE.
- Lists with paging return `{items, total}`; `limit` 1..100 default 20, `offset` ≥0 default 0.
- Timestamps `Instant` (UTC, ISO strings). Membership dates `LocalDate` in zone `app.time-zone` (default `Asia/Almaty`).
- JWT HS256, `app.jwt.secret` (≥32 bytes), access TTL 15 min (`expiresIn: 900`), refresh TTL 30 days, QR TTL 60 s. Access tokens carry `typ=access`, QR tokens `typ=qr`.
- All "now" comes from the `Clock` bean; all randomness from the `RandomGenerator` bean.
- No `@Transactional` (no transaction manager yet); mark booking create, class cancel and check-in with `// TODO(db): transaction`.
- Repository `save(x)` returns the saved object with `id` set.
- Commit after each task on the current branch `backend-rewrite`; do not push.

## Review Focus

1. Email case — `Aruzhan@Example.com` registers/logs in as the same account as `aruzhan@example.com` → test in Task 2.
2. Bad input values (unknown enum `"role":"SUPERUSER"`, malformed JSON, `limit=101`) → 422 Problem, never 500 → tests in Task 2 and Task 3.
3. "Today" across UTC midnight — at 2026-11-16T20:00Z it is already 2026-11-17 in Almaty → test in Task 4.
4. Time boundaries — booking a class whose `startTime == now` is CLASS_NOT_BOOKABLE; cancelling exactly 2 h before start is allowed → tests in Task 5.
5. QR token misuse — a QR token as `Bearer` is 401, an access token scanned as QR is TOKEN_INVALID → tests in Task 6.

---

### Task 1: Foundation — dependencies, domain, repositories, errors, security skeleton, test support

**Files:**
- Modify: `pom.xml` — add `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`; test: `spring-boot-starter-security-test`.
- Modify: `src/main/resources/application.yml` — add `app.jwt.secret` (dev value), `app.jwt.access-ttl: 15m`, `app.jwt.refresh-ttl: 30d`, `app.jwt.qr-ttl: 60s`, `app.time-zone: Asia/Almaty`, `app.jobs.enabled: true`.
- Create `src/main/java/kz/gymbooking/domain/`: enums `Role` (moved from dto), `BookingStatus` (moved), `MetricType` (moved), `ClassStatus{SCHEDULED,CANCELLED,FINISHED}`, `MembershipStatus{ACTIVE,EXPIRED,FROZEN}`, `DeviceStatus{CONNECTED,PENDING,ERROR,DISCONNECTED}`, `CheckInDecision{GRANTED,DENIED}`, `DenyReason{TOKEN_INVALID,TOKEN_EXPIRED,TOKEN_ALREADY_USED,NO_ACTIVE_MEMBERSHIP,NO_VISITS_LEFT}`; classes (no-arg ctor, getters/setters):
  - `User{Long id; String email; String passwordHash; String fullName; String phone; Role role; Instant createdAt}`
  - `Trainer{Long id; Long userId; List<String> specializations; String bio; String photoUrl}`
  - `GymClass{Long id; String name; String type; Long trainerId; Instant startTime; int durationMin; String room; int capacity; ClassStatus status; String cancelReason}` + `Instant endTime()`
  - `Booking{Long id; Long clientId; Long classId; BookingStatus status; Instant bookedAt; Instant cancelledAt; Instant checkedInAt; UUID idempotencyKey}`
  - `MembershipPlan{Long id; String name; int durationDays; Integer visitLimit; int price}`
  - `Membership{Long id; Long clientId; Long planId; LocalDate startDate; LocalDate endDate; Integer visitsLeft; MembershipStatus status}` + `boolean coversDate(LocalDate d)` (ACTIVE and start ≤ d ≤ end) + `boolean hasVisitsLeft()` (visitsLeft == null || > 0)
  - `Visit{Long id; Long userId; String gate; Instant checkedInAt; Long bookingId}`
  - `Device{Long id; Long userId; String provider; String name; DeviceStatus status; Instant connectedAt; Instant lastSyncAt}`
  - `Metric{Long id; Long userId; Long deviceId; MetricType type; double value; String unit; Instant recordedAt}`
  - `RefreshToken{Long id; Long userId; String tokenHash; Instant expiresAt; boolean revoked}`
  - `UsedQrNonce{String nonce; Instant expiresAt}`
- Create `src/main/java/kz/gymbooking/repository/`: `Page<T>(List<T> items, long total)`, `ClassFilter(Instant from, Instant to, Long trainerId, String type, boolean onlyAvailable)` and interfaces (exact signatures — tests mock these):
  - `UserRepository`: `Optional<User> findById(Long)`, `Optional<User> findByEmailIgnoreCase(String)`, `boolean existsByEmailIgnoreCase(String)`, `User save(User)`, `Page<User> search(Role role, String q, int limit, int offset)`
  - `TrainerRepository`: `Optional<Trainer> findById(Long)`, `Optional<Trainer> findByUserId(Long)`, `Trainer save(Trainer)`, `Page<Trainer> search(String specialization, int limit, int offset)`
  - `GymClassRepository`: `Optional<GymClass> findById(Long)`, `Optional<GymClass> findByIdForUpdate(Long)`, `GymClass save(GymClass)`, `Page<GymClass> search(ClassFilter, int limit, int offset)`, `List<GymClass> findOverlapping(Long trainerId, String room, Instant start, Instant end, Long excludeClassId)` (SCHEDULED classes sharing trainer OR room whose interval intersects), `List<GymClass> findScheduledEndedBefore(Instant)`
  - `BookingRepository`: `Optional<Booking> findById(Long)`, `Booking save(Booking)`, `long countActiveByClassId(Long)` (BOOKED+ATTENDED), `Optional<Booking> findActiveByClientIdAndClassId(Long clientId, Long classId)`, `Optional<Booking> findByClientIdAndIdempotencyKey(Long, UUID)`, `List<Booking> findByClassId(Long)`, `List<Booking> findByClassIdAndStatus(Long, BookingStatus)`, `Page<Booking> searchByClient(Long clientId, BookingStatus status, Boolean upcoming, Instant now, int limit, int offset)`, `List<Booking> findBookedByClientWithClassStartBetween(Long clientId, Instant from, Instant to)`
  - `MembershipPlanRepository`: `List<MembershipPlan> findAll()`, `Optional<MembershipPlan> findById(Long)`
  - `MembershipRepository`: `Membership save(Membership)`, `List<Membership> findByClientId(Long)` (startDate desc), `List<Membership> findByClientIdAndStatusIn(Long, Collection<MembershipStatus>)`, `List<Membership> findActiveEndedBefore(LocalDate)`
  - `VisitRepository`: `Visit save(Visit)`, `Page<Visit> search(Long userId, Instant from, Instant to, int limit, int offset)`
  - `DeviceRepository`: `Optional<Device> findById(Long)`, `Device save(Device)`, `List<Device> findByUserIdAndStatusNot(Long, DeviceStatus)`
  - `MetricRepository`: `List<Metric> saveAll(List<Metric>)`, `List<Metric> search(Long userId, MetricType type, Instant from, Instant to)`
  - `RefreshTokenRepository`: `RefreshToken save(RefreshToken)`, `Optional<RefreshToken> findByTokenHash(String)`, `void revokeAllByUserId(Long)`
  - `UsedQrNonceRepository`: `boolean existsByNonce(String)`, `UsedQrNonce save(UsedQrNonce)`
- Modify `error/`: `ErrorCode` enum (the 14 codes), `ApiException(HttpStatus, ErrorCode, String title)` + `ApiException(HttpStatus, ErrorCode, String title, List<FieldErrorItem> errors)` + factories `notFound(String what)`, `forbidden()`, `conflict(ErrorCode, String)`, `validation(String field, String message)`; `ProblemResponse(int status, String code, String title, String detail, List<FieldErrorItem> errors)` with `@JsonInclude(NON_NULL)`; `FieldErrorItem(String field, String message)`; rewrite `ErrorHandler` to produce `application/problem+json` for ApiException, BindException/MethodArgumentNotValidException, HandlerMethodValidationException, TypeMismatchException, HttpMessageNotReadableException (422 VALIDATION_FAILED), and `AccessDeniedException` (403 FORBIDDEN).
- Create `config/TimeConfig`: beans `Clock clock()` (`Clock.systemUTC()`), `ZoneId gymZone(@Value("${app.time-zone}"))`, `RandomGenerator randomGenerator()`.
- Create `security/`: `JwtProperties` (`@ConfigurationProperties("app.jwt")`: `String secret; Duration accessTtl; Duration refreshTtl; Duration qrTtl`), `SecurityConfig` (`@EnableMethodSecurity`, stateless, CSRF off, public matchers from the spec, resource server JWT with decoder validating signature + `JwtTimestampValidator` on the `Clock` + `typ == "access"`, authorities from claim `role` with prefix `ROLE_`, `BCryptPasswordEncoder`, `JwtEncoder` on the same `SecretKeySpec`), `AuthUser(Long id, Role role)` with `static AuthUser from(Jwt)` (`sub` → id, `role` claim), `ProblemAuthenticationEntryPoint` (401 UNAUTHORIZED), `ProblemAccessDeniedHandler` (403 FORBIDDEN).
- Modify existing controllers only for imports of moved enums; add `@PreAuthorize("hasRole('ADMIN')")` to `UserController.list`. `HomeController` returns `{"status":"UP"}`.
- Delete: `src/test/java/kz/gymbooking/StubApiTest.java`.
- Create test support `src/test/java/kz/gymbooking/support/`: `MutableClock extends Clock` (`setInstant(Instant)`, `advance(Duration)`, zone UTC); `IntegrationTest` abstract base — `@SpringBootTest(properties = "app.jobs.enabled=false")`, `@Import` of a `@TestConfiguration` with `@Primary MutableClock` at `NOW = 2026-11-16T10:00:00Z` and `@Primary RandomGenerator` = `new SplittableRandom(42)`; `@MockitoBean` for all 11 repositories; `MockMvc mvc` built with `webAppContextSetup(ctx).apply(springSecurity())`; `@BeforeEach` resets clock and stubs every `save`/`saveAll` to return the argument with a sequential id if null; helpers `RequestPostProcessor as(long userId, Role role)` (spring-security-test `jwt()` with `sub`, `role`, `typ=access` and authority `ROLE_<role>`), `String json(Object)`; `Fixtures` with static builders for each domain class (defaults: client #17 Aruzhan Bekova aruzhan@example.com, trainer #3 user #3 Dana Serikova, class #42 Morning Yoga tomorrow 07:00Z 60 min Hall A capacity 15 SCHEDULED trainer #3, plan #1 Monthly Unlimited 30 days null 25000, plan #2 12 Visits 60 days 12 18000).

**Interfaces:**
- Produces: everything above; later tasks use `AuthUser`, `ApiException` factories, `Page`, `IntegrationTest`, `Fixtures`, `MutableClock`.

- [ ] **Step 1: Write `FoundationIT extends IntegrationTest`** with tests:
  - `rootIsPublic`: `GET /` → 200, `$.status == "UP"`.
  - `missingTokenGives401Problem`: `GET /users/me` → 401, content type `application/problem+json`, `$.status == 401`, `$.code == "UNAUTHORIZED"`.
  - `garbageBearerGives401`: header `Authorization: Bearer abc` → 401.
  - `clientOnAdminEndpointGives403Problem`: `GET /users` with `as(17, CLIENT)` → 403, `$.code == "FORBIDDEN"`.
  - `invalidBodyGives422WithFieldErrors`: `POST /auth/register` `{}` → 422, `$.code == "VALIDATION_FAILED"`, `$.errors[*].field` contains `email`, `password`, `fullName`.
- [ ] **Step 2: Run** `$MVN -q test -Dtest=FoundationIT` → FAIL (compile errors / missing classes).
- [ ] **Step 3: Implement the files above.**
- [ ] **Step 4: Run** `$MVN -q test -Dtest=FoundationIT` → PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "feat: domain, repository interfaces, problem errors and JWT security skeleton"`.

---

### Task 2: Auth and Users

**Files:**
- Create: `dto/UserResponse(Long id, String email, String fullName, String phone, Role role, Instant createdAt)`, `dto/TokenPairResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user)`, `dto/PageResponse<T>(List<T> items, long total)`.
- Create: `security/TokenService` — `IssuedTokens issue(User)` (`record IssuedTokens(String accessToken, String refreshToken, long expiresIn)`; refresh = 32 random bytes base64url, stored as SHA-256 hex hash), `String hash(String rawRefresh)`, `String issueQr(Long userId, String nonce, Instant expiresAt)`, `QrClaims parseQr(String token)` (`record QrClaims(Long userId, String nonce, Instant expiresAt)`; throws `QrTokenException(DenyReason)`: bad signature/format/`typ != qr` → TOKEN_INVALID, `exp` < now(clock) → TOKEN_EXPIRED; uses a decoder without the timestamp validator).
- Create: `service/ResponseMapper` (`@Component`) — `UserResponse user(User)` (more methods are added in later tasks).
- Create: `service/AuthService` — `TokenPairResponse register(RegisterRequest)`, `TokenPairResponse login(LoginRequest)`, `TokenPairResponse refresh(RefreshRequest)`, `void logout(AuthUser)`.
- Create: `service/UserService` — `UserResponse getMe(AuthUser)`, `UserResponse updateMe(AuthUser, UserUpdate)`, `PageResponse<UserResponse> list(Role, String q, int limit, int offset)`, `UserResponse changeRole(Long userId, Role role)` (also `refreshTokenRepository.revokeAllByUserId(userId)`).
- Modify: `controller/AuthController`, `controller/UserController` → JSON, call services; ids `Long`.
- Test: `src/test/java/kz/gymbooking/AuthIT.java`, `UsersIT.java`.

**Interfaces:**
- Consumes: Task 1.
- Produces: `TokenService.issue/issueQr/parseQr/hash`, `ResponseMapper.user`, `PageResponse`, `UserResponse`.

- [ ] **Step 1: Write `AuthIT`:**
  - `registerCreatesClientAndReturnsTokens`: `existsByEmailIgnoreCase` false → 201, `$.user.role == "CLIENT"`, `$.expiresIn == 900`, `$.accessToken` and `$.refreshToken` non-empty; captured saved `User.passwordHash` ≠ plain and `passwordEncoder.matches`.
  - `registerTakenEmailGives409`: exists true → 409 `EMAIL_TAKEN`.
  - `emailIsCaseInsensitive`: register with `Aruzhan@Example.com` calls `existsByEmailIgnoreCase("Aruzhan@Example.com")`; login with that casing finds the stored user.
  - `loginThenBearerWorks`: user with BCrypt hash of `secret123` → login 200 → `GET /users/me` with `Authorization: Bearer <accessToken>` → 200, `$.email == "aruzhan@example.com"`.
  - `loginWrongPasswordGives401` and `loginUnknownEmailGives401`: both 401 `UNAUTHORIZED` with the same title.
  - `refreshRotates`: `findByTokenHash` returns valid token for user 17 → 200 new pair; old token saved with `revoked == true`.
  - `refreshRevokedOrExpiredGives401`: revoked → 401; `expiresAt` < NOW → 401; unknown → 401.
  - `logoutRevokesAllRefreshTokens`: `as(17, CLIENT)` → 204, `verify(refreshTokenRepository).revokeAllByUserId(17L)`.
- [ ] **Step 2: Write `UsersIT`:**
  - `updateMeChangesNameAndPhone` → 200 with new values, saved user has them.
  - `adminListsUsers`: `search(CLIENT, "aru", 20, 0)` returns page of 1 → `$.total == 1`, `$.items[0].email`.
  - `changeRoleRevokesTokens`: ADMIN → 200 `$.role == "STAFF"`, `revokeAllByUserId(17L)` called.
  - `changeRoleUnknownUserGives404`: → 404 `NOT_FOUND`.
  - `unknownRoleValueGives422`: body `{"role":"SUPERUSER"}` → 422 `VALIDATION_FAILED`; malformed JSON `{"role":` → 422.
- [ ] **Step 3: Run** `$MVN -q test -Dtest='AuthIT,UsersIT'` → FAIL.
- [ ] **Step 4: Implement** the files above.
- [ ] **Step 5: Run** `$MVN -q test` → all PASS.
- [ ] **Step 6: Commit** `git commit -am "feat: JWT auth, refresh rotation and user management"` (add new files first with `git add -A`).

---

### Task 3: Trainers and Classes

**Files:**
- Create: `dto/TrainerResponse(Long id, Long userId, String fullName, List<String> specializations, String bio, String photoUrl)`, `dto/TrainerRef(Long id, String fullName)`, `dto/GymClassResponse(Long id, String name, String type, TrainerRef trainer, Instant startTime, int durationMin, String room, int capacity, long bookedCount, ClassStatus status)`, `dto/BookingResponse(Long id, Long clientId, @JsonProperty("class") GymClassResponse gymClass, BookingStatus status, Instant bookedAt, Instant cancelledAt, Instant checkedInAt)`.
- Modify: `ResponseMapper` — add `TrainerResponse trainer(Trainer)`, `GymClassResponse gymClass(GymClass)` (trainer name via trainer → user; `bookedCount = bookingRepository.countActiveByClassId`), `BookingResponse booking(Booking)`.
- Create: `service/Permissions` (`@Component`) — `boolean isTrainerOf(AuthUser, GymClass)` = role TRAINER and `trainerRepository.findByUserId(user.id())` has id == `class.trainerId`; `void requireAny(AuthUser, Role...)`.
- Create: `service/TrainerService` — `PageResponse<TrainerResponse> list(String specialization, int limit, int offset)`, `TrainerResponse get(Long id)`, `TrainerResponse create(TrainerCreate)`, `TrainerResponse update(AuthUser, Long id, TrainerUpdate)`.
- Create: `service/ClassService` — `PageResponse<GymClassResponse> list(ClassFilter, int limit, int offset)`, `GymClassResponse get(Long id)`, `GymClassResponse create(AuthUser, GymClassCreate)`, `GymClassResponse update(AuthUser, Long id, GymClassUpdate)`, `GymClassResponse cancel(AuthUser, Long id, CancelClassRequest)`, `List<BookingResponse> listBookings(AuthUser, Long id)`.
- Modify: `controller/TrainerController` (`@PreAuthorize("hasRole('ADMIN')")` on create), `controller/ClassController` (`@PreAuthorize("hasAnyRole('ADMIN','TRAINER')")` on create/update/cancel; `hasAnyRole('ADMIN','STAFF','TRAINER')` on bookings); request DTO ids → `Long`.
- Test: `TrainersIT.java`, `ClassesIT.java`.

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: `ResponseMapper.gymClass/booking/trainer`, `Permissions.isTrainerOf/requireAny`, response records above.

Rules (from spec): create class — TRAINER only with own `trainerId` (403 otherwise), trainer must exist (422 field `trainerId`), `startTime` > now (422 field `startTime`), overlap → 409 SCHEDULE_CONFLICT. Update — ADMIN or trainer of class; only SCHEDULED (409 INVALID_STATE); new `startTime` > now (422); TRAINER changing `trainerId` → 403; capacity < `countActiveByClassId` → 409 INVALID_STATE; overlap (excluding self) → 409 SCHEDULE_CONFLICT. Cancel — ADMIN or trainer of class; only SCHEDULED; all `findByClassIdAndStatus(id, BOOKED)` → CANCELLED with `cancelledAt = now`; save `cancelReason`.

- [ ] **Step 1: Write `TrainersIT`:** `listIsPublic` (no auth → 200 `$.items[0].fullName`); `adminCreatesTrainerAndPromotesUser` (201; saved user role TRAINER); `createForExistingTrainerGives409` (`ALREADY_EXISTS`); `createForUnknownUserGives404`; `trainerUpdatesSelf` (200); `otherTrainerUpdateGives403`.
- [ ] **Step 2: Write `ClassesIT`:** `listIsPublicWithBookedCount` (`$.items[0].bookedCount == 12` when count stubbed 12, `$.items[0].trainer.fullName == "Dana Serikova"`); `limitOver100Gives422` (`GET /classes?limit=101` → 422); `adminCreatesClass` (201 SCHEDULED); `trainerCreatingForOtherTrainerGives403`; `overlapGives409ScheduleConflict`; `startInPastGives422`; `capacityBelowBookedGives409` (`INVALID_STATE`); `updateCancelledClassGives409`; `cancelCascadesBookings` (two BOOKED bookings saved CANCELLED with `cancelledAt == NOW`, `$.status == "CANCELLED"`); `demotedTrainerCannotCancel` (role CLIENT with trainer profile → 403); `clientCannotSeeAttendance` (403); `unknownClassGives404`.
- [ ] **Step 3: Run** `$MVN -q test -Dtest='TrainersIT,ClassesIT'` → FAIL.
- [ ] **Step 4: Implement.**
- [ ] **Step 5: Run** `$MVN -q test` → all PASS.
- [ ] **Step 6: Commit** `git add -A && git commit -m "feat: trainers and class schedule business logic"`.

---

### Task 4: Memberships

**Files:**
- Create: `dto/MembershipPlanResponse(Long id, String name, int durationDays, Integer visitLimit, int price)`, `dto/MembershipResponse(Long id, Long clientId, MembershipPlanResponse plan, LocalDate startDate, LocalDate endDate, Integer visitsLeft, MembershipStatus status)`.
- Modify: `ResponseMapper` — `MembershipPlanResponse plan(MembershipPlan)`, `MembershipResponse membership(Membership)`.
- Create: `service/MembershipService` — `List<MembershipPlanResponse> listPlans()`, `MembershipResponse buy(AuthUser, BuyMembershipRequest)`, `List<MembershipResponse> listMine(AuthUser)`, `LocalDate today()` (`LocalDate.now(clock.withZone(gymZone))`), `List<Membership> findCovering(Long clientId, LocalDate date)` (ACTIVE and `coversDate`, sorted by `endDate` asc), `Optional<Membership> findUsable(Long clientId, LocalDate date)` (first of `findCovering` with `hasVisitsLeft()`).
- Modify: `controller/MembershipController` → JSON.
- Test: `MembershipsIT.java`.

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: `MembershipService.today/findCovering/findUsable`, `ResponseMapper.membership`.

Rules: plan missing → 404; `startDate` default `today()`; `startDate < today()` → 422 field `startDate`; `endDate = startDate + durationDays − 1`; overlap with any ACTIVE/FROZEN (`findByClientIdAndStatusIn`) → 409 MEMBERSHIP_OVERLAP; `visitsLeft = plan.visitLimit`; status ACTIVE.

- [ ] **Step 1: Write `MembershipsIT`:** `plansArePublic` (3 plans, `$[0].visitLimit` null); `buyDefaultsToToday` (plan 1 → `$.startDate == "2026-11-16"`, `$.endDate == "2026-12-15"`, `$.status == "ACTIVE"`); `buyLimitedPlanSetsVisits` (plan 2 → `$.visitsLeft == 12`); `todayUsesGymZone` (clock 2026-11-16T20:00Z → `$.startDate == "2026-11-17"`); `overlapWithActiveGives409`; `expiredMembershipDoesNotBlock` (only EXPIRED in history → 201); `pastStartDateGives422`; `unknownPlanGives404`; `listMineReturnsHistory`.
- [ ] **Step 2: Run** `$MVN -q test -Dtest=MembershipsIT` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `$MVN -q test` → all PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "feat: membership purchase and validity rules"`.

---

### Task 5: Bookings

**Files:**
- Create: `service/BookingService` — `BookingResponse create(AuthUser, CreateBookingRequest, UUID idempotencyKey)`, `PageResponse<BookingResponse> listMine(AuthUser, BookingStatus, Boolean upcoming, int limit, int offset)`, `BookingResponse get(AuthUser, Long id)`, `BookingResponse cancel(AuthUser, Long id)`.
- Modify: `controller/BookingController` → JSON, `@PreAuthorize("hasRole('CLIENT')")` on create.
- Test: `BookingsIT.java`.

**Interfaces:**
- Consumes: `MembershipService.findUsable`, `MembershipService.today`, `ResponseMapper.booking`, `Permissions.isTrainerOf`, `GymClassRepository.findByIdForUpdate`.
- Produces: `BookingService` (used by nothing else).

Rules: idempotency key hit → return existing booking (201, no further checks, no save). Class via `findByIdForUpdate` (404). Then in order: status SCHEDULED and `startTime > now` else 409 CLASS_NOT_BOOKABLE; `findUsable(clientId, startTime as date in gymZone)` else 403 NO_ACTIVE_MEMBERSHIP; `findActiveByClientIdAndClassId` empty else 409 ALREADY_BOOKED; `countActiveByClassId < capacity` else 409 CLASS_FULL. New booking BOOKED, `bookedAt = now`, stores key. Get: owner, STAFF, ADMIN, trainer of class, else 403. Cancel: owner or ADMIN else 403; status BOOKED else 409 INVALID_STATE; non-admin requires `now <= startTime − 2h` else 409 CANCEL_DEADLINE_PASSED.

- [ ] **Step 1: Write `BookingsIT`:** `bookingSucceeds` (201 `$.status == "BOOKED"`, `$.class.id == 42`); `idempotentRepeatReturnsSameBooking` (key found → 201 same id, `verify(bookingRepository, never()).save(any())`); `unknownClassGives404`; `cancelledClassNotBookable`; `classStartingNowNotBookable` (startTime == NOW → CLASS_NOT_BOOKABLE); `noMembershipGives403`; `membershipEndingBeforeClassDateGives403`; `alreadyBookedGives409`; `fullClassGives409`; `ruleOrderMembershipBeforeFull` (full + no membership → NO_ACTIVE_MEMBERSHIP); `trainerCannotBook` (403); `ownerCancelsInTime` (class in 3 h → 200 CANCELLED); `cancelExactlyTwoHoursBeforeAllowed`; `cancelInsideTwoHoursGives409` (`CANCEL_DEADLINE_PASSED`); `adminCancelsAnytime`; `cancelOthersBookingGives403`; `cancelTwiceGives409` (`INVALID_STATE`); `otherClientCannotView` (403); `classTrainerCanView` (200); `listMinePassesFilters` (verify `searchByClient(17L, BOOKED, true, NOW, 20, 0)`).
- [ ] **Step 2: Run** `$MVN -q test -Dtest=BookingsIT` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `$MVN -q test` → all PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "feat: class booking rules, idempotency and cancellation deadline"`.

---

### Task 6: Access — QR, check-in, visits

**Files:**
- Create: `dto/QrTokenResponse(String token, Instant expiresAt)`, `dto/UserSummary(Long id, String fullName, String photoUrl)`, `dto/CheckInResponse(CheckInDecision decision, DenyReason reason, UserSummary user, MembershipResponse membership, BookingResponse booking, Long visitId)`, `dto/VisitResponse(Long id, Long userId, String gate, Instant checkedInAt, Long bookingId)`.
- Create: `service/AccessService` — `QrTokenResponse issueQr(AuthUser)`, `CheckInResponse checkIn(CheckInRequest)`, `PageResponse<VisitResponse> listVisits(AuthUser, String userId, Instant from, Instant to, int limit, int offset)`.
- Modify: `controller/AccessController` → JSON; `@PreAuthorize("hasRole('CLIENT')")` on qr-token, `hasRole('STAFF')` on check-in.
- Test: `AccessIT.java`.

**Interfaces:**
- Consumes: `TokenService.issueQr/parseQr`, `MembershipService.today/findCovering/findUsable`, `ResponseMapper`.

Rules: QR — `findUsable(user, today())` else 403 NO_ACTIVE_MEMBERSHIP; nonce = random UUID; expiresAt = now + 60 s. Check-in order: `parseQr` → TOKEN_INVALID/TOKEN_EXPIRED; `existsByNonce` → TOKEN_ALREADY_USED; `findCovering` empty → NO_ACTIVE_MEMBERSHIP; none with `hasVisitsLeft` → NO_VISITS_LEFT. GRANTED: save `UsedQrNonce(nonce, expiresAt)`; limited membership `visitsLeft − 1`, at 0 → status EXPIRED; booking from `findBookedByClientWithClassStartBetween(user, now − 30m, now + 30m)` (first) → ATTENDED, `checkedInAt = now`; save `Visit(user, gate, now, bookingId)`. Visits — STAFF/ADMIN: `userId` null → all, "me" → self, number → that user; others: only "me" or own id, anything else (incl. null) → 403.

- [ ] **Step 1: Write `AccessIT`:** `qrIssuedForMember` (`$.expiresAt == "2026-11-16T10:01:00Z"`); `qrWithoutMembershipGives403`; `checkInGrantedRoundTrip` (client gets QR, `as(5, STAFF)` checks in with gate `reception-1` → `$.decision == "GRANTED"`, `$.visitId` set, `$.membership.visitsLeft == 11`, booking at NOW+20m saved ATTENDED and returned in `$.booking`); `bookingOutsideWindowUntouched` (class at NOW+40m → `$.booking` null); `lastVisitExpiresMembership` (visitsLeft 1 → saved with 0 and EXPIRED); `garbageTokenIsInvalid`; `accessTokenScannedAsQrIsInvalid`; `expiredQr` (advance clock 61 s → `TOKEN_EXPIRED`); `reusedNonceDenied` (`existsByNonce` true → `TOKEN_ALREADY_USED`); `noVisitsLeftDenied` (ACTIVE with visitsLeft 0 → `NO_VISITS_LEFT`); `qrTokenAsBearerGives401` (`GET /users/me` with QR as Bearer → 401); `clientCannotCheckIn` (403); `clientSeesOwnVisits` (`userId=me` → `search(17L, …)`); `clientOtherUserVisitsGives403`; `staffSeesAllVisits` (no userId → `search(null, …)`).
- [ ] **Step 2: Run** `$MVN -q test -Dtest=AccessIT` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `$MVN -q test` → all PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "feat: QR entry tokens, check-in decisions and visit log"`.

---

### Task 7: Devices and metrics

**Files:**
- Create: `dto/DeviceProviderResponse(String code, String name, boolean enabled)`, `dto/DeviceResponse(Long id, String provider, String name, DeviceStatus status, Instant connectedAt, Instant lastSyncAt)`, `dto/SyncResultResponse(int imported, Instant lastSyncAt)`, `dto/MetricResponse(Long id, Long deviceId, MetricType type, double value, String unit, Instant recordedAt)`.
- Create: `service/StubMetricGenerator` (`@Component`) — `List<Metric> generate(Device, Instant from, Instant to)`: one HEART_RATE (bpm, 60–160), STEPS (steps, 0–1500), CALORIES (kcal, 20–400) per full hour in `(from, to]`, values from `RandomGenerator`.
- Create: `service/DeviceService` — `List<DeviceProviderResponse> providers()` (stub "Stub device (fake data)" enabled; garmin "Garmin Connect", fitbit "Fitbit" disabled), `List<DeviceResponse> listMine(AuthUser)`, `DeviceResponse connect(AuthUser, ConnectDeviceRequest)`, `void disconnect(AuthUser, Long id)`, `SyncResultResponse sync(AuthUser, Long id)`, `List<MetricResponse> metrics(AuthUser, MetricType, Instant from, Instant to, Long bookingId)`.
- Modify: `controller/DeviceController` → JSON.
- Test: `DevicesIT.java`.

**Interfaces:**
- Consumes: `BookingRepository.findById`, `GymClassRepository.findById`.

Rules: unknown/disabled provider → 422 field `provider`; stub → CONNECTED, `connectedAt = now`, name default provider name. Disconnect/sync on missing or foreign device → 404; sync on non-CONNECTED → 404. Sync window from `lastSyncAt` or now − 24 h to now; `lastSyncAt = now`. Metrics with `bookingId`: not own → 404; window `[class.startTime, class.endTime()]` overrides from/to.

- [ ] **Step 1: Write `DevicesIT`:** `providersArePublic` (3 items, `$[0].code == "stub"`, `$[0].enabled == true`); `connectStub` (201 CONNECTED, `$.name == "Stub device (fake data)"` when name omitted); `connectDisabledProviderGives422` (garmin → `$.errors[0].field == "provider"`); `disconnectOwn` (204, saved DISCONNECTED); `disconnectForeignGives404`; `firstSyncImports24Hours` (`$.imported == 72`, `$.lastSyncAt == "2026-11-16T10:00:00Z"`); `syncSinceLastSync` (lastSyncAt NOW−2h → `$.imported == 6`); `syncDisconnectedGives404`; `metricsForBookingUseClassWindow` (verify `search(17L, null, class.start, class.end)`); `metricsForForeignBookingGives404`.
- [ ] **Step 2: Run** `$MVN -q test -Dtest=DevicesIT` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `$MVN -q test` → all PASS.
- [ ] **Step 5: Commit** `git add -A && git commit -m "feat: stub fitness devices, sync and metrics"`.

---

### Task 8: Scheduled jobs, README, full verification

**Files:**
- Create: `job/StatusJobs` (`@Component`) — `void finishEndedClasses()` (`findScheduledEndedBefore(now)` → FINISHED; their BOOKED bookings → NO_SHOW), `void expireMemberships()` (`findActiveEndedBefore(today())` → EXPIRED); `job/SchedulingConfig` (`@EnableScheduling`, `@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")`) with triggers: `fixedDelay` 5 min for classes, cron `0 5 0 * * *` zone `${app.time-zone}` for memberships (triggers live in `SchedulingConfig` calling `StatusJobs`, so the bean is testable without scheduling).
- Modify: `README.md` — API is JSON per contract, JWT flow, how to run tests (`mvn test`), note that the app needs repository implementations before `spring-boot:run`.
- Test: `JobsIT.java`.

- [ ] **Step 1: Write `JobsIT`:** `endedClassFinishesAndNoShows` (class ended → saved FINISHED; BOOKED booking → NO_SHOW; ATTENDED booking not saved); `expiredMembershipsMarked` (endDate 2026-11-15 → saved EXPIRED).
- [ ] **Step 2: Run** `$MVN -q test -Dtest=JobsIT` → FAIL.
- [ ] **Step 3: Implement** jobs and README.
- [ ] **Step 4: Run full suite** `$MVN test` → `BUILD SUCCESS`, 0 failures.
- [ ] **Step 5: Commit** `git add -A && git commit -m "feat: status jobs and README for business logic"`.
