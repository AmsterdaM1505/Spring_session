# Eureka Server

Приложение `ru.mephi.discovery.DiscoveryApplication`, порт 8761. Реестр не регистрирует сам себя и не загружает реестр другого сервера. Самосохранение Eureka включено. Три остальных приложения регистрируются как eureka clients.

Доступ к API реестра и панели - HTTP Basic registry / значение REGISTRY_PASSWORD. Для локального стенда задан RegistryDemo123. Только /actuator/health доступен без пароля; CSRF отключён для служебных путей /eureka/**. Эти полномочия независимы от пользовательского JWT API.

Сборка из корня: `mvn -pl discovery-server -am verify`. Запуск: `java -jar discovery-server/target/discovery-server-1.0.0.jar`. Внешних библиотек, устанавливаемых вручную, нет: Maven использует общий BOM. Собственной JPA-базы нет, реестр размещён в памяти Eureka.

RegistrySecurityTest проверяет защищённый реестр, действительные служебные реквизиты и открытый health endpoint через MockMvc.
