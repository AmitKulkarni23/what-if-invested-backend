package org.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import org.dagger.ServiceComponent;
import org.dto.CreatePaymentInput;
import org.dto.PaymentLink;
import org.service.CoinbaseApiException;
import org.service.CoinbaseService;
import org.util.DaggerComponentUtil;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CoinbaseMerchantPayments implements
        RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @NonNull
    private final CoinbaseService coinbaseService;

    @NonNull
    private final ObjectMapper objectMapper;

    public CoinbaseMerchantPayments() {
        this(DaggerComponentUtil.create());
    }

    private CoinbaseMerchantPayments(final ServiceComponent serviceComponent) {
        this.coinbaseService = serviceComponent.getCoinbaseService();
        this.objectMapper = serviceComponent.getObjectMapper();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            if (request == null || request.getHttpMethod() == null) {
                return json(400, error("Bad Request"));
            }
            if (!"POST".equalsIgnoreCase(request.getHttpMethod())) {
                return json(405, error("Method Not Allowed"));
            }

            CreatePaymentInput input = parseInput(request.getBody());
            if (input == null) {
                return json(400, error("Invalid request body"));
            }
            log.info("Create charge: amount={} desc={} email={}", input.getAmount(), input.getDescription(), input.getCustomerEmail());

            PaymentLink link = coinbaseService.createCharge(input);
            return json(200, link);
        } catch (CoinbaseApiException e) {
            log.error("Coinbase API error: {}", e.getMessage());
            return json(502, error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unhandled error", e);
            return json(500, error("Internal error: " + e.getMessage()));
        }
    }

    private CreatePaymentInput parseInput(String body) throws JsonProcessingException {
        if (body == null || body.isBlank()) return null;
        return objectMapper.readValue(body, CreatePaymentInput.class);
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
        res.setHeaders(headers);
        return res;
    }

    private Map<String, String> error(String message) {
        Map<String, String> err = new HashMap<>();
        err.put("error", message);
        return err;
    }
}

