package io.mosip.registration.processor;

import io.mosip.registration.processor.train.stage.TrainingStage;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class TrainingApplication {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.scan("io.mosip.registration.processor.core.config", "io.mosip.registration.processor.train.config",
                "io.mosip.registration.processor.rest.client.config",
                "io.mosip.registration.processor.core.kernel.beans",
                "io.mosip.registration.processor.status.config",
                "io.mosip.registration.processor.packet.storage.config");
        ctx.refresh();

        TrainingStage trainingStage = ctx.getBean(TrainingStage.class);
        trainingStage.deployVerticle();

    }}