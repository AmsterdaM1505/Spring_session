# Контракт REST API

Все внешние запросы идут на `http://localhost:8080`. JSON, даты `yyyy-MM-dd`, идентификатор брони UUID. Авторизация: `Authorization: Bearer <accessToken>`. В таблице U означает USER или ADMIN, A - ADMIN, I - отдельный служебный JWT.

| Метод | Путь | Доступ | Тело или параметры | Успех |
| --- | --- | --- | --- | --- |
| POST | /api/users/register | открытый | username, password | 201 TokenView |
| POST | /api/users/auth | открытый | username, password | 200 TokenView |
| GET | /api/users | A | нет | 200 UserView[] |
| GET | /api/users/{id} | A | нет | 200 UserView |
| POST | /api/users | A | username, password, role | 201 UserView |
| PATCH | /api/users/{id} | A | username?, password?, role? | 200 UserView |
| DELETE | /api/users/{id} | A | нет | 204 |
| POST | /api/bookings | U | CreateBooking | 201 / 200 / 202 BookingView |
| GET | /api/bookings | U | page=0, size=20; size 1..100 | 200 History |
| GET | /api/bookings/{id} | U, свой | нет | 200 BookingView |
| DELETE | /api/bookings/{id} | U, свой | нет | 200 BookingView |
| GET | /api/hotels | U | query? поиск по имени и адресу | 200 HotelView[] |
| GET | /api/hotels/{id} | U | нет | 200 HotelView |
| POST | /api/hotels | A | name, address | 201 HotelView |
| PUT | /api/hotels/{id} | A | name, address | 200 HotelView |
| DELETE | /api/hotels/{id} | A | нет | 204 либо 409 при наличии номеров |
| GET | /api/hotels/{id}/occupancy | A | startDate, endDate | 200 HotelStatistics |
| GET | /api/rooms | U | startDate, endDate, hotelId?, sort=id или number | 200 RoomView[] |
| GET | /api/rooms/recommend | U | startDate, endDate, hotelId? | 200 RoomView[] |
| GET | /api/rooms/{id} | U | нет | 200 RoomView |
| POST | /api/rooms | A | hotelId, number, available | 201 RoomView |
| PUT | /api/rooms/{id} | A | number, available | 200 RoomView |
| DELETE | /api/rooms/{id} | A | нет | 204 либо 409 при наличии истории |
| POST | /api/rooms/{id}/confirm-availability | I, напрямую 8082 | SlotRequest | 204 |
| POST | /api/rooms/{id}/release | I, напрямую 8082 | SlotRequest | 204 |

Имя пользователя: 3..64 символа латинского алфавита, цифры, `_`, `.` и `-`. Пароль: 10..72 символа и не более 72 байт UTF-8. Длина name отеля не более 120, address не более 250, number номера не более 30; пустые значения запрещены. Роль: USER либо ADMIN.

Ручное бронирование:

```json
{"requestId":"92c04d4b-a97a-4497-aef9-f287bba08c31","roomId":1,"autoSelect":false,"startDate":"2030-02-01","endDate":"2030-02-04"}
```

Автоматическое бронирование:

```json
{"requestId":"bfad27ba-f5f7-442b-905a-a78c5d225cd1","hotelId":1,"autoSelect":true,"startDate":"2030-02-01","endDate":"2030-02-04"}
```

Даты в примерах заменяются будущими при необходимости. hotelId при автоматическом выборе необязателен: без него рассматриваются все отели. autoSelect по умолчанию false, тогда roomId обязателен. Для autoSelect допустимое поле roomId игнорируется.

BookingView содержит `id, requestId, roomId, startDate, endDate, status, createdAt, failureCode, compensationPending`. У PENDING до выбора номера roomId может быть null. У отменённой брони compensationPending=true означает, что release ещё требует подтверждения. CANCELLED не гарантирует немедленное освобождение при недоступном Hotel: до фонового повтора слот консервативно остаётся занят.

History: `content, page, size, totalElements, totalPages`. История отсортирована по createdAt и id по убыванию. ADMIN видит через этот API только собственные бронирования. Учётные записи администрируются отдельно.

RoomView: `id, hotelId, number, available, timesBooked`. GET /api/rooms возвращает только эксплуатационно доступные и свободные на заданные даты номера; GET /api/rooms/{id} - сведения о конкретном номере без утверждения его свободы на период. recommend применяет порядок timesBooked ASC, id ASC. Доступность в ответе носит справочный характер до атомарного confirm.

HotelStatistics: `hotelId, roomCount, occupiedNights, capacityNights, occupancyPercent, rooms`. В строке номера: `roomId, number, available, timesBooked, occupiedNights, periodNights, occupancyPercent`. Загрузка округлена до двух знаков. Отчётный интервал может относиться к прошлому; новые бронирования и поиск свободных номеров - только к текущей или будущей дате.

SlotRequest: `bookingId, requestId, startDate, endDate`. Все поля обязательны. Повтор той же операции возвращает 204 без повторного счётчика. Иное содержимое того же bookingId либо confirm после release возвращают 409. Release неизвестной операции сохраняет отменяющую запись; release не создаёт действующую занятость.

Ошибки сервисов и безопасности используют ApiError:

```json
{"timestamp":"2030-01-01T10:00:00Z","status":409,"code":"ROOM_BUSY","message":"Номер недоступен на выбранные даты","traceId":"c997d6b7-2e1c-426d-9d40-1dd6ad31df9a"}
```

| Код HTTP | Смысл |
| --- | --- |
| 400 | Неверный JSON, даты, сортировка, пагинация или ограничения полей |
| 401 | Нет JWT, неверная подпись, истёкший токен, другой issuer/audience, неверный пароль |
| 403 | Недостаточно роли или запрещённый внутренний маршрут Gateway |
| 404 | Нет объекта либо бронь принадлежит другому пользователю |
| 409 | Пересечение дат, изменённый повтор, уникальность, связи, конкурирующее изменение |
| 503 | Исчерпаны удалённые попытки либо открыт CircuitBreaker |

Ошибки самого Gateway, включая отсутствие сервиса в Eureka, тоже используют ApiError с кодом GATEWAY_ERROR. Сетевой 503 при создании брони следует уточнять повтором того же requestId или запросом истории.
