/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.builtins.commonjs;

import static com.oracle.truffle.js.builtins.commonjs.CommonJSRequireBuiltin.log;
import static com.oracle.truffle.js.builtins.commonjs.CommonJSResolution.getNodeModulesPaths;
import static com.oracle.truffle.js.builtins.commonjs.CommonJSResolution.hasCoreModuleReplacement;
import static com.oracle.truffle.js.builtins.commonjs.CommonJSResolution.joinPaths;
import static com.oracle.truffle.js.builtins.commonjs.CommonJSResolution.loadAsFile;
import static com.oracle.truffle.js.builtins.commonjs.CommonJSResolution.loadIndex;
import static com.oracle.truffle.js.builtins.commonjs.CommonJSResolution.loadJsonObject;
import static com.oracle.truffle.js.lang.JavaScriptLanguage.ID;
import static com.oracle.truffle.js.lang.JavaScriptLanguage.MODULE_SOURCE_NAME_SUFFIX;
import static com.oracle.truffle.js.runtime.Strings.PACKAGE_JSON_MAIN_PROPERTY_NAME;
import static com.oracle.truffle.js.runtime.Strings.PACKAGE_JSON_MODULE_VALUE;
import static com.oracle.truffle.js.runtime.Strings.PACKAGE_JSON_TYPE_PROPERTY_NAME;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import com.oracle.js.parser.ir.Module.ModuleRequest;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSErrorType;
import com.oracle.truffle.js.runtime.JSException;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.Strings;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionObject;
import com.oracle.truffle.js.runtime.objects.DefaultESModuleLoader;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;
import com.oracle.truffle.js.runtime.objects.JSModuleData;
import com.oracle.truffle.js.runtime.objects.JSModuleRecord;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.ScriptOrModule;
import com.oracle.truffle.js.runtime.objects.Undefined;

public final class NpmCompatibleESModuleLoader extends DefaultESModuleLoader {

    public static final String PACKAGE_JSON = "package.json";

    public static NpmCompatibleESModuleLoader create(JSRealm realm) {
        return new NpmCompatibleESModuleLoader(realm);
    }

    private NpmCompatibleESModuleLoader(JSRealm realm) {
        super(realm);
    }

    /**
     * Node.js-compatible implementation of ES modules loading.
     *
     * @see <a href="https://nodejs.org/api/esm.html#esm_import_specifiers">ES Modules</a>
     * @see <a href="https://nodejs.org/api/esm.html#esm_resolver_algorithm">Resolver algorithm</a>
     *
     * @param referencingModule Referencing ES Module.
     * @param moduleRequest ES Modules Request.
     * @return ES Module record for this module.
     */
    @TruffleBoundary
    @Override
    public JSModuleRecord resolveImportedModule(ScriptOrModule referencingModule, ModuleRequest moduleRequest) {
        TruffleString specifierTS = moduleRequest.getSpecifier();
        String specifier = Strings.toJavaString(specifierTS);
        log("IMPORT resolve ", specifier);
        if (hasCoreModuleReplacement(realm.getContext(), specifier)) {
            return loadCoreModuleReplacement(referencingModule, moduleRequest);
        }
        try {
            TruffleFile file = resolveURL(referencingModule, specifier);
            if (file == null) {
                // Could not load as ESM. Try loading as Commonjs default.
                return tryLoadingAsCommonjsModule(specifierTS);
            }
            return loadModuleFromUrl(referencingModule, moduleRequest, file, file.getPath());
        } catch (IOException e) {
            log("IMPORT resolve ", specifier, " FAILED ", e.getMessage());
            throw Errors.createErrorFromException(e);
        }
    }

    private JSModuleRecord loadCoreModuleReplacement(ScriptOrModule referencingModule, ModuleRequest moduleRequest) {
        TruffleString specifier = moduleRequest.getSpecifier();
        log("IMPORT resolve built-in ", specifier);
        JSModuleRecord existingModule = moduleMap.get(specifier.toJavaStringUncached());
        if (existingModule != null) {
            log("IMPORT resolve built-in from cache ", specifier);
            return existingModule;
        }
        String moduleReplacementName = realm.getContext().getContextOptions().getCommonJSRequireBuiltins().get(specifier.toJavaStringUncached());
        Source src;
        if (moduleReplacementName != null && moduleReplacementName.endsWith(MODULE_SOURCE_NAME_SUFFIX)) {
            URI maybeUri = asURI(moduleReplacementName);
            if (maybeUri != null) {
                // Load from URI
                TruffleFile file = resolveURL(referencingModule, moduleReplacementName);
                try {
                    return loadModuleFromUrl(referencingModule, moduleRequest, file, file.getPath());
                } catch (IOException e) {
                    throw fail("Failed to load built-in ES module: " + specifier + ". " + e.getMessage());
                }
            } else {
                // Just load the module
                try {
                    String cwdOption = realm.getContext().getContextOptions().getRequireCwd();
                    TruffleFile cwd = cwdOption == null ? realm.getEnv().getCurrentWorkingDirectory() : realm.getEnv().getPublicTruffleFile(cwdOption);
                    TruffleFile modulePath = joinPaths(realm.getEnv(), cwd, moduleReplacementName);
                    src = Source.newBuilder(ID, modulePath).build();
                } catch (IOException | SecurityException e) {
                    throw fail("Failed to load built-in ES module: " + specifier + ". " + e.getMessage());
                }
            }
        } else {
            // Else, try loading as commonjs built-in module replacement
            return tryLoadingAsCommonjsModule(specifier);
        }
        JSModuleData parsedModule = realm.getContext().getEvaluator().envParseModule(realm, src);
        JSModuleRecord record = new JSModuleRecord(parsedModule, this);
        moduleMap.put(specifier.toJavaStringUncached(), record);
        return record;
    }

