package io.mosip.registration.processor.core.packet.dto.datashare;

import java.util.List;

import lombok.Data;

@Data
public class Filter {
	public String language;
	public String type;
	public List<String> subType;
}
