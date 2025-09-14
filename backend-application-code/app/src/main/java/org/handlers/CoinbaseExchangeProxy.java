package org.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dto.coinbaseexchange.CandlesRequest;
import org.dto.coinbaseexchange.OrderProxyRequest;
import org.dto.coinbaseexchange.ProxyRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CoinbaseExchangeProxy implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SecretsManagerClient secretsManagerClient;

    private String apiKey;
    private String apiSecret;
    private String apiPassphrase;

    private static final String API_ENDPOINT = "https://api-public.sandbox.exchange.coinbase.com";

    public CoinbaseExchangeProxy() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
        this.secretsManagerClient = SecretsManagerClient.builder().build();
    }

    private synchronized void loadApiKeys() throws Exception {
        if (apiKey != null && apiSecret != null && apiPassphrase != null) {
            return; // Already loaded
        }

        String secretArn = System.getenv("COINBASE_API_SECRET_ARN");
        if (secretArn == null) {
            throw new RuntimeException("COINBASE_API_SECRET_ARN environment variable is not set.");
        }

        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secretArn)
                .build();
        GetSecretValueResponse getSecretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);

        if (getSecretValueResponse.secretString() != null) {
            Map<String, String> secretMap = objectMapper.readValue(getSecretValueResponse.secretString(), new TypeReference<Map<String, String>>() {});
            apiKey = secretMap.get("apiKey");
            apiSecret = secretMap.get("apiSecret");
            apiPassphrase = secretMap.get("apiPassphrase");
        } else {
            throw new RuntimeException("Secret string is null.");
        }
    }

    private String createSignature(String timestamp, String method, String requestPath, String body) throws NoSuchAlgorithmException, InvalidKeyException {
        String message = timestamp + method + requestPath + body;
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(Base64.getDecoder().decode(apiSecret), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(message.getBytes()));
    }

    private APIGatewayProxyResponseEvent json(int status, Object payload) {
        APIGatewayProxyResponseEvent res = new APIGatewayProxyResponseEvent();
        res.setStatusCode(status);
        try {
            res.setBody(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            res.setBody("{\"error\":\"Serialization error\"}");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", System.getenv("FRONTEND_BASE_URL") != null ? System.getenv("FRONTEND_BASE_URL") : "*");
        headers.put("Access-Control-Allow-Methods", "OPTIONS,GET,POST");
        res.setHeaders(headers);
        return res;
    }

    private Map<String, String> error(String message) {
        Map<String, String> err = new HashMap<>();
        err.put("error", message);
        return err;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            loadApiKeys();

            if (!"POST".equalsIgnoreCase(request.getHttpMethod())) {
                return json(405, error("Method Not Allowed"));
            }

            ProxyRequest proxyRequest = objectMapper.readValue(request.getBody(), ProxyRequest.class);

            if (proxyRequest instanceof CandlesRequest) {
                CandlesRequest candlesRequest = (CandlesRequest) proxyRequest;
                String tradingPair = candlesRequest.getTradingPair();
                int granularity = candlesRequest.getGranularity();

                String requestPath = String.format("/products/%s/candles?granularity=%d", tradingPair, granularity);
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                String signature = createSignature(timestamp, "GET", requestPath, "");

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(API_ENDPOINT + requestPath))
                        .header("CB-ACCESS-KEY", apiKey)
                        .header("CB-ACCESS-SIGN", signature)
                        .header("CB-ACCESS-TIMESTAMP", timestamp)
                        .header("CB-ACCESS-PASSPHRASE", apiPassphrase)
                        .header("Content-Type", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<List<Double>> rawCandles = objectMapper.readValue(response.body(), new TypeReference<List<List<Double>>>() {});
                    return json(200, rawCandles);
                } else {
                    log.error("Failed to fetch candles: {} - {}", response.statusCode(), response.body());
                    return json(response.statusCode(), error(String.format("Failed to fetch candles: %d - %s", response.statusCode(), response.body())));
                }
            } else if (proxyRequest instanceof OrderProxyRequest) {
                OrderProxyRequest orderRequest = (OrderProxyRequest) proxyRequest;
                String side = orderRequest.getSide();
                String productId = orderRequest.getProductId();
                String type = orderRequest.getType();

                String requestPath = "/orders";
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                Map<String, String> orderBody = new HashMap<>();
                orderBody.put("side", side);
                orderBody.put("product_id", productId);
                orderBody.put("type", type); // Ensure this is "market" for market orders

                if ("buy".equalsIgnoreCase(side)) {
                    // For market buy orders, use 'funds'
                    if (orderRequest.getFunds() == null || orderRequest.getFunds().isEmpty()) {
                        return json(400, error("Funds are required for market buy orders."));
                    }
                    orderBody.put("funds", orderRequest.getFunds());
                } else if ("sell".equalsIgnoreCase(side)) {
                    // For market sell orders, use 'size'
                    if (orderRequest.getSize() == null || orderRequest.getSize().isEmpty()) {
                        return json(400, error("Size is required for market sell orders."));
                    }
                    orderBody.put("size", orderRequest.getSize());
                } else {
                    return json(400, error("Invalid order side. Must be 'buy' or 'sell'."));
                }

                String bodyString = objectMapper.writeValueAsString(orderBody);
                String signature = createSignature(timestamp, "POST", requestPath, bodyString);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(API_ENDPOINT + requestPath))
                        .header("CB-ACCESS-KEY", apiKey)
                        .header("CB-ACCESS-SIGN", signature)
                        .header("CB-ACCESS-TIMESTAMP", timestamp)
                        .header("CB-ACCESS-PASSPHRASE", apiPassphrase)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodyString))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return json(200, objectMapper.readValue(response.body(), Object.class));
                } else {
                    log.error("Failed to place order: {} - {}", response.statusCode(), response.body());
                    return json(response.statusCode(), error(String.format("Failed to place order: %d - %s", response.statusCode(), response.body())));
                }
            } else {
                return json(400, error("Unsupported proxy request type"));
            }
        } catch (Exception e) {
            log.error("Unhandled error in proxy lambda", e);
            return json(500, error("Internal server error: " + e.getMessage()));
        }
    }
}