    private JSModuleRecord tryLoadingAsCommonjsModule(TruffleString specifier) {
        JSModuleRecord existingModule = moduleMap.get(specifier.toJavaStringUncached());
        if (existingModule != null) {
            log("IMPORT resolve built-in from cache ", specifier);
            return existingModule;
        }
        JSFunctionObject require = (JSFunctionObject) realm.getCommonJSRequireFunctionObject();
        // Any exception thrown during module loading will be propagated
        Object maybeModule = JSFunction.call(JSArguments.create(Undefined.instance, require, specifier));
        if (maybeModule == Undefined.instance || !JSDynamicObject.isJSDynamicObject(maybeModule)) {
            throw fail("Failed to load built-in ES module: " + specifier);
        }
        JSDynamicObject module = (JSDynamicObject) maybeModule;
        // Wrap any exported symbol in an ES module.
        List<TruffleString> exportedValues = JSObject.enumerableOwnNames(module);
        TruffleStringBuilder moduleBody = Strings.builderCreate();
        Strings.builderAppend(moduleBody, "const builtinModule = require('");
        Strings.builderAppend(moduleBody, specifier);
        Strings.builderAppend(moduleBody, "');\n");
        for (TruffleString s : exportedValues) {
            Strings.builderAppend(moduleBody, "export const ");
            Strings.builderAppend(moduleBody, s);
            Strings.builderAppend(moduleBody, " = builtinModule.");
            Strings.builderAppend(moduleBody, s);
            Strings.builderAppend(moduleBody, ";\n");
        }
        Strings.builderAppend(moduleBody, "export default builtinModule;");
        Source src = Source.newBuilder(ID, Strings.builderToJavaString(moduleBody), specifier + "-internal.mjs").build();
        JSModuleData parsedModule = realm.getContext().getEvaluator().envParseModule(realm, src);
        JSModuleRecord record = new JSModuleRecord(parsedModule, this);
        moduleMap.put(specifier.toJavaStringUncached(), record);
        return record;
    }

    private TruffleFile resolveURL(ScriptOrModule referencingModule, String specifier) {
        if (specifier.isEmpty()) {
            throw fail(specifier);
        }
        TruffleLanguage.Env env = realm.getEnv();
        // 1. Let resolvedURL be undefined.
        TruffleFile resolvedUrl = null;
        // 2. If specifier is a valid URL, then
        URI maybeUri = asURI(specifier);
        if (maybeUri != null) {
            try {
                resolvedUrl = env.getPublicTruffleFile(maybeUri);
            } catch (UnsupportedOperationException e) {
                throw failMessage("Only file:// urls are supported: " + e.getMessage());
            }
            // 3. Otherwise, if specifier starts with "/", then
        } else if (specifier.charAt(0) == '/') {
            resolvedUrl = env.getPublicTruffleFile(specifier);
            // 4. Otherwise, if specifier starts with "./" or "../", then
        } else if (isRelativePathFileName(specifier)) {
            // 4.1 Set resolvedURL to the URL resolution of specifier relative to parentURL.
            TruffleFile fullPath = getParentPath(referencingModule);
            if (fullPath == null) {
                throw fail(specifier);
            }
            resolvedUrl = joinPaths(env, fullPath, specifier);
            // 5. Otherwise
        } else {
            // 5.1 Note: specifier is now a bare specifier.
            // 5.2 Set resolvedURL the result of PACKAGE_RESOLVE(specifier, parentURL).
            resolvedUrl = packageResolve(specifier, referencingModule);
        }
        if (resolvedUrl == null) {
            // package was not found: will try loading as Commonjs module instead
            return null;
        }
        // 6. If resolvedURL contains any percent encodings of "/" or "\" ("%2f" and "%5C"
        // respectively), then
        if (resolvedUrl.toString().toUpperCase().contains("%2F") || resolvedUrl.toString().toUpperCase().contains("%5C")) {
            // 6.1 Throw an Invalid Module Specifier error.
            throw fail(specifier);
        }
        // 7. If resolvedURL does not end with a trailing "/" and the file at resolvedURL does not
        // exist, then
        if (!resolvedUrl.endsWith("/") && !resolvedUrl.exists()) {
            // 7.1 Throw an Invalid Module Specifier error.
            throw fail(specifier);
        }
        return resolvedUrl;
    }

