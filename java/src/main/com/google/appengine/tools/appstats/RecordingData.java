// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.tools.appstats;

import com.google.appengine.tools.appstats.StatsProtos.IndividualRpcStatsProto;
import com.google.apphosting.api.ApiStats;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * An intermediary class that holds recording-related fields that need to
 * be remembered until the protocol buffer can be completely populated.
 *
 */
class RecordingData {

  private IndividualRpcStatsProto.Builder stats;
  private String packageName;
  private String methodName;
  private boolean wasSuccessful;
  private long durationMilliseconds;
  private Long apiMcyclesOrNull;
  private byte[] response;
  private Throwable exceptionOrError;
  private long overhead;
  private ApiStats apiStats;
  private boolean isProcessed;

  RecordingData(String packageName, String methodName) {
    setPackageName(packageName);
    setMethodName(methodName);
  }

  ApiStats getApiStats() {
    return apiStats;
  }
  void setApiStats(ApiStats apiStats) {
    this.apiStats = apiStats;
  }
  IndividualRpcStatsProto.Builder getStats() {
    return stats;
  }
  void setStats(IndividualRpcStatsProto.Builder stats) {
    this.stats = stats;
  }
  String getPackageName() {
    return packageName;
  }
  void setPackageName(String packageName) {
    this.packageName = packageName;
  }
  String getMethodName() {
    return methodName;
  }
  void setMethodName(String methodName) {
    this.methodName = methodName;
  }
  boolean isWasSuccessful() {
    return wasSuccessful;
  }
  void setWasSuccessful(boolean wasSuccessful) {
    this.wasSuccessful = wasSuccessful;
  }
  long getDurationMilliseconds() {
    return durationMilliseconds;
  }
  void setDurationMilliseconds(long durationMilliseconds) {
    this.durationMilliseconds = durationMilliseconds;
  }
  Long getApiMcyclesOrNull() {
    return apiMcyclesOrNull;
  }
  void setApiMcyclesOrNull(Long apiMcyclesOrNull) {
    this.apiMcyclesOrNull = apiMcyclesOrNull;
  }
  byte[] getResponse() {
    return response;
  }
  void setResponse(byte[] response) {
    this.response = response;
  }
  Throwable getExceptionOrError() {
    return exceptionOrError;
  }
  void setExceptionOrError(Throwable exceptionOrError) {
    this.exceptionOrError = exceptionOrError;
  }
  long getOverhead() {
    return overhead;
  }
  void addOverhead(long overhead) {
    this.overhead += overhead;
  }
  boolean isProcessed() {
    return isProcessed;
  }
  void setProcessed() {
    isProcessed = true;
  }

  /**
   * Using the data stored in the intermediary, populate the fields of the
   * stats protobuf that refer to successful (or unsuccessful) rpc execution.
   * @param payloadRenderer
   */
  void storeResultData(PayloadRenderer payloadRenderer) {
    stats.setWasSuccessful(wasSuccessful);
    stats.setDurationMilliseconds(durationMilliseconds);
    if (apiMcyclesOrNull != null) {
      stats.setApiMcycles(apiMcyclesOrNull);
    }
    if (response != null) {
      stats.setResponseDataSummary(
          payloadRenderer.renderPayload(packageName, methodName, response, false));
    }
    if (exceptionOrError != null) {
      StringWriter stackTrace = new StringWriter();
      exceptionOrError.printStackTrace(new PrintWriter(stackTrace));
      stats.setResponseDataSummary(stackTrace.toString());
    }
  }

}
