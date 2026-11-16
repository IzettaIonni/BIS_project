# Gym Booking Backend

Бэкенд проекта. Стек: Spring Boot 4, Java 21, Maven, Angular.

API работает с JSON согласно контракту, ошибки возвращаются как `application/problem+json`.

Структура (слои):

- `controller` — эндпоинты, тонкий слой над сервисами
- `service` — бизнес-логика (расписание, записи, абонементы, доступ по QR, устройства)
- `repository` — интерфейсы доступа к данным
- `domain` — сущности и enum'ы
- `dto` — тела запросов и ответов из контракта
- `security` — JWT, роли, разбор токенов
- `job` — плановые задачи (завершение прошедших занятий, истечение абонементов)
- `error` — ошибки и их вывод в формате problem+json

## Аутентификация

- `POST /auth/register` или `POST /auth/login` возвращает пару токенов: access-токен (JWT, живёт 15 минут) передаётся в заголовке `Authorization: Bearer <token>`.
- `POST /auth/refresh` меняет refresh-токен на новую пару (ротация, refresh живёт 30 дней, старый становится недействительным).
- `POST /auth/logout` отзывает все refresh-токены пользователя.
- Пароль при регистрации: от 8 до 72 символов (и не более 72 байт в UTF-8, ограничение BCrypt).
- Роли: `CLIENT`, `TRAINER`, `STAFF`, `ADMIN`; доступ к эндпоинтам зависит от роли.

## Запуск и тесты

Тесты: `mvn test`. Интеграционные тесты (`*IT`) поднимают Spring-контекст с замоканными репозиториями и фиксированными часами; плановые задачи в них отключены (`app.jobs.enabled=false`).

Настройки:

- `APP_JWT_SECRET` — секрет подписи JWT (не менее 32 байт). В продакшене обязательно задать; значение по умолчанию в `application.yml` годится только для разработки.
- `app.time-zone` — часовой пояс зала (по умолчанию `Asia/Almaty`): от него зависят «сегодня» для абонементов и их истечение.

Ошибки: все ответы с ошибкой — `application/problem+json`. Коды из контракта плюс один дополнительный, `INTERNAL_ERROR` (500, непредвиденная ошибка сервера; в контракте такого кода нет). Ответы 404/405/415 от самого фреймворка тоже приходят в этом формате (404 — `NOT_FOUND`, 405 и 415 — `VALIDATION_FAILED`).

Приложение пока нельзя запустить через `spring-boot:run`: реализаций репозиториев ещё нет (ждём SQL-схему).

адрес локального апи: http://localhost:8080/api/v1/

контракт апи лежит в ресурсах проекта по адресу src/main/resources/static
заготовка по фронтэнду пока не функционирует, но находится в процессе написания

## API

Базовый адрес: `http://localhost:8080/api/v1`

| Метод | Путь | Что делает | Успешный ответ |
|---|---|---|---|
| POST | /auth/register | Регистрация клиента | 201 |
| POST | /auth/login | Вход по email и паролю | 200 |
| POST | /auth/refresh | Новая пара токенов по refresh-токену | 200 |
| POST | /auth/logout | Выход | 204 |
| GET | /users/me | Мой профиль | 200 |
| PATCH | /users/me | Изменить имя / телефон | 200 |
| GET | /users | Список пользователей (role, q, limit, offset) | 200 |
| PATCH | /users/{userId}/role | Сменить роль пользователя | 200 |
| GET | /trainers | Список тренеров (specialization, limit, offset) | 200 |
| POST | /trainers | Сделать пользователя тренером | 201 |
| GET | /trainers/{trainerId} | Тренер по id | 200 |
| PATCH | /trainers/{trainerId} | Изменить тренера | 200 |
| GET | /classes | Расписание (from, to, trainerId, type, onlyAvailable, limit, offset) | 200 |
| POST | /classes | Создать занятие | 201 |
| GET | /classes/{classId} | Занятие по id | 200 |
| PATCH | /classes/{classId} | Изменить занятие | 200 |
| POST | /classes/{classId}/cancel | Отменить занятие | 200 |
| GET | /classes/{classId}/bookings | Записи на занятие | 200 |
| POST | /bookings | Записаться на занятие | 201 |
| GET | /users/me/bookings | Мои записи (status, upcoming, limit, offset) | 200 |
| GET | /bookings/{bookingId} | Запись по id | 200 |
| POST | /bookings/{bookingId}/cancel | Отменить запись | 200 |
| GET | /membership-plans | Тарифы абонементов | 200 |
| POST | /memberships | Купить абонемент | 201 |
| GET | /users/me/memberships | Мои абонементы | 200 |
| POST | /access/qr-token | QR-токен для входа (живёт 60 сек) | 200 |
| POST | /access/check-in | Проверка QR на входе (GRANTED / DENIED) | 200 |
| GET | /access/visits | Журнал посещений (userId, from, to, limit, offset) | 200 |
| GET | /device-providers | Поддерживаемые устройства | 200 |
| GET | /users/me/devices | Мои устройства | 200 |
| POST | /users/me/devices | Подключить устройство | 201 |
| DELETE | /users/me/devices/{deviceId} | Отключить устройство | 204 |
| POST | /users/me/devices/{deviceId}/sync | Синхронизировать устройство | 200 |
| GET | /users/me/metrics | Мои метрики (type, from, to, bookingId) | 200 |

## Frontend
