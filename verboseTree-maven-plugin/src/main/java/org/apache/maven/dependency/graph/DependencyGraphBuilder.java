/*
 *  Copyright 2020 Google LLC.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.maven.dependency.graph;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.io.FileUtils.write;
import static org.apache.maven.dependency.graph.RepositoryUtility.CENTRAL;
import static org.apache.maven.dependency.graph.RepositoryUtility.mavenRepositoryFromUrl;

import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;

/**
 * Builds the DependencyGraph
 */
@Mojo( name = "tree", requiresDependencyResolution = ResolutionScope.TEST )
public class DependencyGraphBuilder extends AbstractMojo
{

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    protected MavenSession session;

    @Parameter
    private String outputFile;

    @Component
    private ProjectDependenciesResolver resolver;

    // replace Component with sisu guice named/inject or singleton annotation
    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    private SerializeGraph serializer;

    private DependencyNode rootNode;

    private static final RepositorySystem system = RepositoryUtility.newRepositorySystem();

    /** Maven repositories to use when resolving dependencies. */
    private final List<RemoteRepository> repositories;
    private Path localRepository;

    public DependencyGraphBuilder() {
        this( Arrays.asList( CENTRAL.getUrl() ) );
    }


    static {
        for ( Map.Entry<String, String> entry : OsProperties.detectOsProperties().entrySet() )
        {
            System.setProperty( entry.getKey(), entry.getValue() );
        }
    }

    /**
     * @param mavenRepositoryUrls remote Maven repositories to search for dependencies
     * @throws IllegalArgumentException if a URL is malformed or does not have an allowed scheme
     */
    public DependencyGraphBuilder(Iterable<String> mavenRepositoryUrls) {
        List<RemoteRepository> repositoryList = new ArrayList<RemoteRepository>();
        for (String mavenRepositoryUrl : mavenRepositoryUrls) {
            RemoteRepository repository = mavenRepositoryFromUrl(mavenRepositoryUrl);
            repositoryList.add(repository);
        }
        this.repositories = repositoryList;
    }

    @Parameter( defaultValue = "${repositorySystemSession}" )
    private RepositorySystemSession repositorySystemSession;

    /**
     * Enable temporary repositories for tests.
     */
    //@VisibleForTesting
    void setLocalRepository(Path localRepository) {
        this.localRepository = localRepository;
    }

    public void execute() throws MojoExecutionException
    {
        // ToDo: if outputFile not null write to outputFile
        File file = new File( project.getBasedir().getAbsolutePath().replace( '\\', '/' ) + "/target/tree.txt" );

        List<Artifact> artifacts = new ArrayList<Artifact>();
        Set<org.apache.maven.artifact.Artifact> artifactSet = session.getCurrentProject().getArtifacts() ;

        for( org.apache.maven.artifact.Artifact artifact : artifactSet )
        {
            Artifact newArtifact = new DefaultArtifact( artifact.getGroupId(), artifact.getArtifactId(),
                    artifact.getClassifier(), artifact.getType(), artifact.getVersion());
            newArtifact.setFile( artifact.getFile() );
            artifacts.add( newArtifact );
        }

        Model model = project.getModel();
        Dependency rootDependency = new Dependency( new DefaultArtifact( model.getGroupId(),
                model.getArtifactId(), model.getPackaging(), model.getVersion()), "" );

        rootNode = buildFullDependencyGraph( artifacts, rootDependency );
        // rootNode is given compile Scope by default but should not have a scope
        rootNode.setScope( null );

        SerializeGraph serializer = new SerializeGraph();
        String serialized = serializer.serialize( rootNode );

        try
        {
            write( file, serialized );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
            getLog().error( "Failed to write to file:" + file.getAbsolutePath() );
        }
    }

