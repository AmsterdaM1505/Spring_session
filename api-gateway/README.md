# API Gateway

Приложение `ru.mephi.gateway.GatewayApplication`, порт 8080. Использует Spring Cloud Gateway WebFlux и Eureka LoadBalancer. Маршруты объявлены в GatewayRoutes с URI `lb://booking-service` и `lb://hotel-service`; discovery locator не включён, произвольные сервисные пути наружу не публикуются.

JWT не удаляется при проксировании и независимо проверяется на шлюзе и в предметном сервисе. Входящие X-Request-Id нормализуются как UUID; значение передаётся дальше и возвращается клиенту. Входящий X-Booking-Id удаляется, служебную связь задаёт сам Booking. Внутренние confirm/release запрещены на входе.

Сборка из корня: `mvn -pl api-gateway -am verify`. Запуск: `java -jar api-gateway/target/api-gateway-1.0.0.jar`. Базы предметных данных нет. Ключ проверки - USER_PUBLIC_KEY, адрес реестра - EUREKA_URL. Ошибки аутентификации и самого шлюза имеют общий формат ApiError.

GatewaySecurityTest использует WebTestClient для проверки 401, 403, traceId и динамических URI. Полное проксирование действительного JWT проверяется сценарием scripts/e2e.py после запуска всех приложений.
