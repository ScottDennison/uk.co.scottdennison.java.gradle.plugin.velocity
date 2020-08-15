package uk.co.scottdennison.java.gradle.plugin.velocity;

import java.io.File;
import java.io.Serializable;
import java.util.Set;

public class SingleFileVelocityInvocation implements Serializable {
	private static final long serialVersionUID = 473318731182809607L;

	private final File file;
	private final Set<Serializable> templateData;

	public SingleFileVelocityInvocation(File file, Set<Serializable> templateData) {
		this.file = file;
		this.templateData = templateData;
	}

	public File getFile() {
		return file;
	}

	public Set<Serializable> getTemplateData() {
		return templateData;
	}
}
