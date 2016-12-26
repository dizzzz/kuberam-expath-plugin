package ro.kuberam.maven.plugins.expath.mojos;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.jar.Attributes;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.ResourceIterator;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import ro.kuberam.maven.plugins.expath.DefaultFileSet;
import ro.kuberam.maven.plugins.expath.DependencySet;
import ro.kuberam.maven.plugins.expath.DescriptorConfiguration;
import ro.kuberam.maven.plugins.mojos.KuberamAbstractMojo;
import ro.kuberam.maven.plugins.mojos.NameValuePair;

/**
 * Assembles a package. <br>
 * 
 * @author <a href="mailto:claudius.teodorescu@gmail.com">Claudius
 *         Teodorescu</a>
 * 
 */

@Mojo(name = "make-xar")
public class MakeXarMojo extends KuberamAbstractMojo {

	@Parameter(required = true)
	private File descriptor;

	@Parameter(defaultValue = "${project.build.directory}")
	private File outputDir;

	@Component(role = org.codehaus.plexus.archiver.Archiver.class, hint = "zip")
	private ZipArchiver zipArchiver;

	@Component
	private RepositorySystem repoSystem;

	private static String componentsTemplateFileContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
			+ "<package xmlns=\"http://exist-db.org/ns/expath-pkg\">${components}</package>";

	public void execute() throws MojoExecutionException, MojoFailureException {

		// test if descriptor file exists
		if (!descriptor.exists()) {
			throw new MojoExecutionException(
					"Global descriptor file '" + descriptor.getAbsolutePath() + "' does not exist.");
		}

		// set needed variables
		String outputDirectoryPath = outputDir.getAbsolutePath();
		String assemblyDescriptorName = descriptor.getName();
		String archiveTmpDirectoryPath = projectBuildDirectory + File.separator + "make-xar-tmp";
		String components = "";
		Path descriptorsDirectoryPath = Paths.get(outputDirectoryPath, "expath-descriptors-" + UUID.randomUUID());

		// Plugin xarPlugin =
		// project.getPlugin("ro.kuberam.maven.plugins:kuberam-xar-plugin");
		// DescriptorConfiguration mainConfig = new
		// DescriptorConfiguration((Xpp3Dom) xarPlugin.getConfiguration());

		// filter the descriptor file
		filterResource(descriptor.getParent(), assemblyDescriptorName, archiveTmpDirectoryPath, outputDir);
		File filteredDescriptor = Paths.get(archiveTmpDirectoryPath, assemblyDescriptorName).toFile();

		// get the execution configuration
		FileReader fileReader;
		DescriptorConfiguration executionConfig;
		try {
			fileReader = new FileReader(filteredDescriptor);
			executionConfig = new DescriptorConfiguration(Xpp3DomBuilder.build(fileReader));
		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage());
		}

		// extract settings from execution configuration
		List<DefaultFileSet> fileSets = executionConfig.getFileSets();
		List<DependencySet> dependencySets = executionConfig.getDependencySets();
		String moduleNamespace = executionConfig.getModuleNamespace();

		// set the zip archiver
		zipArchiver.setCompress(true);
		zipArchiver.setDestFile(Paths.get(outputDirectoryPath, finalName + ".xar").toFile());
		zipArchiver.setForced(true);

