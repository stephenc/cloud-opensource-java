/*
 * Copyright 2020 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.opensource.classpath;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;

/**
 * Diamond dependency conflict caused a {@link LinkageProblem} where the {@link LinkageProblem}'s
 * invalid reference points to the symbol in {@code pathToSelectedArtifact.getLeaf()} but a valid
 * symbol is in {@code pathToUnselectedArtifact.getLeaf()}.
 */
final class DependencyConflict extends LinkageProblemCause {
  private Symbol symbol;
  private DependencyPath pathToArtifactThruSource;
  private DependencyPath pathToSelectedArtifact;

  DependencyConflict(
      Symbol symbol,
      DependencyPath pathToSelectedArtifact,
      DependencyPath pathToArtifactThruSource) {
    this.symbol = checkNotNull(symbol);
    this.pathToArtifactThruSource = checkNotNull(pathToArtifactThruSource);
    this.pathToSelectedArtifact = checkNotNull(pathToSelectedArtifact);
  }

  /**
   * Returns the path from the root to the artifact which contains the target symbol and is used
   * when building the artifact of the source class. This artifact is the first in the breadth-first
   * traversal in the source artifact's dependency graph.
   */
  public DependencyPath getPathToArtifactThruSource() {
    return pathToArtifactThruSource;
  }

  /**
   * Returns the path from the root to the artifact which caused the linkage error and is selected
   * for the class path.
   */
  public DependencyPath getPathToSelectedArtifact() {
    return pathToSelectedArtifact;
  }

  @Override
  public String toString() {
    Artifact selected = pathToSelectedArtifact.getLeaf();
    Artifact unselected = pathToArtifactThruSource.getLeaf();
    return "Dependency conflict: "
        + Artifacts.toCoordinates(selected)
        + " does not define "
        + symbol
        + " but "
        + Artifacts.toCoordinates(unselected)
        + " defines it.\n"
        + "  selected: "
        + pathToSelectedArtifact
        + "\n  unselected: "
        + pathToArtifactThruSource;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DependencyConflict that = (DependencyConflict) other;
    return Objects.equals(symbol, that.symbol)
        && Objects.equals(pathToArtifactThruSource, that.pathToArtifactThruSource)
        && Objects.equals(pathToSelectedArtifact, that.pathToSelectedArtifact);
  }

  @Override
  public int hashCode() {
    return Objects.hash(symbol, pathToArtifactThruSource, pathToSelectedArtifact);
  }
}
