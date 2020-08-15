package uk.co.scottdennison.java.gradle.plugin.velocity;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.classloader.ClassLoaderVisitor;
import org.gradle.util.ConfigureUtil;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutionException;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VelocityTask extends DefaultTask implements HasPublicType {
	private final WorkerExecutor workerExecutor;
	private final DirectoryProperty outputDirectoryProperty;
	private final ObjectFactory objectFactory;
	private final ListProperty<VelocityInvocation> invocationsProperty;
	private final Property<Boolean> useForcedDataClasspathProperty;

	private FileCollection velocityClasspath;

	@Inject
	public VelocityTask(WorkerExecutor workerExecutor) {
		this.objectFactory = getProject().getObjects();
		this.workerExecutor = workerExecutor;
		this.invocationsProperty = this.objectFactory.listProperty(VelocityInvocation.class);
		this.outputDirectoryProperty = this.objectFactory.directoryProperty();
		this.useForcedDataClasspathProperty = this.objectFactory.property(Boolean.class).convention(Boolean.FALSE);
	}

	@Internal
	public Property<Boolean> getUseForcedDataClasspath() {
		return this.useForcedDataClasspathProperty;
	}

	@Nested
	public ListProperty<VelocityInvocation> getInvocations() {
		return this.invocationsProperty;
	}

	public void invocation(Closure<VelocityInvocation> configureClosure) {
		VelocityInvocation velocityInvocation = new VelocityInvocation(this.objectFactory);
		ConfigureUtil.configure(configureClosure, velocityInvocation);
		this.invocationsProperty.add(velocityInvocation);
	}

	public void invocation(Action<VelocityInvocation> action) {
		VelocityInvocation velocityInvocation = new VelocityInvocation(this.objectFactory);
		action.execute(velocityInvocation);
		this.invocationsProperty.add(velocityInvocation);
	}

	@OutputDirectory
	public DirectoryProperty getOutputDirectory() {
		return outputDirectoryProperty;
	}

	@Classpath
	public FileCollection getVelocityClasspath() {
		return this.velocityClasspath;
	}

	public void setVelocityClasspath(FileCollection velocityClasspath) {
		this.velocityClasspath = velocityClasspath;
	}

	@TaskAction
	protected void run(InputChanges inputChanges) {
		List<VelocityInvocation> invocations = this.invocationsProperty.get();
		Map<File, Set<Serializable>> calculatedTemplateDataPerFile = new HashMap<>();
		boolean useForcedDataClasspath = Boolean.TRUE.equals(useForcedDataClasspathProperty.get());
		Set<File> dataClassFiles = new HashSet<>();
		ClassLoader baseClassLoader = this.getClass().getClassLoader();

		for (VelocityInvocation velocityInvocation : invocations) {
			List<Serializable> templateData = velocityInvocation.getTemplateData().get();
			if (useForcedDataClasspath) {
				for (Serializable templateDataEntry : templateData) {
					ClassLoader templateDataClassLoader = templateDataEntry.getClass().getClassLoader();
					new ClassLoaderVisitor(baseClassLoader) {
						public void visitClassPath(URL[] urls) {
							for (URL url : urls) {
								try {
									URI uri = url.toURI();
									if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
										dataClassFiles.add(new File(uri.getPath()));
									}
								} catch (URISyntaxException ex) {
									// Do nothing, bypass this URL.
								}
							}
						}
					}.visit(templateDataClassLoader);
				}
			}
			for (FileChange fileChange : inputChanges.getFileChanges(velocityInvocation.getFiles())) {
				switch (fileChange.getChangeType()) {
					case ADDED:
					case MODIFIED:
						List<File> files;
						File baseFile = fileChange.getFile();
						switch (fileChange.getFileType()) {
							case FILE:
								files = Collections.singletonList(baseFile);
								break;
							case DIRECTORY:
								//files = Files.newDirectoryStream(baseFile.toPath()).
								files = new ArrayList<>();
								try {
									Files.walkFileTree(
										baseFile.toPath(),
										new FileVisitor<Path>() {
											@Override
											public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
												return FileVisitResult.CONTINUE;
											}

											@Override
											public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
												files.add(file.toFile());
												return FileVisitResult.CONTINUE;
											}

											@Override
											public FileVisitResult visitFileFailed(Path file, IOException ex) throws IOException {
												throw ex;
											}

											@Override
											public FileVisitResult postVisitDirectory(Path dir, IOException ex) throws IOException {
												throw ex;
											}
										}
									);
								} catch (IOException ex) {
									throw new IllegalStateException("Could not walk file tree", ex);
								}
								break;
							default:
								files = Collections.emptyList();
						}
						for (File file : files) {
							calculatedTemplateDataPerFile.computeIfAbsent(file, k -> new HashSet<>()).addAll(templateData);
						}
					break;
				}
			}
		}

		if (!calculatedTemplateDataPerFile.isEmpty()) {
			WorkQueue workQueue;
			if (this.velocityClasspath == null && !useForcedDataClasspath) {
				workQueue = this.workerExecutor.noIsolation();
			}
			else {
				workQueue = this.workerExecutor.classLoaderIsolation(
					classLoaderWorkerSpec -> {
						classLoaderWorkerSpec.getClasspath().from(this.velocityClasspath).from(dataClassFiles);
					}
				);
			}

			try {
				workQueue.submit(
					VelocityWorkAction.class,
					parameters -> {
						parameters.getInvocations().set(calculatedTemplateDataPerFile.entrySet().stream().map(entry -> new SingleFileVelocityInvocation(entry.getKey(), entry.getValue())).collect(Collectors.toList()));
						parameters.getOutputDirectory().set(this.outputDirectoryProperty.getLocationOnly().get().getAsFile());
					}
				);
				workQueue.await();
			} catch (WorkerExecutionException ex) {
				if (!useForcedDataClasspath) {
					Throwable currentThrowable = ex;
					while (currentThrowable != null) {
						if (currentThrowable.getClass().getSimpleName().equals("ValueSnapshottingException")) {
							Throwable parentThrowable = currentThrowable.getCause();
							if (parentThrowable instanceof ClassNotFoundException) {
								throw new IllegalStateException("Unable to run task. Probably cause is class loader isolation preventing access to the template data classes. Try setting useForcedDataClasspath to true", ex);
							}
						}
						currentThrowable = currentThrowable.getCause();
					}
				}
				throw ex;
			}
		}
	}

	@Override
	@Internal
	public TypeOf<?> getPublicType() {
		return TypeOf.typeOf(VelocityTask.class);
	}

	public void hi() {
		System.out.println("hi");
	}
}
