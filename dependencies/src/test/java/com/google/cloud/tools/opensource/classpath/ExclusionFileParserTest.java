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

import static com.google.cloud.tools.opensource.classpath.TestHelper.absolutePathOfResource;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Test;
import org.xml.sax.SAXException;

public class ExclusionFileParserTest {

  @Test
  public void testParse_sourceClass()
      throws URISyntaxException, IOException, ParserConfigurationException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-class.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);
    boolean result =
        matcher.match(null, new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testParse_sourceMethod()
      throws URISyntaxException, IOException, ParserConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-method.xml");
    try {
      ExclusionFileParser.parse(exclusionFile);
      fail();
    } catch (SAXException ex) {
      Truth.assertThat(ex.getMessage()).startsWith("Unexpected parent-child relationship.");
    }
  }

  @Test
  public void testParse_targetField()
      throws URISyntaxException, IOException, ParserConfigurationException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-field.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new FieldSymbol("com.google.Foo", "fieldA", "Ljava.lang.String;"),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Foo"));
    boolean result =
        matchers
            .get(0)
            .match(
                symbolProblemToMatch,
                new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testParse_targetMethod()
      throws URISyntaxException, IOException, ParserConfigurationException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-method.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Foo"));
    boolean result =
        matcher.match(
            symbolProblemToMatch,
            new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testParse_sourceAndTarget_match()
      throws URISyntaxException, IOException, ParserConfigurationException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-and-target.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Foo"));
    boolean result =
        matcher.match(
            symbolProblemToMatch,
            new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testParse_sourceAndTarget_unmatch()
      throws URISyntaxException, IOException, ParserConfigurationException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-and-target.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Foo"));
    boolean result =
        matcher.match(
            symbolProblemToMatch,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Bar")); // No match
    assertFalse(result);
  }
}