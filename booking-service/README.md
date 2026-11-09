# Booking Service

Приложение `ru.mephi.booking.BookingApplication`, порт 8081, собственная H2 `booking-service`. Отвечает за пользователей, выпуск JWT, историю и координацию бронирования. Полный контракт: [API](../docs/api.md).

BookingCoordinator не имеет @Transactional: сетевые вызовы не держат транзакцию. BookingTransactions - отдельный bean для коротких локальных транзакций. HotelPort отделяет сценарий от HTTP-клиента и позволяет моделировать отказ без запуска другого сервиса. HotelHttpClient содержит ограниченные Retry, тайм-ауты и CircuitBreaker; SagaRecovery повторяет незавершённые компенсации.

Сборка из корня: `mvn -pl booking-service -am verify`. Запуск: `java -jar booking-service/target/booking-service-1.0.0.jar`. Для обычного запуска нужен доступный Eureka и зарегистрированный Hotel. Для отдельного стенда можно указать HOTEL_DISCOVERY=false и HOTEL_BASE_URL=http://localhost:8082.

Настройки hotel.timeout-ms=1200, hotel.max-attempts=3, hotel.retry-delay-ms=150; saga.pending-timeout-seconds=60 и saga.recovery-delay-ms=5000. Значения могут быть переопределены аргументом Spring или переменной окружения в relaxed binding. Параметры безопасности описаны в [ADR](../docs/adr/002-security.md).

BookingIntegrationTest использует реальные H2 и MockMvc с управляемым HotelPort. HotelHttpClientTest отдельно использует настоящий JDK HTTP-сервер: проверяет 503, 409, тайм-аут, число попыток и компенсацию при открытом CircuitBreaker. Docker и внешняя сеть для этих тестов не требуются после загрузки Maven-зависимостей.
