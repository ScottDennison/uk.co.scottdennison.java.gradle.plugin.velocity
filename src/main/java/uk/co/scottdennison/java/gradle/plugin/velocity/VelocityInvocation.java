package uk.co.scottdennison.java.gradle.plugin.velocity;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.work.Incremental;

import javax.inject.Inject;
import java.io.Serializable;

public class VelocityInvocation {
	private ConfigurableFileCollection filesCollection;
	private ListProperty<Serializable> templateDataProperty;

	@Inject
	public VelocityInvocation(ObjectFactory objectFactory) {
		this.filesCollection = objectFactory.fileCollection();
		this.templateDataProperty = objectFactory.listProperty(Serializable.class);
	}

	@Inject
	public VelocityInvocation(ObjectFactory objectFactory, Object[] files, Serializable[] templateData) {
		this(objectFactory);
		this.filesCollection.from(files);
		this.templateDataProperty.addAll(templateData);
	}

	@InputFiles
	@Incremental
	public FileCollection getFiles() {
		return this.filesCollection;
	}

	@Nested
	public ListProperty<Serializable> getTemplateData() {
		return this.templateDataProperty;
	}

	public void file(Object file) {
		if (file == null) {
			throw new IllegalArgumentException("file == null");
		}
		this.filesCollection.from(file);
	}

	public void files(Object... files) {
		if (files == null) {
			throw new IllegalArgumentException("files == null");
		}
		this.filesCollection.from(files);
	}

	public void templateDataEntry(Serializable serializable) {
		this.templateDataProperty.add(serializable);
	}

	public void templateDataEntry(Object object) {
		throw new IllegalArgumentException("Template data must be serializable.");
	}
}
