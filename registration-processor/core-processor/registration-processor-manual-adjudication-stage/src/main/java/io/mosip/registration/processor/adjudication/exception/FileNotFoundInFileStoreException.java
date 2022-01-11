package io.mosip.registration.processor.adjudication.exception;

import io.mosip.kernel.core.exception.BaseUncheckedException;

public class FileNotFoundInFileStoreException extends BaseUncheckedException {
	
	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a new no record assigned exception.
	 *
	 * @param code the code
	 * @param message the message
	 */
	public FileNotFoundInFileStoreException(String code, String message){
		super(code, message);
	}

}