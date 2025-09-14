package org.dto.coinbaseexchange;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "action"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CandlesRequest.class, name = "getCandles"),
        @JsonSubTypes.Type(value = OrderProxyRequest.class, name = "placeOrder")
})
@Data
public class ProxyRequest {
    // Base class for different proxy actions
}
