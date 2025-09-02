package org.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.config.CoinbaseConfig;
import org.dto.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class CoinbaseService {
    private static final String COINBASE_CHARGES_URL = "https://api.commerce.coinbase.com/charges";
    private static final String COINBASE_API_VERSION = "2018-03-22";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final CoinbaseConfig config;

    public PaymentLink createCharge(CreatePaymentInput input) {
        validate(input);

        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new CoinbaseApiException("Missing COINBASE_COMMERCE_API_KEY env var");
        }

        try {
            CoinbaseCreateCharge body = new CoinbaseCreateCharge();
            body.setPricing_type("fixed_price");
            body.setLocal_price(new LocalPrice(formatAmount(input.getAmount()), "USD"));
            body.setName(Optional.ofNullable(input.getDescription()).orElse("Payment"));
            body.setDescription(input.getDescription());

            Map<String, String> metadata = new HashMap<>();
            if (input.getCustomerEmail() != null && !input.getCustomerEmail().isBlank()) {
                metadata.put("customer_email", input.getCustomerEmail());
            }
            body.setMetadata(metadata.isEmpty() ? null : metadata);

            body.setRedirect_url(config.getFrontendBaseUrl());
            body.setCancel_url(config.getFrontendBaseUrl());

            String payload = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(COINBASE_CHARGES_URL))
                    .header("Content-Type", "application/json")
                    .header("X-CC-Api-Key", config.getApiKey())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            log.info("Creating Coinbase charge: amount={} currency=USD", input.getAmount());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String msg = extractErrorMessage(response.body());
                log.error("Coinbase API error status={} body={}", response.statusCode(), response.body());
                throw new CoinbaseApiException("Coinbase API error: " + msg);
            }

            CoinbaseChargeResponse chargeRes = mapper.readValue(response.body(), CoinbaseChargeResponse.class);
            if (chargeRes == null || chargeRes.getData() == null || chargeRes.getData().getCode() == null || chargeRes.getData().getHosted_url() == null) {
                throw new CoinbaseApiException("Unexpected Coinbase response");
            }

            PaymentLink link = new PaymentLink();
            link.setId(chargeRes.getData().getCode());
            link.setChargeId(chargeRes.getData().getCode());
            link.setHostedUrl(chargeRes.getData().getHosted_url());
            link.setCreatedAt(chargeRes.getData().getCreated_at());
            link.setAmount(input.getAmount());
            link.setCurrency("USD");
            link.setDescription(input.getDescription());
            link.setCustomerEmail(input.getCustomerEmail());
            link.setAccepted(input.getAccepted());
            link.setStatus("pending");
            return link;

        } catch (Exception e) {
            if (e instanceof CoinbaseApiException) throw (CoinbaseApiException) e;
            throw new CoinbaseApiException("Internal error: " + e.getMessage(), e);
        }
    }

    private void validate(CreatePaymentInput input) {
        if (input == null || input.getAmount() == null || input.getAmount() <= 0) {
            throw new CoinbaseApiException("Invalid amount");
        }
    }

    private String extractErrorMessage(String body) {
        try {
            Map<?, ?> root = mapper.readValue(Optional.ofNullable(body).orElse("{}"), Map.class);
            Object error = root.get("error");
            if (error instanceof Map) {
                Object message = ((Map<?, ?>) error).get("message");
                if (message != null) return message.toString();
            }
            return body;
        } catch (Exception ex) {
            return body;
        }
    }

    private static String formatAmount(Double amount) {
        return String.format(java.util.Locale.US, "%.2f", amount);
    }
}

