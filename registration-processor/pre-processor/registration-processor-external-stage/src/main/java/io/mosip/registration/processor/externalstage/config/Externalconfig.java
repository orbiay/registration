package io.mosip.registration.processor.externalstage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import io.mosip.registration.processor.externalstage.stage.ExternalStage;

/**
 * external stage beans configuration class
 *
 */
@Configuration
@EnableAspectJAutoProxy
public class Externalconfig {
	/**
	 * ExternalStage bean
	 */
	@Bean
	public ExternalStage externalStage() {
		return new ExternalStage();
	}
}
