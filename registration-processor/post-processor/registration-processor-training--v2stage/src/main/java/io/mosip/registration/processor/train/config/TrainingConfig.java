package io.mosip.registration.processor.train.config;

import io.mosip.registration.processor.train.stage.TrainingStage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TrainingConfig {
    @Bean
    public TrainingStage getTrainStage() {
        return new TrainingStage();
    }
}
