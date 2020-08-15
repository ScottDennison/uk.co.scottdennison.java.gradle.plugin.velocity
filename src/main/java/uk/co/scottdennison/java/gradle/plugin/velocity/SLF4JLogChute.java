package uk.co.scottdennison.java.gradle.plugin.velocity;

/*
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SLF4JLogChute implements LogChute {
	private static final String RUNTIME_LOG_LOGSYSTEM_SLF4J_LOGGER_NAME = "runtime.log.logsystem.slf4j.logger.name";

	private Logger logger;

	public SLF4JLogChute() {
		this.logger = null;
	}

	public SLF4JLogChute(Logger logger) {
		this.logger = logger;
	}

	@Override
	public void init(RuntimeServices rs) throws Exception {
		if (this.logger == null) {
			String loggerName = (String)rs.getProperty(RUNTIME_LOG_LOGSYSTEM_SLF4J_LOGGER_NAME);
			if (loggerName == null) {
				this.logger = LoggerFactory.getLogger(this.getClass());
				logLoggerChoice("due to no log name being configured");
			} else {
				this.logger = LoggerFactory.getLogger(loggerName);
				logLoggerChoice("from property \"" + RUNTIME_LOG_LOGSYSTEM_SLF4J_LOGGER_NAME + '"');
			}
		} else {
			logLoggerChoice("as provided in constructor");
		}
	}

	private void logLoggerChoice(String reason) {
		log(LogChute.DEBUG_ID, this.getClass().getName() + " initialized to use log name \"" + this.logger.getName() + "\", " + reason);
	}

	@Override
	public void log(int level, String message) {
		switch (level) {
			case LogChute.ERROR_ID:
				this.logger.error(message);
				break;
			case LogChute.WARN_ID:
				this.logger.warn(message);
				break;
			case LogChute.INFO_ID:
				this.logger.info(message);
				break;
			case LogChute.DEBUG_ID:
			default:
				this.logger.debug(message);
				break;
			case LogChute.TRACE_ID:
				this.logger.trace(message);
				break;
		}
	}

	@Override
	public void log(int level, String message, Throwable t) {
		switch (level) {
			case LogChute.ERROR_ID:
				this.logger.error(message, t);
				break;
			case LogChute.WARN_ID:
				this.logger.warn(message, t);
				break;
			case LogChute.INFO_ID:
				this.logger.info(message, t);
				break;
			case LogChute.DEBUG_ID:
			default:
				this.logger.debug(message, t);
				break;
			case LogChute.TRACE_ID:
				this.logger.trace(message, t);
				break;
		}
	}

	@Override
	public boolean isLevelEnabled(int level) {
		switch (level) {
			case LogChute.ERROR_ID:
				return this.logger.isErrorEnabled();
			case LogChute.WARN_ID:
				return this.logger.isWarnEnabled();
			case LogChute.INFO_ID:
				return this.logger.isInfoEnabled();
			case LogChute.DEBUG_ID:
			default:
				return this.logger.isDebugEnabled();
			case LogChute.TRACE_ID:
				return this.logger.isTraceEnabled();
		}
	}
}
*/