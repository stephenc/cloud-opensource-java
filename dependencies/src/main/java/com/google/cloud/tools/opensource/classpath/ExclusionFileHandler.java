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

import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Handler for Linkage Checker exclusion XML files.
 *
 * <p>An instance of this class can process one document and cannot be reused for other documents.
 */
class ExclusionFileHandler extends DefaultHandler {

  /**
   * Stack to hold the ancestors of the XML element in {@link #startElement(String, String, String,
   * Attributes)}.
   */
  private ArrayDeque<SymbolProblemMatcher> stack = new ArrayDeque<>();

  private ImmutableList.Builder<LinkageErrorMatcher> matchers;

  ImmutableList<LinkageErrorMatcher> getMatchers() {
    return matchers.build();
  }

  @Override
  public void startDocument() {
    if (matchers != null) {
      throw new IllegalStateException("This handler started reading document already");
    }
    matchers = ImmutableList.builder();
  }

  /** Adds {@code child} to the parent matcher that is at the top of the {@link #stack}. */
  private void addMatcherToParent(SymbolProblemTargetMatcher child) throws SAXException {
    SymbolProblemMatcher parent = stack.peek();
    if (parent instanceof SourceMatcher && child instanceof SymbolProblemSourceMatcher) {
      ((SourceMatcher) parent).setMatcher((SymbolProblemSourceMatcher) child);
    } else if (parent instanceof TargetMatcher) {
      ((TargetMatcher) parent).setMatcher(child);
    } else {
      throw new SAXException(
          "Unexpected parent-child relationship. Parent:" + parent + ", child:" + child);
    }
  }

  @Override
  public void startElement(
      String namespaceUri, String localName, String qualifiedName, Attributes attributes)
      throws SAXException {
    if (!namespaceUri.isEmpty()) {
      throw new SAXException(
          "unrecognized element: " + qualifiedName + " in namespace " + namespaceUri);
    }
    switch (localName) {
      case "LinkageCheckerFilter":
        break;
      case "LinkageError":
        stack.push(new LinkageErrorMatcher());
        break;
      case "Source":
        SourceMatcher sourceMatcher = new SourceMatcher();
        ((LinkageErrorMatcher) stack.peek()).setSourceMatcher(sourceMatcher);
        stack.push(sourceMatcher);
        break;
      case "Target":
        TargetMatcher targetMatcher = new TargetMatcher();
        ((LinkageErrorMatcher) stack.peek()).setTargetMatcher(targetMatcher);
        stack.push(targetMatcher);
        break;
      case "Package":
        PackageMatcher packageMatcher = new PackageMatcher(attributes.getValue("name"));
        addMatcherToParent(packageMatcher);
        break;
      case "Class":
        String classNameOnClass = attributes.getValue("name");
        ClassMatcher classMatcher = new ClassMatcher(classNameOnClass);
        addMatcherToParent(classMatcher);
        break;
      case "Method":
        String classNameOnMethod = attributes.getValue("className");
        MethodMatcher methodMatcher =
            new MethodMatcher(classNameOnMethod, attributes.getValue("name"));
        addMatcherToParent(methodMatcher);
        break;
      case "Field":
        String classNameOnField = attributes.getValue("className");
        FieldMatcher fieldMatcher = new FieldMatcher(classNameOnField, attributes.getValue("name"));
        addMatcherToParent(fieldMatcher);
        break;
      default:
        throw new SAXException("Unknown tag " + localName);
    }
  }

  @Override
  public void endElement(String uri, String localName, String qualifiedName) throws SAXException {
    SymbolProblemMatcher poppedMatcher;
    if (!uri.isEmpty()) {
      throw new SAXException("unrecognized element: " + qualifiedName + " in namespace " + uri);
    }
    switch (localName) {
      case "Source":
      case "Target":
        poppedMatcher = stack.pop();
        if (!(poppedMatcher instanceof TargetMatcher)
            && !(poppedMatcher instanceof SourceMatcher)) {
          throw new SAXException("Unexpected matcher in stack");
        }
        break;
      case "LinkageError":
        poppedMatcher = stack.pop();
        if (!(poppedMatcher instanceof LinkageErrorMatcher)) {
          throw new SAXException("Unexpected stack status after reading LinkageError element");
        }
        matchers.add((LinkageErrorMatcher) poppedMatcher);
    }
  }

  @Override
  public void endDocument() throws SAXException {
    if (stack.size() != 0) {
      throw new SAXException("Unexpected elements in stack");
    }
  }
}