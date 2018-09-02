/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.php.editor.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.api.lexer.TokenUtilities;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.Error;
import org.netbeans.modules.csl.spi.GsfUtilities;
import org.netbeans.modules.csl.spi.support.CancelSupport;
import org.netbeans.modules.php.api.PhpVersion;
import org.netbeans.modules.php.editor.CodeUtils;
import org.netbeans.modules.php.editor.lexer.LexUtilities;
import org.netbeans.modules.php.editor.lexer.PHPTokenId;
import org.netbeans.modules.php.editor.parser.PHPParseResult;
import org.netbeans.modules.php.editor.parser.astnodes.ASTNode;
import org.netbeans.modules.php.editor.parser.astnodes.ClassInstanceCreation;
import org.netbeans.modules.php.editor.parser.astnodes.Expression;
import org.netbeans.modules.php.editor.parser.astnodes.FunctionInvocation;
import org.netbeans.modules.php.editor.parser.astnodes.visitors.DefaultVisitor;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

public class PHP73UnhandledError extends UnhandledErrorRule {

    @NbBundle.Messages("PHP73UnhandledError.displayName=Language feature not compatible with PHP version indicated in project settings")
    @Override
    public String getDisplayName() {
        return Bundle.PHP73UnhandledError_displayName();
    }

    @Override
    public void invoke(PHPRuleContext context, List<Error> errors) {
        PHPParseResult phpParseResult = (PHPParseResult) context.parserResult;
        if (phpParseResult.getProgram() == null) {
            return;
        }
        FileObject fileObject = phpParseResult.getSnapshot().getSource().getFileObject();
        if (fileObject != null
                && appliesTo(fileObject)) {
            if (CancelSupport.getDefault().isCancelled()) {
                return;
            }
            CheckVisitor checkVisitor = new CheckVisitor(fileObject);
            phpParseResult.getProgram().accept(checkVisitor);
            if (CancelSupport.getDefault().isCancelled()) {
                return;
            }
            errors.addAll(checkVisitor.getErrors());
        }
    }

    private static boolean appliesTo(FileObject fileObject) {
        return CodeUtils.isPhpVersionLessThan(fileObject, PhpVersion.PHP_73);
    }

    //~ Inner classes
    private static final class CheckVisitor extends DefaultVisitor {

        private final List<VerificationError> errors = new ArrayList<>();
        private final List<ASTNode> nodes = new ArrayList<>();
        private final FileObject fileObject;

        public CheckVisitor(FileObject fileObject) {
            this.fileObject = fileObject;
        }

        public Collection<VerificationError> getErrors() {
            checkFunctionCallTrailingCommas();
            return Collections.unmodifiableCollection(errors);
        }

        @Override
        public void visit(ClassInstanceCreation node) {
            if (CancelSupport.getDefault().isCancelled()) {
                return;
            }
            nodes.add(node);
            super.visit(node);
        }

        @Override
        public void visit(FunctionInvocation node) {
            if (CancelSupport.getDefault().isCancelled()) {
                return;
            }
            nodes.add(node);
            super.visit(node);
        }

        private void checkFunctionCallTrailingCommas() {
            if (!nodes.isEmpty()) {
                BaseDocument document = GsfUtilities.getDocument(fileObject, true);
                if (document == null) {
                    return;
                }
                document.readLock();
                try {
                    TokenSequence<PHPTokenId> ts = LexUtilities.getPHPTokenSequence(document, 0);
                    if (ts == null) {
                        return;
                    }
                    checkFunctionCallTrailingCommas(ts);
                } finally {
                    document.readUnlock();
                    nodes.clear();
                }
            }
        }

        private void checkFunctionCallTrailingCommas(TokenSequence<PHPTokenId> ts) {
            nodes.forEach((node) -> {
                if (CancelSupport.getDefault().isCancelled()) {
                    return;
                }

                // find a comma ","
                Token<? extends PHPTokenId> token = findPreviousToken(ts, node);
                List<Expression> parameters = Collections.emptyList();
                if (token != null
                        && token.id() == PHPTokenId.PHP_TOKEN
                        && TokenUtilities.textEquals(token.text(), ",")) { // NOI18N
                    if (node instanceof FunctionInvocation) {
                        parameters = ((FunctionInvocation) node).getParameters();
                    } else if (node instanceof ClassInstanceCreation) {
                        parameters = ((ClassInstanceCreation) node).ctorParams();
                    }
                }

                // mark the last parameter as an error
                if (!parameters.isEmpty()) {
                    createError(parameters.get(parameters.size() - 1));
                }
            });
        }

        @CheckForNull
        private Token<? extends PHPTokenId> findPreviousToken(TokenSequence<PHPTokenId> ts, ASTNode node) {
            ts.move(node.getEndOffset());
            if (!ts.movePrevious()) {
                return null;
            }
            if (TokenUtilities.textEquals(ts.token().text(), ")")) { // NOI18N
                if (!ts.movePrevious()) {
                    return null;
                }
            }
            return LexUtilities.findPrevious(ts, Arrays.asList(PHPTokenId.WHITESPACE));
        }

        private void createError(ASTNode node) {
            createError(node.getStartOffset(), node.getEndOffset());
        }

        private void createError(int startOffset, int endOffset) {
            errors.add(new PHP73VersionError(fileObject, startOffset, endOffset));
        }
    }

    private static final class PHP73VersionError extends VerificationError {

        private static final String KEY = "Php.Version.73"; // NOI18N

        private PHP73VersionError(FileObject fileObject, int startOffset, int endOffset) {
            super(fileObject, startOffset, endOffset);
        }

        @NbBundle.Messages("PHP73VersionError.displayName=Language feature not compatible with PHP version indicated in project settings")
        @Override
        public String getDisplayName() {
            return Bundle.PHP73VersionError_displayName();
        }

        @NbBundle.Messages("PHP73VersionError.description=Detected language features not compatible with PHP version indicated in project settings")
        @Override
        public String getDescription() {
            return Bundle.PHP73VersionError_description();
        }

        @Override
        public String getKey() {
            return KEY;
        }
    }
}
