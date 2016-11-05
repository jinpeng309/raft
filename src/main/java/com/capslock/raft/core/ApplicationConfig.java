package com.capslock.raft.core;

import org.h2.mvstore.MVStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by capslock1874.
 */
@Configuration
public class ApplicationConfig {
    @Value("${storage.dir}")
    private String storageDir;

    @Bean
    public MVStore mvStore() {
        return MVStore.open(storageDir.concat("\\raft"));
    }
}
