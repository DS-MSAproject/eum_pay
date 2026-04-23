package com.eum.rag;

import com.eum.rag.common.config.properties.RagAiProperties;
import com.eum.rag.common.config.properties.RagAsyncProperties;
import com.eum.rag.common.config.properties.RagChatProperties;
import com.eum.rag.common.config.properties.RagDocumentProperties;
import com.eum.rag.common.config.properties.RagRetrievalProperties;
import com.eum.rag.common.config.properties.RagSessionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        RagAiProperties.class,
        RagAsyncProperties.class,
        RagChatProperties.class,
        RagDocumentProperties.class,
        RagRetrievalProperties.class,
        RagSessionProperties.class
})
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
