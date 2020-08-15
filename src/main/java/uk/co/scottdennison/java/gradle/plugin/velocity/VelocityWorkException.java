package uk.co.scottdennison.java.gradle.plugin.velocity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VelocityWorkException extends RuntimeException {
	private final List<VelocityActionException> causes;

	public VelocityWorkException(String message, List<VelocityActionException> causes) {
		super(message);
		for (VelocityActionException velocityActionException : causes) {
			this.addSuppressed(velocityActionException);
		}
		this.causes = new ArrayList<>(causes);
	}

	public List<VelocityActionException> getCauses() {
		return Collections.unmodifiableList(this.causes);
	}
}
