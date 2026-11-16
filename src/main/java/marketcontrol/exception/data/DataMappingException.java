package marketcontrol.exception.data;

import marketcontrol.exception.PersistenceException;

public class DataMappingException extends PersistenceException{

	private static final long serialVersionUID = 1L;

	public DataMappingException(String message, Throwable cause) {
		super(message, cause);
	}
	public DataMappingException(String message) {
		super(message);
	}
}
