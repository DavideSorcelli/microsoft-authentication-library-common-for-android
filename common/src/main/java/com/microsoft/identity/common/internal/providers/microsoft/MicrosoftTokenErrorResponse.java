package com.microsoft.identity.common.internal.providers.microsoft;

import com.google.gson.annotations.SerializedName;
import com.microsoft.identity.common.internal.providers.oauth2.TokenErrorResponse;

import java.util.ArrayList;

public class MicrosoftTokenErrorResponse extends TokenErrorResponse {

    @SerializedName("error_codes")
    protected ArrayList<Long> mErrorCodes;

    @SerializedName("timestamp")
    protected String mTimeStamp;

    @SerializedName("trace_id")
    protected String mTraceId;

    @SerializedName("correlation_id")
    protected String mCorrelationId;


    public ArrayList<Long> getErrorCodes() {
        return mErrorCodes;
    }

    public void setErrorCodes(ArrayList<Long> errorCodes) {
        this.mErrorCodes = errorCodes;
    }

    public String getTimeStamp() {
        return mTimeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.mTimeStamp = timeStamp;
    }

    public String getTraceId() {
        return mTraceId;
    }

    public void setTraceId(String traceId) {
        this.mTraceId = traceId;
    }

    public String getCorrelationId() {
        return mCorrelationId;
    }

    public void setCorrelationId(String correlationId) {
        this.mCorrelationId = correlationId;
    }
}