    private DependencyNode resolveCompileTimeDependencies(
            List<DependencyNode> dependencyNodes, DefaultRepositorySystemSession session, Dependency root)
            throws org.eclipse.aether.resolution.DependencyResolutionException
    {
        List<Dependency> dependencyList = new ArrayList<Dependency>();

        for (DependencyNode dependencyNode : dependencyNodes) {
            Dependency dependency = dependencyNode.getDependency();
            if (dependency == null) {
                // Root DependencyNode has null dependency field.
                dependencyList.add(new Dependency(dependencyNode.getArtifact(), "compile"));
            } else {
                // The dependency field carries exclusions
                dependencyList.add(dependency.setScope("compile"));
            }
        }

        if (localRepository != null) {
            LocalRepository local = new LocalRepository(localRepository.toAbsolutePath().toString());
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, local));
        }

        CollectRequest collectRequest = new CollectRequest();

        collectRequest.setRoot( dependencyList.get( 0 ) );
        if (dependencyList.size() != 1) {
            // With setRoot, the result includes dependencies with `optional:true` or `provided`
            // collectRequest.setRoot(dependencyList.get(0));
            collectRequest.setDependencies(dependencyList);
        } else {
            collectRequest.setDependencies(dependencyList);
        }
        for ( RemoteRepository repository : repositories) {
            collectRequest.addRepository(repository);
        }
        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest(collectRequest);
         // dependencyRequest.setRoot(  );

        // resolveDependencies equals to calling both collectDependencies (build dependency tree) and
        // resolveArtifacts (download JAR files).
        DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);
        return dependencyResult.getRoot();
    }

    /**
     * Finds the full compile time, transitive dependency graph including duplicates, conflicting
     * versions, and provided and optional dependencies. In the event of I/O errors, missing
     * artifacts, and other problems, it can return an incomplete graph. Each node's dependencies are
     * resolved recursively. The scope of a dependency does not affect the scope of its children's
     * dependencies. Provided and optional dependencies are not treated differently than any other
     * dependency.
     *
     * @param artifacts Maven artifacts whose dependencies to retrieve
     * @return dependency graph representing the tree of Maven artifacts
     */
    public DependencyNode buildFullDependencyGraph(List<Artifact> artifacts, Dependency root ) {
        List<DependencyNode> dependencyNodes = new ArrayList<DependencyNode>();
        dependencyNodes.add( new DefaultDependencyNode( root ) );

        for( Artifact artifact : artifacts )
        {
            dependencyNodes.add( new DefaultDependencyNode( artifact ) );
        }
        DefaultRepositorySystemSession session = RepositoryUtility.newSessionForFullDependency(system);
        return buildDependencyGraph(dependencyNodes, session, root);
    }

    private DependencyNode buildDependencyGraph(
            List<DependencyNode> dependencyNodes, DefaultRepositorySystemSession session, Dependency root) {

        try {
            DependencyNode node = resolveCompileTimeDependencies(dependencyNodes, session, root);
            return node;
        } catch ( org.eclipse.aether.resolution.DependencyResolutionException ex) {
            DependencyResult result = ex.getResult();
            DependencyNode graph = result.getRoot();

            for ( ArtifactResult artifactResult : result.getArtifactResults()) {
                Artifact resolvedArtifact = artifactResult.getArtifact();

                if (resolvedArtifact == null) {
                    Artifact requestedArtifact = artifactResult.getRequest().getArtifact();
                    // ToDo: graph.addUnresolvableArtifactProblem(requestedArtifact);
                }
            }

            return graph;
        }
    }


    public DependencyNode getDependencyGraph()
    {
        return rootNode;
    }


    private DependencyResolutionResult resolveDependencies( DependencyResolutionRequest request,
                                                            Collection<MavenProject> reactorProjects )
            throws DependencyResolutionException
    {
        try
        {
            return resolver.resolve( request );
        }
        catch ( DependencyResolutionException e )
        {
            if ( reactorProjects == null )
            {
                throw new DependencyResolutionException( e.getResult(), "Could not resolve following dependencies: "
                        + e.getResult().getUnresolvedDependencies(), e.getCause() );
            }

            throw new DependencyResolutionException( e.getResult(),
                    "REACTOR NOT SUPPORTED YET. Could not resolve following dependencies: "
                    + e.getResult().getUnresolvedDependencies(), e.getCause() );

            // ToDo: try collecting from reactor for multi module project
            // return collectDependenciesFromReactor( e, reactorProjects );
        }
    }

    /**
     * Gets the Maven project used by this mojo.
     *
     * @return the Maven project
     */
    public MavenProject getProject()
    {
        return project;
    }

    public RepositorySystemSession getRepositorySystemSession()
    {
        return repositorySystemSession;
    }
}
