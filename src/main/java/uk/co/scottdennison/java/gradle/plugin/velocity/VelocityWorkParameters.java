package uk.co.scottdennison.java.gradle.plugin.velocity;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.model.Managed;
import org.gradle.workers.WorkParameters;

@Managed
public interface VelocityWorkParameters extends WorkParameters {
	ListProperty<SingleFileVelocityInvocation> getInvocations();
	DirectoryProperty getOutputDirectory();
}