    private TruffleFile getParentPath(ScriptOrModule referencingModule) {
        String refPath = referencingModule == null ? null : referencingModule.getSource().getPath();
        if (refPath == null) {
            return realm.getEnv().getPublicTruffleFile(realm.getContext().getContextOptions().getRequireCwd());
        }
        return realm.getEnv().getPublicTruffleFile(refPath).getParent();
    }

    private TruffleFile getFullPath(ScriptOrModule referencingModule) {
        String refPath = referencingModule == null ? null : referencingModule.getSource().getPath();
        if (refPath == null) {
            refPath = realm.getContext().getContextOptions().getRequireCwd();
        }
        return realm.getEnv().getPublicTruffleFile(refPath);
    }

    /**
     * PACKAGE_RESOLVE(packageSpecifier, parentURL).
     */
    private TruffleFile packageResolve(String packageSpecifier, ScriptOrModule referencingModule) {
        // 1. Let packageName be undefined.
        TruffleLanguage.Env env = realm.getEnv();
        String packageName = null;
        // 2. If packageSpecifier is an empty string, then
        if (packageSpecifier.isEmpty()) {
            // Throw an Invalid Module Specifier error.
            throw fail(packageSpecifier);
        }
        // 4. If packageSpecifier does not start with "@", then
        int packageSpecifierSeparator = packageSpecifier.indexOf('/');
        if (packageSpecifier.charAt(0) != '@') {
            // Set packageName to the substring of packageSpecifier until the first "/"
            if (packageSpecifierSeparator != -1) {
                packageName = packageSpecifier.substring(0, packageSpecifierSeparator);
            } else {
                // or the end of the string.
                packageName = packageSpecifier;
            }
        } else {
            // 5. Otherwise, if packageSpecifier does not contain a "/" separator, then
            if (packageSpecifierSeparator == -1) {
                // Throw an Invalid Module Specifier error.
                throw fail(packageSpecifier);
            }
            // Set packageName to the substring of packageSpecifier until the second "/" separator
            int secondSeparator = packageSpecifier.indexOf('/', packageSpecifierSeparator + 1);
            if (secondSeparator != -1) {
                packageName = packageSpecifier.substring(0, secondSeparator);
            } else {
                // or the end of the string.
                packageName = packageSpecifier;
            }
        }
        // 6. If packageName starts with "." or contains "\" or "%", then
        if (packageName.charAt(0) == '.' || packageName.indexOf('\\') >= 0 || packageName.indexOf('%') >= 0) {
            // Throw an Invalid Module Specifier error.
            throw fail(packageSpecifier);
        }
        // Load module using `package.json`
        TruffleFile mainPackageFolder = getFullPath(referencingModule);
        List<TruffleFile> nodeModulesPaths = getNodeModulesPaths(mainPackageFolder);

        for (TruffleFile modulePath : nodeModulesPaths) {
            TruffleFile moduleFolder = joinPaths(env, modulePath, packageSpecifier);
            TruffleFile packageJson = joinPaths(env, moduleFolder, PACKAGE_JSON);
            if (CommonJSResolution.fileExists(packageJson)) {
                JSDynamicObject jsonObj = loadJsonObject(packageJson, realm);
                if (JSDynamicObject.isJSDynamicObject(jsonObj)) {
                    Object main = JSObject.get(jsonObj, PACKAGE_JSON_MAIN_PROPERTY_NAME);
                    Object type = JSObject.get(jsonObj, PACKAGE_JSON_TYPE_PROPERTY_NAME);
                    if (type == Undefined.instance || !Strings.isTString(type) || !PACKAGE_JSON_MODULE_VALUE.equals(JSRuntime.safeToString(type))) {
                        throw failMessage("do not use import() to load non-ES modules.");
                    }
                    if (!Strings.isTString(main)) {
                        return loadIndex(env, moduleFolder);
                    }
                    TruffleFile mainPackageFile = joinPaths(env, moduleFolder, Strings.toJavaString(JSRuntime.safeToString(main)));
                    TruffleFile asFile = loadAsFile(env, mainPackageFile);
                    if (asFile != null) {
                        return asFile;
                    } else {
                        return loadIndex(env, mainPackageFile);
                    }
                }
            }
        }
        // A custom Truffle FS might still try to map a package specifier to some file.
        TruffleFile maybeFile = env.getPublicTruffleFile(packageSpecifier);
        if (maybeFile.exists()) {
            return maybeFile;
        }
        return null;
    }

    @TruffleBoundary
    private static JSException failMessage(String message) {
        return JSException.create(JSErrorType.TypeError, message);
    }

    @TruffleBoundary
    private static JSException fail(String moduleIdentifier) {
        return failMessage("Cannot load module: '" + moduleIdentifier + '\'');
    }

    private static boolean isRelativePathFileName(String moduleIdentifier) {
        return moduleIdentifier.startsWith("./") || moduleIdentifier.startsWith("../");
    }
}
