package com.eum.authserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KafkaLagResponse {

    private String groupId;
    private String topic;
    private int partition;
    private long currentOffset;
    private long endOffset;
    private long lag;
}
