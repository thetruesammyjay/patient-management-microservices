package com.patientmanagement.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class JwtValidationGatewayFilterFactory
    extends AbstractGatewayFilterFactory<Object> {

  private final WebClient webClient;

  public JwtValidationGatewayFilterFactory(WebClient.Builder builder,
      @Value("${AUTH_SERVICE_URL:http://localhost:4005}") String authServiceUrl) {
    this.webClient = builder.baseUrl(authServiceUrl).build();
  }

  @Override
  public GatewayFilter apply(Object config) {
    return (exchange, chain) -> {
      String authorization = exchange.getRequest().getHeaders()
          .getFirst(HttpHeaders.AUTHORIZATION);
      if (authorization == null || !authorization.startsWith("Bearer ")) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
      }

      return webClient.get()
          .uri("/validate")
          .header(HttpHeaders.AUTHORIZATION, authorization)
          .retrieve()
          .toBodilessEntity()
          .then(chain.filter(exchange));
    };
  }
}
