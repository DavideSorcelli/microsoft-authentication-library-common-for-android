// Copyright (c) Microsoft Corporation.
// All rights reserved.
//
// This code is licensed under the MIT License.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files(the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions :
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
package com.microsoft.identity.common.internal.eststelemetry;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.microsoft.identity.common.exception.BaseException;
import com.microsoft.identity.common.internal.cache.ISharedPreferencesFileManager;
import com.microsoft.identity.common.internal.cache.SharedPreferencesFileManager;
import com.microsoft.identity.common.internal.controllers.CommandResult;
import com.microsoft.identity.common.internal.controllers.InteractiveTokenCommand;
import com.microsoft.identity.common.internal.controllers.TokenCommand;
import com.microsoft.identity.common.internal.logging.DiagnosticContext;
import com.microsoft.identity.common.internal.logging.Logger;
import com.microsoft.identity.common.internal.result.AcquireTokenResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EstsTelemetry {
    private final static String TAG = EstsTelemetry.class.getSimpleName();

    /**
     * The name of the SharedPreferences file on disk for the last request telemetry.
     */
    private static final String LAST_REQUEST_TELEMETRY_SHARED_PREFERENCES =
            "com.microsoft.identity.client.last_request_telemetry";

    private static volatile EstsTelemetry sEstsTelemetryInstance = null;
    private IRequestTelemetryCache mLastRequestTelemetryCache;
    private Map<String, EstsTelemetryInternal> mTelemetryMap;

    private Queue<Map<String, String>> history;

    private EstsTelemetry() {
        mTelemetryMap = new ConcurrentHashMap<>();
    }

    private boolean isDisabled() {
        return mLastRequestTelemetryCache == null;
    }

    /**
     * Get an instance of {@link EstsTelemetry}. This method will return an existing
     * instance of EstsTelemetry or create and return a new instance if the existing instance is null.
     *
     * @return EstsTelemetry object instance
     */
    public static synchronized EstsTelemetry getInstance() {
        if (sEstsTelemetryInstance == null) {
            sEstsTelemetryInstance = new EstsTelemetry();
        }

        return sEstsTelemetryInstance;
    }

    public void setupLastRequestTelemetryCache(@NonNull final Context context) {
        history = new ConcurrentLinkedQueue<>();
        this.mLastRequestTelemetryCache = createLastRequestTelemetryCache(context);

        if (this.mLastRequestTelemetryCache != null) {
            com.microsoft.identity.common.internal.logging.Logger.verbose(
                    TAG,
                    "Ests Telemetry cache has been initialized properly."
            );
        }
    }

    /**
     * Emit multiple telemetry fields by passing a map of telemetry fields.
     * The fields will be saved in {@link RequestTelemetry} object associated to the current request.
     *
     * @param telemetry a map containing telemetry fields and their values
     */
    public void emit(@Nullable final Map<String, String> telemetry) {
        if (telemetry == null) {
            return;
        }

        for (Map.Entry<String, String> entry : telemetry.entrySet()) {
            emit(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Emit the provided telemetry field. The field will be saved in {@link RequestTelemetry} object
     * associated to the current request.
     *
     * @param key   the key associated to the telemetry field
     * @param value the value associated to the telemetry field
     */
    public void emit(final String key, final String value) {
        final String correlationId = DiagnosticContext.getRequestContext().get(DiagnosticContext.CORRELATION_ID);
        emit(correlationId, key, value);
    }

    private void emit(final String correlationId, final String key, final String value) {
        // Ests telemetry will be disabled if cache is not initialized
        // this should never happen in the case of MSAL
        if (isDisabled()) {
            return;
        }

        RequestTelemetry currentTelemetryInstance = getCurrentTelemetryInstance(correlationId);
        if (currentTelemetryInstance != null) {
            currentTelemetryInstance.putTelemetry(key, value);
        }
    }

    public void createEntry(@Nullable final String correlationId, final String commandType) {
        if (mTelemetryMap == null || correlationId == null || correlationId.equals("UNSET")) {
            return;
        }

        if (mTelemetryMap.containsKey(correlationId)) {
            // if it is already there don't override it
            // createEntry is only expected to be called once per command
            // calling it twice will be a bad calling pattern
            return;
        }

        // create internal object
        // which will create a context and a current request telemetry object
        EstsTelemetryContext estsTelemetryContext = new EstsTelemetryContext(correlationId, commandType);
        CurrentRequestTelemetry currentRequestTelemetry = new CurrentRequestTelemetry();
        EstsTelemetryInternal estsTelemetryInternal = new EstsTelemetryInternal(estsTelemetryContext, currentRequestTelemetry);
        mTelemetryMap.put(correlationId, estsTelemetryInternal);
    }

    @Nullable
    public EstsTelemetryInternal getCurrentEstsTelemetryInternal(@Nullable final String correlationId) {
        if (mTelemetryMap == null || correlationId == null || correlationId.equals("UNSET")) {
            return null;
        }

        return mTelemetryMap.get(correlationId);
    }

    // get will now just return the instance for the correlation id or null if not present
    // won't create a new one if one wasn't already there
    @Nullable
    private CurrentRequestTelemetry getCurrentTelemetryInstance(@Nullable final String correlationId) {
        final EstsTelemetryInternal estsTelemetryInternal = getCurrentEstsTelemetryInternal(correlationId);
        return estsTelemetryInternal != null ? estsTelemetryInternal.getCurrentRequestTelemetry() : null;
    }

    public EstsTelemetryContext getCurrentTelemetryContext(@Nullable final String correlationId) {
        final EstsTelemetryInternal estsTelemetryInternal = getCurrentEstsTelemetryInternal(correlationId);
        return estsTelemetryInternal != null ? estsTelemetryInternal.getEstsTelemetryContext() : null;
    }

    @Nullable
    private LastRequestTelemetry loadLastRequestTelemetryFromCache() {
        final String methodName = ":loadLastRequestTelemetry";

        if (mLastRequestTelemetryCache == null) {
            Logger.verbose(
                    TAG + methodName,
                    "Last Request Telemetry Cache has not been initialized. " +
                            "Cannot load Last Request Telemetry data from cache."
            );
            return null;
        }

        return (LastRequestTelemetry) mLastRequestTelemetryCache.getRequestTelemetryFromCache();
    }


    /**
     * Emit the ApiId for the current request. The field will be saved in {@link RequestTelemetry}
     * object associated to the current request.
     *
     * @param apiId the api id to emit to telemetry
     */
    public void emitApiId(final String apiId) {
        emit(Schema.Key.API_ID, apiId);
    }

    /**
     * Emit the forceRefresh value for the current request. The field will be saved in
     * {@link RequestTelemetry} object associated to the current request.
     *
     * @param forceRefresh the force refresh value to emit to telemetry
     */
    public void emitForceRefresh(final boolean forceRefresh) {
        String val = Schema.getSchemaCompliantStringFromBoolean(forceRefresh);
        emit(Schema.Key.FORCE_REFRESH, val);
    }

    private IRequestTelemetryCache createLastRequestTelemetryCache(@NonNull final Context context) {
        final String methodName = ":createLastRequestTelemetryCache";

        if (context == null) {
            Logger.verbose(
                    TAG + methodName,
                    "Context is NULL. Unable to create last request telemetry cache."
            );
            return null;
        }

        Logger.verbose(
                TAG + methodName,
                "Creating Last Request Telemetry Cache"
        );

        final ISharedPreferencesFileManager sharedPreferencesFileManager =
                new SharedPreferencesFileManager(
                        context,
                        LAST_REQUEST_TELEMETRY_SHARED_PREFERENCES
                );

        return new SharedPreferencesLastRequestTelemetryCache(sharedPreferencesFileManager);
    }

    private void setupLastFromCurrent(@NonNull CurrentRequestTelemetry currentTelemetry, @NonNull LastRequestTelemetry lastRequestTelemetry) {
        // grab whatever platform fields we can from current request
        for (Map.Entry<String, String> entry : currentTelemetry.getPlatformTelemetry().entrySet()) {
            lastRequestTelemetry.putTelemetry(entry.getKey(), entry.getValue());
        }
    }

    void flush() {
        final String correlationId = DiagnosticContext.getRequestContext().get(DiagnosticContext.CORRELATION_ID);
        flush(correlationId, (String) null);
    }

    public void flush(final String correlationId) {
        flush(correlationId, (String) null);
    }

    public void flush(final String correlationId, final BaseException baseException) {
        flush(correlationId, baseException == null ? null : baseException.getErrorCode());
    }

    public void flush(final String correlationId, final AcquireTokenResult acquireTokenResult) {
        final String errorCode = TelemetryUtils.errorFromAcquireTokenResult(acquireTokenResult);
        flush(correlationId, errorCode);
    }


    public void flush(final String correlationId, @NonNull final CommandResult commandResult) {
        String errorCode = null;

        final Object result = commandResult.getResult();

        if (result instanceof BaseException) {
            errorCode = ((BaseException) result).getErrorCode();
        }

        flush(correlationId, errorCode);
    }


    private void flush(final String correlationId, @Nullable final String errorCode) {
        final String methodName = ":flush";

        if (mTelemetryMap == null || correlationId == null) {
            return;
        }

        EstsTelemetryInternal estsTelemetryInternal = mTelemetryMap.get(correlationId);

        if (estsTelemetryInternal == null) {
            return;
        }

        CurrentRequestTelemetry currentTelemetry = estsTelemetryInternal.getCurrentRequestTelemetry();

        LastRequestTelemetry lastRequestTelemetry = loadLastRequestTelemetryFromCache();

        if (lastRequestTelemetry == null) {
            lastRequestTelemetry = new LastRequestTelemetry(currentTelemetry.mSchemaVersion);
        }

        setupLastFromCurrent(currentTelemetry, lastRequestTelemetry);

        boolean returnedTokenFromCache = estsTelemetryInternal.getEstsTelemetryContext().isResultServicedFromCache();

        if (errorCode != null) {
            lastRequestTelemetry.appendFailedRequestWithError(
                    currentTelemetry.getApiId(),
                    correlationId,
                    errorCode);
        } else {
            lastRequestTelemetry.wipeFailedRequestData();

            // must be token request for now so this should be removed
            if (isCurrentRequestTokenRequest()) {
                if (returnedTokenFromCache) {
                    lastRequestTelemetry.incrementSilentSuccessCount();
                } else {
                    lastRequestTelemetry.resetSilentSuccessCount();
                }
            }
            // else leave silent success count as is
        }

        currentTelemetry.clearTelemetry(); // is this needed?
        mTelemetryMap.remove(correlationId);

        if (mLastRequestTelemetryCache == null) {
            Logger.warn(
                    TAG + methodName,
                    "Last Request Telemetry Cache object was null. " +
                            "Unable to save request telemetry to cache."
            );
        } else if (eligibleToCache(lastRequestTelemetry)) {
            // remove old last request telemetry data from cache
            mLastRequestTelemetryCache.clearRequestTelemetry();
            // save new last request telemetry data to cache
            mLastRequestTelemetryCache.saveRequestTelemetryToCache(lastRequestTelemetry);
        }
    }


// this should NOT be needed anymore
    // if we don't have api id then we won't save telemetry to cache
    // this can happen for commands like the GetDeviceModeCommand
    // that are generated via a method for which we don't want telemetry
    private boolean eligibleToCache(RequestTelemetry lastTelemetry) {
        return !TextUtils.isEmpty(lastTelemetry.getSchemaVersion());
    }

    String getCurrentTelemetryHeaderString() {
        final String correlationId = DiagnosticContext.getRequestContext().get(DiagnosticContext.CORRELATION_ID);
        if (mTelemetryMap == null || correlationId == null) {
            return null;
        }

        RequestTelemetry currentTelemetry = getCurrentTelemetryInstance(correlationId);

        if (currentTelemetry == null) {
            return null;
        }

        return currentTelemetry.getCompleteHeaderString();
    }

    String getLastTelemetryHeaderString() {
        RequestTelemetry lastTelemetry = loadLastRequestTelemetryFromCache();

        if (lastTelemetry == null) {
            return null;
        }

        return lastTelemetry.getCompleteHeaderString();
    }

    /**
     * Get the headers for the Ests Telemetry. These headers can be attached to the requests made to the ests.
     *
     * @return a map containing telemetry headers and their values
     */
    public Map<String, String> getTelemetryHeaders() {
        final String methodName = ":getTelemetryHeaders";
        final Map<String, String> headerMap = new HashMap<>();

        if (isDisabled()) {
            // return empty header map if ests telemetry is disabled
            return headerMap;
        }

        final String currentHeader = getCurrentTelemetryHeaderString();
        final String lastHeader = getLastTelemetryHeaderString();

        if (currentHeader != null) {
            //headerMap.put(Schema.CURRENT_REQUEST_HEADER_NAME, currentHeader);
        } else {
            Logger.verbose(
                    TAG + methodName,
                    "Current Request Telemetry Header is null"
            );
        }

        if (lastHeader != null) {
            //headerMap.put(Schema.LAST_REQUEST_HEADER_NAME, lastHeader);
        } else {
            Logger.verbose(
                    TAG + methodName,
                    "Last Request Telemetry Header is null"
            );
        }

        history.add(new HashMap<String, String>() {{
            put(Schema.CURRENT_REQUEST_HEADER_NAME, currentHeader);
            put(Schema.LAST_REQUEST_HEADER_NAME, lastHeader);
        }}); // for debugging purposes only
        return headerMap;
    }

    public CurrentRequestTelemetry getCurrentRequestTelemetry() {
        final String correlationId = DiagnosticContext.getRequestContext().get(DiagnosticContext.CORRELATION_ID);
        return getCurrentTelemetryInstance(correlationId);
    }

    // probably not needed anymore
    private boolean isCurrentRequestTokenRequest() {
        final String commandType = DiagnosticContext.getRequestContext().get(DiagnosticContext.COMMAND_TYPE);
        return commandType.equals(TokenCommand.class.getSimpleName()) || commandType.equals(InteractiveTokenCommand.class.getSimpleName());
    }

    public void printHistory() {
        String msg = craftHistory();
        Logger.error("ESTS Telemetry", msg, null);
    }


    // need a real hook for test purposes
    private String craftHistory() {
        StringBuilder sb = new StringBuilder(100);

        sb.append('\n');

        for (Map<String, String> map : history) {
            sb.append("curr: " + map.get(Schema.CURRENT_REQUEST_HEADER_NAME) + " --- " + "last: " +
                    map.get(Schema.LAST_REQUEST_HEADER_NAME) + '\n');
        }

        return sb.toString();
    }

}
