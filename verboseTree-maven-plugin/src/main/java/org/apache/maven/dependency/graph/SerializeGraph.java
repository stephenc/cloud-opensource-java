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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class SerializeGraph
{
    // will be injected eventually
    private String outputType;

    private Map<DependencyNode, Boolean> visitedNodes;
    private Set<String> artifactSet;

    public String serialize( DependencyNode root )
    {
        visitedNodes = new IdentityHashMap<DependencyNode, Boolean>( 512 );
        artifactSet = new HashSet<String>();
        StringBuilder builder = new StringBuilder();
        return dfs( root, builder, 0, "" ).toString();
    }

    private StringBuilder appendDependency( StringBuilder builder, DependencyNode node )
    {
        String scope = node.getDependency().getScope();
        builder.append( getArtifactString( node.getArtifact() ) );

        if ( scope != null && !scope.isEmpty() )
        {
            builder.append( ":" + scope );
        }
        return builder;
    }

    private String getArtifactString( Artifact artifact )
    {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" +
                artifact.getExtension() + ":" + artifact.getVersion();
    }

    private boolean artifactDuplicate( Artifact artifact )
    {
        String artifactString = getArtifactString( artifact );
        if( artifactSet.contains( artifactString ) ) return true;
        return false;
    }

    public StringBuilder dfs( DependencyNode node, StringBuilder builder, int level, String start )
    {
        builder.append( start );
        builder = appendDependency( builder, node );


        if ( visitedNodes.containsKey( node ) )
        {
            builder.append( " (Omitted due to cycle)\n" );
            // System.lineSeparator());
        }
        else if ( artifactDuplicate( node.getArtifact() ) )
        {
            builder.append( " (Omitted due to duplicate artifact)\n" );
        }
        else
        {
            artifactSet.add( getArtifactString( node.getArtifact() ) );
            builder.append( "\n" );
            // System.lineSeparator());
            visitedNodes.put( node, true );

            for ( int i = 0; i < node.getChildren().size(); ++i )
            {
                if ( start.endsWith( "+- " ) )
                {
                    start = start.replace( "+- ", "|  " );
                }
                else if ( start.endsWith( "\\- " ) )
                {
                    start = start.replace( "\\- ", "   " );
                }

                if ( i == node.getChildren().size() - 1 )
                {
                    builder = dfs( node.getChildren().get( i ), builder, level + 1, start.concat( "\\- " ) );
                }
                else
                {
                    builder = dfs( node.getChildren().get( i ), builder, level + 1, start.concat( "+- " ) );
                }
            }
        }
        return builder;
    }
}