		// process the maven type dependencies
		for (int i = 0, il = dependencySets.size(); i < il; i++) {
			DependencySet dependencySet = dependencySets.get(i);
			String dependencySetOutputDirectory = dependencySet.outputDirectory;
			String outputFileNameMapping = dependencySet.outputFileNameMapping;

			// define the artifact
			Artifact artifactReference;
			try {
				artifactReference = new DefaultArtifact(
						dependencySet.groupId + ":" + dependencySet.artifactId + ":" + dependencySet.version);
			} catch (IllegalArgumentException e) {
				throw new MojoFailureException(e.getMessage(), e);
			}

			String artifactIdentifier = artifactReference.toString();
			getLog().debug("Resolving artifact: " + artifactReference);

			// resolve the artifact
			ArtifactRequest request = new ArtifactRequest();
			request.setArtifact(artifactReference);
			request.setRepositories(projectRepos);

			ArtifactResult artifactResult;
			try {
				artifactResult = repoSystem.resolveArtifact(repoSession, request);
			} catch (ArtifactResolutionException e) {
				throw new MojoExecutionException(e.getMessage(), e);
			}

			getLog().info("Resolved artifact: " + artifactReference);

			Artifact artifact = artifactResult.getArtifact();
			File artifactFile = artifact.getFile();
			String artifactFileAbsolutePath = artifactFile.getAbsolutePath();
			String artifactFileName = artifactFile.getName();
			if (outputFileNameMapping != null) {
				artifactFileName = outputFileNameMapping;
			}
			String archiveComponentPath = artifactFileName;
			dependencySetOutputDirectory = dependencySetOutputDirectory + artifactFileName;

			// add file to archive
			if (artifactFileAbsolutePath.endsWith(".jar")) {
				archiveComponentPath = "content/" + archiveComponentPath;
			}
			zipArchiver.addFile(artifactFile, archiveComponentPath);

			// collect metadata about module's java main class for exist.xml
			if (i == 0 && artifactIdentifier.contains(":jar:")) {
				components += "<resource><public-uri>http://exist-db.org/ns/expath-pkg/module-main-class</public-uri><file>"
						+ getMainClass(artifactFileAbsolutePath).get(0) + "</file></resource>";
				components += "<resource><public-uri>http://exist-db.org/ns/expath-pkg/module-namespace</public-uri><file>"
						+ getMainClass(artifactFileAbsolutePath).get(1) + "</file></resource>";
			}
		}

		// process the file sets
		for (DefaultFileSet fileSet : fileSets) {
			zipArchiver.addFileSet(fileSet);
		}

		// collect metadata about the archive's entries
		ResourceIterator itr = zipArchiver.getResources();
		while (itr.hasNext()) {
			ArchiveEntry entry = itr.next();
			String entryPath = entry.getName();

			// resource files
			if (entryPath.endsWith(".jar")) {
				components += "<resource><public-uri>" + moduleNamespace + "</public-uri><file>" + entryPath
						+ "</file></resource>";
			}
		}

		project.getModel().addProperty("components", components);

		// create and filter the components descriptor
		File componentsTemplateFile = Paths.get(archiveTmpDirectoryPath, "components.xml").toFile();
		try {
			FileUtils.fileWrite(componentsTemplateFile, "UTF-8", componentsTemplateFileContent);
		} catch (IOException e2) {
			e2.printStackTrace();
		}
		filterResource(archiveTmpDirectoryPath, "components.xml", descriptorsDirectoryPath.toString(), outputDir);

		// generate the expath descriptors

		NameValuePair[] parameters = new NameValuePair[] {
				new NameValuePair("package-dir", descriptorsDirectoryPath.toString()) };

		xsltTransform(filteredDescriptor,
				this.getClass().getResource("/ro/kuberam/maven/plugins/expath/generate-descriptors.xsl").toString(),
				descriptorsDirectoryPath.toString(), parameters);

		// add the expath descriptors
		// File descriptorsDirectory = descriptorsDirectoryPath.toFile();
		// try {
		// Files.list(descriptorsDirectoryPath).forEach(zipArchiver::addFile);
		// } catch (IOException e) {
		// e.printStackTrace();
		// }

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(descriptorsDirectoryPath)) {
			for (Path entry : stream) {
				zipArchiver.addFile(entry.toFile(), entry.getFileName().toString());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		//
		// for (String descriptorFileName : descriptorsDirectory.list()) {
		// zipArchiver.addFile(descriptorsDirectoryPath.resolve(descriptorFileName).toFile(),
		// descriptorFileName);
		// }

		try {
			zipArchiver.createArchive();
		} catch (ArchiverException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		project.getModel().addProperty("components", "");
	}

	private static List<String> getMainClass(String firstDependencyAbsolutePath) {
		List<String> result = new ArrayList<String>();

		URL u;
		JarURLConnection uc;
		Attributes attr = null;
		try {
			u = new URL("jar", "", "file://" + firstDependencyAbsolutePath + "!/");
			uc = (JarURLConnection) u.openConnection();
			attr = uc.getMainAttributes();
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		result.add(attr.getValue(Attributes.Name.MAIN_CLASS));
		result.add(attr.getValue("ModuleNamespace"));

		return result;
	}

}
