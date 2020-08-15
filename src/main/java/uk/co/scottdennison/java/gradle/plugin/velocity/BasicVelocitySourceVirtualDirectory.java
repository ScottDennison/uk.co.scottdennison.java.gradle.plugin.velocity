package uk.co.scottdennison.java.gradle.plugin.velocity;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

class BasicVelocitySourceVirtualDirectory implements VelocitySourceVirtualDirectory, HasPublicType {
	private final SourceDirectorySet velocity;

	BasicVelocitySourceVirtualDirectory(String parentName, String parentDisplayName, ObjectFactory objectFactory) {
		this.velocity = objectFactory.sourceDirectorySet(parentName + ".velocity", parentDisplayName + " Velocity sources");
	}

	@Override
	public SourceDirectorySet getVelocity() {
		return this.velocity;
	}

	@Override
	public TypeOf<?> getPublicType() {
		return TypeOf.typeOf(VelocitySourceVirtualDirectory.class);
	}
}
