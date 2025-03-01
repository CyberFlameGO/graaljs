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
package com.oracle.truffle.js.nodes.cast;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.access.IsPrimitiveNode;
import com.oracle.truffle.js.nodes.access.PropertyNode;
import com.oracle.truffle.js.nodes.function.JSFunctionCallNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.SafeInteger;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.ToDisplayStringFormat;
import com.oracle.truffle.js.runtime.builtins.JSDate;
import com.oracle.truffle.js.runtime.interop.JSInteropUtil;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.Null;
import com.oracle.truffle.js.runtime.objects.Undefined;

/**
 * Implements ToPrimitive.
 *
 * @see OrdinaryToPrimitiveNode
 */
@ImportStatic({JSConfig.class})
public abstract class JSToPrimitiveNode extends JavaScriptBaseNode {

    @Child private OrdinaryToPrimitiveNode ordinaryToPrimitiveNode;

    public enum Hint {
        None,
        String,
        Number
    }

    protected final Hint hint;

    protected JSToPrimitiveNode(Hint hint) {
        this.hint = hint;
    }

    public abstract Object execute(Object value);

    public static JSToPrimitiveNode createHintNone() {
        return create(Hint.None);
    }

    public static JSToPrimitiveNode createHintString() {
        return create(Hint.String);
    }

    public static JSToPrimitiveNode createHintNumber() {
        return create(Hint.Number);
    }

    public static JSToPrimitiveNode create(Hint hint) {
        return JSToPrimitiveNodeGen.create(hint);
    }

    @Specialization
    protected int doInt(int value) {
        return value;
    }

    @Specialization
    protected SafeInteger doSafeInteger(SafeInteger value) {
        return value;
    }

    @Specialization
    protected long doLong(long value) {
        return value;
    }

    @Specialization
    protected double doDouble(double value) {
        return value;
    }

    @Specialization
    protected boolean doBoolean(boolean value) {
        return value;
    }

    @Specialization
    protected Object doString(TruffleString value) {
        return value;
    }

    @Specialization
    protected Symbol doSymbol(Symbol value) {
        return value;
    }

    @Specialization
    protected BigInt doBigInt(BigInt value) {
        return value;
    }

    @Specialization(guards = "isJSNull(value)")
    protected JSDynamicObject doNull(@SuppressWarnings("unused") Object value) {
        return Null.instance;
    }

    @Specialization(guards = "isUndefined(value)")
    protected JSDynamicObject doUndefined(@SuppressWarnings("unused") Object value) {
        return Undefined.instance;
    }

    @Specialization
    protected Object doJSObject(JSObject object,
                    @Cached("createGetToPrimitive(object)") PropertyNode getToPrimitive,
                    @Cached("create()") IsPrimitiveNode isPrimitive,
                    @Cached("createOrdinaryToPrimitive(object)") OrdinaryToPrimitiveNode ordinaryToPrimitive,
                    @Cached("createBinaryProfile()") ConditionProfile exoticToPrimProfile,
                    @Cached("createCall()") JSFunctionCallNode callExoticToPrim) {
        Object exoticToPrim = getToPrimitive.executeWithTarget(object);
        if (exoticToPrimProfile.profile(!JSRuntime.isNullOrUndefined(exoticToPrim))) {
            Object result = callExoticToPrim.executeCall(JSArguments.createOneArg(object, exoticToPrim, getHintName()));
            if (isPrimitive.executeBoolean(result)) {
                return result;
            }
            throw Errors.createTypeError("[Symbol.toPrimitive] method returned a non-primitive object", this);
        }

        return ordinaryToPrimitive.execute(object);
    }

    private Object getHintName() {
        switch (hint) {
            case Number:
                return Strings.HINT_NUMBER;
            case String:
                return Strings.HINT_STRING;
            case None:
            default:
                return Strings.HINT_DEFAULT;
        }
    }

    protected final boolean isHintString() {
        return hint == Hint.String;
    }

    protected final boolean isHintNumber() {
        return hint == Hint.Number || hint == Hint.None;
    }

