/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.parser.env;

import java.util.Map;
import java.util.StringJoiner;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JSFrameSlot;
import com.oracle.truffle.js.nodes.NodeFactory;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.Strings;

public final class GlobalEnvironment extends DerivedEnvironment {
    /** Entries: (name, const). */
    private final EconomicMap<TruffleString, Boolean> lexicalDeclarations;
    private final EconomicMap<TruffleString, Boolean> varDeclarations;

    public GlobalEnvironment(Environment parent, NodeFactory factory, JSContext context) {
        super(parent, factory, context);
        this.lexicalDeclarations = EconomicMap.create();
        this.varDeclarations = EconomicMap.create();
    }

    @Override
    public JSFrameSlot findBlockFrameSlot(Object name) {
        return null;
    }

    public boolean addLexicalDeclaration(TruffleString name, boolean isConst) {
        return lexicalDeclarations.put(name, isConst) == null;
    }

    public boolean hasLexicalDeclaration(TruffleString name) {
        return lexicalDeclarations.containsKey(name);
    }

    public boolean hasConstDeclaration(TruffleString name) {
        return lexicalDeclarations.get(name, Boolean.FALSE);
    }

    public boolean addVarDeclaration(TruffleString name) {
        return varDeclarations.put(name, Boolean.FALSE) == null;
    }

    public boolean hasVarDeclaration(TruffleString name) {
        return varDeclarations.containsKey(name);
    }

    /**
     * Returns true for always-defined immutable value properties of the global object.
     */
    public static boolean isGlobalObjectConstant(TruffleString name) {
        return Strings.UNDEFINED.equals(name) || Strings.NAN.equals(name) || Strings.INFINITY.equals(name);
    }

    @Override
    protected String toStringImpl(Map<String, Integer> state) {
        return "Global" + new StringJoiner(", ", "{", "}").add(joinElements(lexicalDeclarations.getKeys())).add(joinElements(varDeclarations.getKeys())).toString();
    }
}
