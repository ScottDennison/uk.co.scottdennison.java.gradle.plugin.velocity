package uk.co.scottdennison.java.gradle.plugin.velocity;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.TemplateInitException;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.parser.ParseException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.workers.WorkAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.SourceVersion;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class VelocityWorkAction implements WorkAction<VelocityWorkParameters>/*, AdapterWorkParameters*/ {
	private final Logger LOGGER = LoggerFactory.getLogger(VelocityWorkAction.class);

	@Override
	public void execute() {
		VelocityWorkParameters workParameters = getParameters();
		File outputDirectory = workParameters.getOutputDirectory().get().getAsFile();

		RuntimeServices runtimeServices = new RuntimeInstance();
		runtimeServices.setProperty(RuntimeConstants.RUNTIME_REFERENCES_STRICT,true);
		runtimeServices.init();

		List<VelocityActionException> velocityActionExceptions = new ArrayList<>();
		Set<String> knownQualifiedClassNames = new HashSet<>();

		for (SingleFileVelocityInvocation singleFileVelocityInvocation : workParameters.getInvocations().get()) {
			Template template = new Template();
			template.setRuntimeServices(runtimeServices);
			File file = singleFileVelocityInvocation.getFile();
			LOGGER.debug("Processing template file {}",file);
			try (
				FileInputStream fileInputStream = new FileInputStream(file);
				InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
				BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			) {
				template.setData(runtimeServices.parse(bufferedReader, template));
				template.initDocument();
			} catch (IOException ex) {
				velocityActionExceptions.add(new VelocityActionException("Could not read template file \"" + file.getPath() + "\".", ex));
				continue;
			} catch (ParseException | VelocityException ex) {
				velocityActionExceptions.add(new VelocityActionException("Could not parse template file \"" + file.getPath() + "\".", ex));
				continue;
			}

			for (Object templateData : singleFileVelocityInvocation.getTemplateData()) {
				LOGGER.debug("Processing template data {} for template file {}",templateData,file);
				VelocityContext velocityContext = new VelocityContext();
				velocityContext.put("root", templateData);

				String generatedJavaCode;
				try (
					StringWriter generatedJavaCodeWriter = new StringWriter();
				) {
					template.merge(velocityContext, generatedJavaCodeWriter);
					generatedJavaCode = generatedJavaCodeWriter.toString().trim()+"\n";
				} catch (IOException | VelocityException ex) {
					velocityActionExceptions.add(new VelocityActionException("Could not process template file \"" + file.getPath() + "\" when using template data " + templateData.toString(), ex));
					continue;
				}

				String packageName;
				String className;
				try {
					packageName = VelocityWorkAction.getJavaNameFromContext(velocityContext, "packageName", JavaNameType.PACKAGE);
					className = VelocityWorkAction.getJavaNameFromContext(velocityContext, "className", JavaNameType.CLASS);
				} catch (VelocityVariableException ex) {
					velocityActionExceptions.add(new VelocityActionException("Could not read required values from velocity context for template file \"" + file.getPath() + "\" when using template data " + templateData.toString() + ". Did the script set them?",ex));
					continue;
				}

				String qualifiedClassName;
				if (packageName.isEmpty()) {
					qualifiedClassName = className;
				} else {
					qualifiedClassName = packageName + "." + className;
				}

				if (!knownQualifiedClassNames.add(qualifiedClassName)) {
					velocityActionExceptions.add(new VelocityActionException("Qualified class name " + qualifiedClassName + " has already been seen during this round of velocity processing."));
					continue;
				}

				try {
					File outputFile = new File(outputDirectory,qualifiedClassName.replace('.', '/') + ".java");
					Files.createDirectories(outputFile.toPath().getParent());
					try (
						FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
						OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
						BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
					) {
						bufferedWriter.write(generatedJavaCode);
					}
				} catch (IOException ex) {
					velocityActionExceptions.add(new VelocityActionException("Unable to write qualified class " + qualifiedClassName,ex));
					continue;
				}
			}
		}

		VelocityWorkException velocityWorkException;

		int velocityActionExceptionCount = velocityActionExceptions.size();
		switch (velocityActionExceptionCount) {
			case 0:
				velocityWorkException = null;
				break;
			case 1:
				velocityWorkException = new VelocityWorkException("An exception occurred while processing velocity templates",velocityActionExceptions);
				break;
			default:
				velocityWorkException = new VelocityWorkException(velocityActionExceptionCount + " exceptions occurred while processing velocity templates",velocityActionExceptions);
				break;
		}

		if (velocityWorkException == null) {
			LOGGER.debug("Process succeeded");
		} else {
			LOGGER.debug("Process failed", velocityWorkException);
			throw velocityWorkException;
		}
	}

	private enum JavaNameType {
		PACKAGE("Java package name") {
			@Override
			Optional<String> validateValue(String value) {
				if (value.isEmpty()) {
					return Optional.empty();
				}
				String[] parts = value.split("\\.", -1);
				int partCount = parts.length;
				for (int partIndex=0; partIndex<partCount; partIndex++) {
					String part = parts[partIndex];
					String problemMessageSegment;
					if (part.isEmpty()) {
						problemMessageSegment = "is empty";
					} else if (!part.trim().equals(part)) {
						problemMessageSegment = "is surrounded by whitespace";
					} else if (!SourceVersion.isIdentifier(part)) {
						problemMessageSegment = "is not a valid java identifier";
					} else if (SourceVersion.isKeyword(part)) {
						problemMessageSegment = "is not reserved java keyword";
					} else {
						problemMessageSegment = null;
					}
					if (problemMessageSegment != null) {
						return Optional.of("package name part " + (partIndex+1) + " " + problemMessageSegment);
					}
				}
				return Optional.empty();
			}
		},
		CLASS("Java class name") {
			@Override
			Optional<String> validateValue(String value) {
				if (value.isEmpty()) {
					return Optional.of("class name cannot be empty");
				} else if (value.indexOf('.') >= 0) {
					return Optional.of("class names cannot contain a dot");
				} else if (!SourceVersion.isIdentifier(value)) {
					return Optional.of("value is not a valid java identifier");
				} else if (SourceVersion.isKeyword(value)) {
					return Optional.of("value is a reserved java keyword");
				} else {
					return Optional.empty();
				}
			}
		};

		private final String description;

		JavaNameType(String description) {
			this.description = description;
		}

		public String getDescription() {
			return this.description;
		}

		abstract Optional<String> validateValue(String value);
	}

	private static String getJavaNameFromContext(Context context, String key, JavaNameType javaNameType) throws VelocityVariableException {
		if (context.containsKey(key)) {
			Object rawValue = context.get(key);
			if (rawValue == null) {
				throw new VelocityVariableException("The value for context key \"" + key + "\" is null");
			} else if (!(rawValue instanceof String)) {
				throw new VelocityVariableException("The value for context key \"" + key + "\" is not a string");
			} else {
				String stringValue = ((String)rawValue).trim();
				Optional<String> optionalNamingProblem = javaNameType.validateValue(stringValue);
				if (optionalNamingProblem.isPresent()) {
					throw new VelocityVariableException("The value for context key \"" + key + "\" is not a valid " + javaNameType.getDescription() + ": " + optionalNamingProblem.get());
				}
				return stringValue;
			}
		} else {
			throw new VelocityVariableException("Context does not contain a key called \"" + key + "\"");
		}
	}
}