    @Specialization(guards = "isForeignObject(object)", limit = "InteropLibraryLimit")
    protected Object doForeignObject(Object object,
                    @CachedLibrary("object") InteropLibrary interop,
                    @CachedLibrary(limit = "InteropLibraryLimit") InteropLibrary resultInterop) {
        if (interop.isNull(object)) {
            return Null.instance;
        }
        try {
            if (interop.isBoolean(object)) {
                return interop.asBoolean(object);
            } else if (interop.isString(object)) {
                return interop.asTruffleString(object);
            } else if (interop.isNumber(object)) {
                if (interop.fitsInInt(object)) {
                    return interop.asInt(object);
                } else if (interop.fitsInLong(object)) {
                    return interop.asLong(object);
                } else if (interop.fitsInDouble(object)) {
                    return interop.asDouble(object);
                }
            }
        } catch (UnsupportedMessageException e) {
            throw Errors.createTypeErrorUnboxException(object, e, this);
        }
        JSRealm realm = getRealm();
        TruffleLanguage.Env env = realm.getEnv();
        if (env.isHostObject(object)) {
            if (isHintNumber() && getLanguage().getJSContext().isOptionNashornCompatibilityMode() &&
                            interop.isMemberInvocable(object, "doubleValue")) {
                try {
                    return interop.invokeMember(object, "doubleValue");
                } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                    throw Errors.createTypeErrorInteropException(object, e, "doubleValue()", this);
                }
            } else if (interop.isInstant(object)) {
                return JSDate.getDateValueFromInstant(object, interop);
            } else if (isJavaArray(object, interop)) {
                return formatJavaArray(object, interop);
            } else if (interop.isMetaObject(object)) {
                return javaClassToString(object, interop);
            } else if (interop.isException(object)) {
                return javaExceptionToString(object, interop);
            }
            // else, try OrdinaryToPrimitive (toString(), valueOf())
        }
        Object result = ordinaryToPrimitive(object);
        assert IsPrimitiveNode.getUncached().executeBoolean(result) : result;
        return JSInteropUtil.toPrimitiveOrDefault(result, result, resultInterop, this);
    }

    private static boolean isJavaArray(Object object, InteropLibrary interop) {
        return interop.hasArrayElements(object) && interop.isMemberReadable(object, "length");
    }

    @TruffleBoundary
    private static TruffleString formatJavaArray(Object object, InteropLibrary interop) {
        assert isJavaArray(object, interop);
        // toDisplayString formats host arrays similar to Arrays.toString.
        return JSRuntime.toDisplayString(object, true, ToDisplayStringFormat.getArrayFormat());
    }

    @TruffleBoundary
    private TruffleString javaClassToString(Object object, InteropLibrary interop) {
        try {
            String qualifiedName = InteropLibrary.getUncached().asString(interop.getMetaQualifiedName(object));
            if (getLanguage().getJSContext().isOptionNashornCompatibilityMode() && qualifiedName.endsWith("[]")) {
                Object hostObject = getRealm().getEnv().asHostObject(object);
                qualifiedName = ((Class<?>) hostObject).getName();
            }
            return Strings.fromJavaString("class " + qualifiedName);
        } catch (UnsupportedMessageException e) {
            throw Errors.createTypeErrorInteropException(object, e, "getTypeName", this);
        }
    }

    @TruffleBoundary
    private TruffleString javaExceptionToString(Object object, InteropLibrary interop) {
        try {
            return InteropLibrary.getUncached().asTruffleString(interop.toDisplayString(object, true));
        } catch (UnsupportedMessageException e) {
            throw Errors.createTypeErrorInteropException(object, e, "toString", this);
        }
    }

    @Fallback
    protected TruffleString doFallback(Object value) {
        assert value != null;
        throw Errors.createTypeErrorCannotConvertToPrimitiveValue(this);
    }

    private Object ordinaryToPrimitive(Object object) {
        if (ordinaryToPrimitiveNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            ordinaryToPrimitiveNode = insert(OrdinaryToPrimitiveNode.create(getLanguage().getJSContext(), isHintString() ? Hint.String : Hint.Number));
        }
        return ordinaryToPrimitiveNode.execute(object);
    }

    protected static PropertyNode createGetToPrimitive(JSDynamicObject object) {
        JSContext context = JSObject.getJSContext(object);
        return PropertyNode.createMethod(context, null, Symbol.SYMBOL_TO_PRIMITIVE);
    }

    protected OrdinaryToPrimitiveNode createOrdinaryToPrimitive(JSDynamicObject object) {
        JSContext context = JSObject.getJSContext(object);
        return OrdinaryToPrimitiveNode.create(context, isHintString() ? Hint.String : Hint.Number);
    }
}
