package uk.co.scottdennison.java.gradle.plugin.velocity;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.util.ConfigureUtil;

public interface VelocitySourceVirtualDirectory {
	public static final String NAME = "velocity";

	SourceDirectorySet getVelocity();

	default VelocitySourceVirtualDirectory velocity(Closure<?> configureClosure) {
		ConfigureUtil.configure(configureClosure, this.getVelocity());
		return this;
	}

	default VelocitySourceVirtualDirectory velocity(Action<? super SourceDirectorySet> configureAction) {
		configureAction.execute(this.getVelocity());
		return this;
	}
}
