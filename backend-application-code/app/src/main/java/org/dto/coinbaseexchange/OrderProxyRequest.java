package org.dto.coinbaseexchange;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderProxyRequest extends ProxyRequest {
    private String side;
    private String productId; // Renamed from product_id for Java convention
    private String type; // Should be "market" for market orders
    private String size; // For market sell orders (amount of base currency)
    private String funds; // For market buy orders (amount of quote currency to spend)
}
