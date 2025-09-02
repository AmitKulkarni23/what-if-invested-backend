package org.dto;

import lombok.Data;

@Data
public class CoinbaseCharge {
    private String id;
    private String code;
    private String hosted_url;
    private String created_at;
}
