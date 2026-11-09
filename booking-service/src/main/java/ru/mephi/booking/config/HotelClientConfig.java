package ru.mephi.booking.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.mephi.booking.integration.*;
import ru.mephi.booking.security.TokenService;

@Configuration
public class HotelClientConfig {
    @Bean
    @LoadBalanced RestClient.Builder discoveredRestClient() {
        return RestClient.builder();
    }
    @Bean HotelPort hotelPort(@LoadBalanced RestClient.Builder discovered, TokenService tokens,
    @Value("${hotel.base-url}") String baseUrl, @Value("${hotel.discovery:true}") boolean discovery,
    @Value("${hotel.timeout-ms:1200}") long timeout, @Value("${hotel.max-attempts:3}") int attempts,
    @Value("${hotel.retry-delay-ms:150}") long delay) {
        var transport = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout)).build();
        var factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofMillis(timeout));
        RestClient client = (discovery ? discovered : RestClient.builder()).baseUrl(baseUrl).requestFactory(factory).build();
        return new HotelHttpClient(client, tokens, attempts, delay);
    }
}
