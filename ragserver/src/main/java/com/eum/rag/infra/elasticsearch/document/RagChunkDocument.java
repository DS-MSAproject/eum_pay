package com.eum.rag.infra.elasticsearch.document;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChunkDocument {

    @Id
    private String chunkId;
    private String documentId;
    private String filename;
    private String category;
    private Integer version;
    private Integer chunkIndex;
    private String text;
    private List<Float> embedding;
    private Boolean active;
    private OffsetDateTime createdAt;
}
