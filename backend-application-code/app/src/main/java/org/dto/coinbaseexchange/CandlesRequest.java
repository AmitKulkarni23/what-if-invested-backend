package org.dto.coinbaseexchange;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandlesRequest extends ProxyRequest {
    private String tradingPair;
    private int granularity;
}
