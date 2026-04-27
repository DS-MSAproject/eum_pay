package com.eum.cartserver.message;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderCancelledMessage {

    private String eventId;

    @JsonAlias({"order_id"})
    private Long orderId;

    @JsonAlias({"user_id"})
    private Long userId;

    private String reason;
}
