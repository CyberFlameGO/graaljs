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
package com.oracle.truffle.js.builtins.intl;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.builtins.JSBuiltinsContainer;
import com.oracle.truffle.js.builtins.intl.DateTimeFormatPrototypeBuiltinsFactory.JSDateTimeFormatFormatRangeNodeGen;
import com.oracle.truffle.js.builtins.intl.DateTimeFormatPrototypeBuiltinsFactory.JSDateTimeFormatFormatRangeToPartsNodeGen;
import com.oracle.truffle.js.builtins.intl.DateTimeFormatPrototypeBuiltinsFactory.JSDateTimeFormatFormatToPartsNodeGen;
import com.oracle.truffle.js.builtins.intl.DateTimeFormatPrototypeBuiltinsFactory.JSDateTimeFormatResolvedOptionsNodeGen;
import com.oracle.truffle.js.nodes.cast.JSToNumberNode;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.builtins.BuiltinEnum;
import com.oracle.truffle.js.runtime.builtins.JSDate;
import com.oracle.truffle.js.runtime.builtins.intl.JSDateTimeFormat;
import com.oracle.truffle.js.runtime.builtins.intl.JSDateTimeFormatObject;
import com.oracle.truffle.js.runtime.objects.Undefined;

public final class DateTimeFormatPrototypeBuiltins extends JSBuiltinsContainer.SwitchEnum<DateTimeFormatPrototypeBuiltins.DateTimeFormatPrototype> {

    public static final JSBuiltinsContainer BUILTINS = new DateTimeFormatPrototypeBuiltins();

    protected DateTimeFormatPrototypeBuiltins() {
        super(JSDateTimeFormat.PROTOTYPE_NAME, DateTimeFormatPrototype.class);
    }

    public enum DateTimeFormatPrototype implements BuiltinEnum<DateTimeFormatPrototype> {

        resolvedOptions(0),
        formatToParts(1),

        formatRange(2),
        formatRangeToParts(2);

        private final int length;

        DateTimeFormatPrototype(int length) {
            this.length = length;
        }

        @Override
        public int getLength() {
            return length;
        }

        @Override
        public int getECMAScriptVersion() {
            switch (this) {
                case formatToParts:
                    return JSConfig.ECMAScript2017;
                case formatRange:
                case formatRangeToParts:
                    return JSConfig.ECMAScript2021;
            }
            return BuiltinEnum.super.getECMAScriptVersion();
        }
    }

    @Override
    protected Object createNode(JSContext context, JSBuiltin builtin, boolean construct, boolean newTarget, DateTimeFormatPrototype builtinEnum) {
        switch (builtinEnum) {
            case resolvedOptions:
                return JSDateTimeFormatResolvedOptionsNodeGen.create(context, builtin, args().withThis().createArgumentNodes(context));
            case formatToParts:
                return JSDateTimeFormatFormatToPartsNodeGen.create(context, builtin, args().withThis().fixedArgs(1).createArgumentNodes(context));
            case formatRange:
                return JSDateTimeFormatFormatRangeNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
            case formatRangeToParts:
                return JSDateTimeFormatFormatRangeToPartsNodeGen.create(context, builtin, args().withThis().fixedArgs(2).createArgumentNodes(context));
        }
        return null;
    }

    public abstract static class JSDateTimeFormatResolvedOptionsNode extends JSBuiltinNode {

        public JSDateTimeFormatResolvedOptionsNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public Object doResolvedOptions(JSDateTimeFormatObject dateTimeFormat) {
            return JSDateTimeFormat.resolvedOptions(getContext(), getRealm(), dateTimeFormat);
        }

        @Fallback
        public Object throwTypeError(@SuppressWarnings("unused") Object bummer) {
            throw Errors.createTypeErrorTypeXExpected(JSDateTimeFormat.CLASS_NAME);
        }
    }

    public abstract static class JSDateTimeFormatFormatToPartsNode extends JSBuiltinNode {

        public JSDateTimeFormatFormatToPartsNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public Object doFormatToParts(JSDateTimeFormatObject dateTimeFormat, Object value) {
            return JSDateTimeFormat.formatToParts(getContext(), getRealm(), dateTimeFormat, value, null);
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object throwTypeError(Object bummer, Object value) {
            throw Errors.createTypeErrorTypeXExpected(JSDateTimeFormat.CLASS_NAME);
        }
    }

    public abstract static class JSDateTimeFormatFormatRangeNode extends JSBuiltinNode {

        public JSDateTimeFormatFormatRangeNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public TruffleString doFormatRange(JSDateTimeFormatObject dateTimeFormat, Object startDate, Object endDate,
                        @Cached JSToNumberNode startDateToNumberNode,
                        @Cached JSToNumberNode endDateToNumberNode,
                        @Cached BranchProfile errorBranch) {
            if (startDate == Undefined.instance || endDate == Undefined.instance) {
                errorBranch.enter();
                throw Errors.createTypeErrorInvalidTimeValue();
            }
            Number xNumber = startDateToNumberNode.executeNumber(startDate);
            Number yNumber = endDateToNumberNode.executeNumber(endDate);
            double x = JSDate.timeClip(JSRuntime.toDouble(xNumber));
            double y = JSDate.timeClip(JSRuntime.toDouble(yNumber));
            if (Double.isNaN(x) || Double.isNaN(y) || x > y) {
                errorBranch.enter();
                throw Errors.createRangeErrorInvalidTimeValue();
            }
            return JSDateTimeFormat.formatRange(dateTimeFormat, x, y);
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object throwTypeError(Object bummer, Object startDate, Object endDate) {
            throw Errors.createTypeErrorTypeXExpected(JSDateTimeFormat.CLASS_NAME);
        }
    }

    public abstract static class JSDateTimeFormatFormatRangeToPartsNode extends JSBuiltinNode {

        public JSDateTimeFormatFormatRangeToPartsNode(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        public Object doFormatRangeToParts(JSDateTimeFormatObject dateTimeFormat, Object startDate, Object endDate,
                        @Cached JSToNumberNode startDateToNumberNode,
                        @Cached JSToNumberNode endDateToNumberNode,
                        @Cached BranchProfile errorBranch) {
            if (startDate == Undefined.instance || endDate == Undefined.instance) {
                errorBranch.enter();
                throw Errors.createTypeErrorInvalidTimeValue();
            }
            Number xNumber = startDateToNumberNode.executeNumber(startDate);
            Number yNumber = endDateToNumberNode.executeNumber(endDate);
            double x = JSDate.timeClip(JSRuntime.toDouble(xNumber));
            double y = JSDate.timeClip(JSRuntime.toDouble(yNumber));
            if (Double.isNaN(x) || Double.isNaN(y) || x > y) {
                errorBranch.enter();
                throw Errors.createRangeErrorInvalidTimeValue();
            }
            return JSDateTimeFormat.formatRangeToParts(getContext(), getRealm(), dateTimeFormat, x, y);
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object throwTypeError(Object bummer, Object startDate, Object endDate) {
            throw Errors.createTypeErrorTypeXExpected(JSDateTimeFormat.CLASS_NAME);
        }
    }

}
