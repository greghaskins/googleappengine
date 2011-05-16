// Copyright 2010 Google Inc. All rights reserved.
package com.google.appengine.api.taskqueue;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.taskqueue.TaskOptions.Param;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest.Header;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueBulkAddResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueDeleteRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueDeleteResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueMode;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueuePurgeQueueRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueuePurgeQueueResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueQueryAndOwnTasksRequest;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueQueryAndOwnTasksResponse;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueRetryParameters;
import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueServiceError;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Implements the {@link Queue} interface.
 * {@link QueueImpl} is thread safe.
 *
 */
class QueueImpl implements Queue {
  private final String queueName;
  private final DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
  final QueueApiHelper apiHelper;

  /**
   * The name of the HTTP header specifying the default namespace
   * for API calls.
   */
  static final String DEFAULT_NAMESPACE_HEADER = "X-AppEngine-Default-Namespace";
  static final String CURRENT_NAMESPACE_HEADER = "X-AppEngine-Current-Namespace";

  QueueImpl(String queueName, QueueApiHelper apiHelper) {
    QueueApiHelper.validateQueueName(queueName);

    this.apiHelper = apiHelper;
    this.queueName = queueName;
  }

  /**
   * See {@link Queue#add()}
   */
  public TaskHandle add() {
    return add(
        getDatastoreService().getCurrentTransaction(null), TaskOptions.Builder.withDefaults());
  }

  /**
   * Returns a {@link URI} validated to only contain legal components.
   * <p>The "scheme", "authority" and "fragment" components of a URI
   * must not be specified.  The path component must be absolute
   * (i.e. start with "/").
   *
   * @param urlString The "url" specified by the client.
   * @throws IllegalArgumentException The provided urlString is null, too long or does not have
   *         correct syntax.
   */
  private URI parsePartialUrl(String urlString) {
    if (urlString == null) {
      throw new IllegalArgumentException("url must not be null");
    }

    if (urlString.length() > QueueConstants.maxUrlLength()) {
      throw new IllegalArgumentException(
          "url is longer than " + ((Integer) QueueConstants.maxUrlLength()).toString() + ".");
    }

    URI uri;
    try {
      uri = new URI(urlString);
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("URL syntax error", exception);
    }

    uriCheckNull(uri.getScheme(), "scheme");
    uriCheckNull(uri.getRawAuthority(), "authority");
    uriCheckNull(uri.getRawFragment(), "fragment");
    String path = uri.getPath();

    if (path == null || path.length() == 0 || path.charAt(0) != '/') {
      if (path == null) {
        path = "(null)";
      } else if (path.length() == 0) {
        path = "<empty string>";
      }
      throw new IllegalArgumentException(
          "url must contain a path starting with '/' part - contains :" + path);
    }

    return uri;
  }

  private void uriCheckNull(String value, String valueName) {
    if (value != null) {
      throw new IllegalArgumentException(
          "url must not contain a '" + valueName + "' part - contains :" + value);
    }
  }

  private void checkPullTask(String url,
      HashMap<String, List<String>> headers,
      byte[] payload,
      RetryOptions retryOptions) {
    if (url != null && !url.isEmpty()) {
      throw new IllegalArgumentException("May not specify url in tasks that have method PULL");
    }
    if (!headers.isEmpty()) {
      throw new IllegalArgumentException(
          "May not specify any header in tasks that have method PULL");
    }
    if (retryOptions != null) {
      throw new IllegalArgumentException(
          "May not specify retry options in tasks that have method PULL");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload must be specified for tasks with method PULL");
    }
  }

  private void checkPostTask(List<Param> params, byte[] payload, String query) {
    if (query != null && query.length() != 0) {
      throw new IllegalArgumentException(
          "POST method may not have a query string; use setParamater(s) instead");
    }
  }

  /**
   * Construct a byte array data from params if payload is not specified.
   * If it sees payload is specified, return null.
   * @throws IllegalArgumentException if params and payload both exist
   */
  private byte[] constructPayloadFromParams(List<Param> params, byte[] payload) {
    if (!params.isEmpty() && payload != null) {
      throw new IllegalArgumentException(
          "Message body and parameters may not both be present; "
          + "only one of these may be supplied");
    }
    return payload != null ? null : encodeParamsPost(params);

  }

  private void validateAndFillAddRequest(com.google.appengine.api.datastore.Transaction txn,
      TaskOptions taskOptions,
      TaskQueueAddRequest addRequest) {
    boolean useUrlEncodedContentType = false;

    HashMap<String, List<String>> headers = taskOptions.getHeaders();
    String url = taskOptions.getUrl();
    byte[] payload = taskOptions.getPayload();
    List<Param> params = taskOptions.getParams();
    RetryOptions retryOptions = taskOptions.getRetryOptions();
    TaskOptions.Method method = taskOptions.getMethod();

    URI parsedUrl;
    if (url == null) {
      parsedUrl = parsePartialUrl(defaultUrl());
    } else {
      parsedUrl = parsePartialUrl(url);
    }
    String query = parsedUrl.getQuery();
    StringBuilder relativeUrl = new StringBuilder(parsedUrl.getRawPath());
    if (query != null && query.length() != 0 && !params.isEmpty()) {
      throw new IllegalArgumentException(
          "Query string and parameters both present; only one of these may be supplied");
    }

    byte[] constructedPayload;
    if (method == TaskOptions.Method.PULL) {
      constructedPayload = constructPayloadFromParams(params, payload);
      if (constructedPayload != null) {
        payload = constructedPayload;
      }
      checkPullTask(url, headers, payload, retryOptions);
    } else if (method == TaskOptions.Method.POST) {
      constructedPayload = constructPayloadFromParams(params, payload);
      if (constructedPayload != null) {
        payload = constructedPayload;
        useUrlEncodedContentType = true;
      }
      checkPostTask(params, payload, query);
    } else {
      if (!params.isEmpty()) {
        query = encodeParamsUrlEncoded(params);
      }
      if (query != null && query.length() != 0) {
        relativeUrl.append("?").append(query);
      }
    }
    if (payload != null && payload.length != 0 && !taskOptions.getMethod().supportsBody()) {
      throw new IllegalArgumentException(
          taskOptions.getMethod().toString() + " method may not specify a payload.");
    }

    fillAddRequest(txn,
        queueName,
        taskOptions.getTaskName(),
        determineEta(taskOptions),
        method,
        relativeUrl.toString(),
        payload,
        headers,
        retryOptions,
        useUrlEncodedContentType,
        addRequest);
  }

  private void fillAddRequest(com.google.appengine.api.datastore.Transaction txn,
      String queueName,
      String taskName,
      long etaMillis,
      TaskOptions.Method method,
      String relativeUrl,
      byte[] payload,
      HashMap<String, List<String>> headers,
      RetryOptions retryOptions,
      boolean useUrlEncodedContentType,
      TaskQueueAddRequest addRequest) {
    addRequest.setQueueName(queueName);
    addRequest.setTaskName(taskName == null ? "" : taskName);

    if (method == TaskOptions.Method.PULL) {
      addRequest.setMode(TaskQueueMode.Mode.PULL.getValue());
    } else {
      addRequest.setUrl(relativeUrl.toString());
      addRequest.setMode(TaskQueueMode.Mode.PUSH.getValue());
      addRequest.setMethod(method.getPbMethod());
    }

    if (payload != null) {
      addRequest.setBodyAsBytes(payload);
    }

    addRequest.setEtaUsec(etaMillis * 1000);

    if (taskName != null && !taskName.equals("") && txn != null) {
      throw new IllegalArgumentException(
          "transactional tasks cannot be named: " + taskName);
    }
    if (txn != null) {
      addRequest.setTransaction(localTxnToRemoteTxn(txn));
    }

    if (retryOptions != null) {
      fillRetryParameters(retryOptions, addRequest.getMutableRetryParameters());
    }

    if (NamespaceManager.getGoogleAppsNamespace().length() != 0) {
      if (!headers.containsKey(DEFAULT_NAMESPACE_HEADER)) {
        headers.put(DEFAULT_NAMESPACE_HEADER,
                    Arrays.asList(NamespaceManager.getGoogleAppsNamespace()));
      }
    }
    if (!headers.containsKey(CURRENT_NAMESPACE_HEADER)) {
      String namespace = NamespaceManager.get();
      headers.put(CURRENT_NAMESPACE_HEADER, Arrays.asList(namespace == null ? "" : namespace));
    }
    for (Entry<String, List<String>> entry : headers.entrySet()) {
      if (useUrlEncodedContentType && entry.getKey().toLowerCase().equals("content-type")) {
        continue;
      }

      for (String value : entry.getValue()) {
        Header header = addRequest.addHeader();
        header.setKey(entry.getKey());
        header.setValue(value);
      }
    }
    if (useUrlEncodedContentType) {
      Header contentTypeHeader = addRequest.addHeader();
      contentTypeHeader.setKey("content-type");
      contentTypeHeader.setValue("application/x-www-form-urlencoded");
    }

    if (method == TaskOptions.Method.PULL) {
      if (addRequest.encodingSize() > QueueConstants.maxPullTaskSizeBytes()) {
        throw new IllegalArgumentException("Task size too large");
      }
    } else {
      if (addRequest.encodingSize() > QueueConstants.maxPushTaskSizeBytes()) {
        throw new IllegalArgumentException("Task size too large");
      }
    }
  }

  /**
   * Translates a local transaction to the Datastore PB.
   * Due to pb dependency issues, Transaction pb is redefined for TaskQueue.
   * Keep in sync with DatastoreServiceImpl.localTxnToRemoteTxn.
   */
  private static Transaction localTxnToRemoteTxn(
      com.google.appengine.api.datastore.Transaction local) {
    Transaction remote = new Transaction();
    remote.setApp(local.getApp());
    remote.setHandle(Long.parseLong(local.getId()));
    return remote;
  }

  /**
   * Translates from RetryOptions to TaskQueueRetryParameters.
   * Also checks ensures minBackoffSeconds and maxBackoffSeconds are ordered
   * correctly.
   */
  private static void fillRetryParameters(
      RetryOptions retryOptions,
      TaskQueueRetryParameters retryParameters) {
    if (retryOptions.getTaskRetryLimit() != null) {
      retryParameters.setRetryLimit(retryOptions.getTaskRetryLimit());
    }
    if (retryOptions.getTaskAgeLimitSeconds() != null) {
      retryParameters.setAgeLimitSec(retryOptions.getTaskAgeLimitSeconds());
    }
    if (retryOptions.getMinBackoffSeconds() != null) {
      retryParameters.setMinBackoffSec(retryOptions.getMinBackoffSeconds());
    }
    if (retryOptions.getMaxBackoffSeconds() != null) {
      retryParameters.setMaxBackoffSec(retryOptions.getMaxBackoffSeconds());
    }
    if (retryOptions.getMaxDoublings() != null) {
      retryParameters.setMaxDoublings(retryOptions.getMaxDoublings());
    }

    if (retryParameters.hasMinBackoffSec() && retryParameters.hasMaxBackoffSec()) {
      if (retryParameters.getMinBackoffSec() > retryParameters.getMaxBackoffSec()) {
        throw new IllegalArgumentException(
            "minBackoffSeconds must not be greater than maxBackoffSeconds.");
      }
    } else if (retryParameters.hasMinBackoffSec()) {
      if (retryParameters.getMinBackoffSec() > retryParameters.getMaxBackoffSec()) {
        retryParameters.setMaxBackoffSec(retryParameters.getMinBackoffSec());
      }
    } else if (retryParameters.hasMaxBackoffSec()) {
      if (retryParameters.getMinBackoffSec() > retryParameters.getMaxBackoffSec()) {
        retryParameters.setMinBackoffSec(retryParameters.getMaxBackoffSec());
      }
    }
  }

  /**
   * See {@link Queue#add(TaskOptions)}.
   */
  public TaskHandle add(TaskOptions taskOptions) {
    return add(getDatastoreService().getCurrentTransaction(null), taskOptions);
  }

  /**
   * See {@link Queue#add(Iterable)}.
   */
  public List<TaskHandle> add(Iterable<TaskOptions> taskOptions) {
    return add(getDatastoreService().getCurrentTransaction(null), taskOptions);
  }

  /**
   * See {@link Queue#add(com.google.appengine.api.datastore.Transaction, TaskOptions)}.
   */
  public TaskHandle add(com.google.appengine.api.datastore.Transaction txn,
      TaskOptions taskOptions) {
    return add(txn, Collections.singletonList(taskOptions)).get(0);
  }

  /**
   * See {@link
   * Queue#add(com.google.appengine.api.datastore.Transaction, Iterable)}.
   */
  public List<TaskHandle> add(com.google.appengine.api.datastore.Transaction txn,
      Iterable<TaskOptions> taskOptions) {
    List<TaskOptions> taskOptionsList = new ArrayList<TaskOptions>();
    Set<String> taskNames = new HashSet<String>();

    TaskQueueBulkAddRequest bulkAddRequest = new TaskQueueBulkAddRequest();
    TaskQueueBulkAddResponse bulkAddResponse = new TaskQueueBulkAddResponse();

    boolean hasPushTask = false;
    boolean hasPullTask = false;
    for (TaskOptions option : taskOptions) {
      TaskQueueAddRequest addRequest = bulkAddRequest.addAddRequest();
      validateAndFillAddRequest(txn, option, addRequest);
      if (addRequest.getMode() == TaskQueueMode.Mode.PULL.getValue()) {
        hasPullTask = true;
      } else {
        hasPushTask = true;
      }

      taskOptionsList.add(option);
      if (option.getTaskName() != null && !option.getTaskName().equals("")) {
        if (!taskNames.add(option.getTaskName())) {
          throw new IllegalArgumentException(
              String.format("Identical task names in request : \"%s\" duplicated",
                  option.getTaskName()));
        }
      }
    }
    if (bulkAddRequest.addRequestSize() > QueueConstants.maxTasksPerAdd()) {
      throw new IllegalArgumentException(
          String.format("No more than %d tasks can be added in a single add call",
              QueueConstants.maxTasksPerAdd()));
    }

    if (hasPullTask && hasPushTask) {
      throw new IllegalArgumentException(
          "May not add both push tasks and pull tasks in the same call.");
    }

    if (txn != null &&
        bulkAddRequest.encodingSize() > QueueConstants.maxTransactionalRequestSizeBytes()) {
      throw new IllegalArgumentException(
          String.format("Transactional add may not be larger than %d bytes: %d bytes requested.",
              QueueConstants.maxTransactionalRequestSizeBytes(),
              bulkAddRequest.encodingSize()));
    }

    apiHelper.makeSyncCall("BulkAdd", bulkAddRequest, bulkAddResponse);

    if (bulkAddResponse.taskResultSize() != bulkAddRequest.addRequestSize()) {
        throw new InternalFailureException(
            String.format("expected %d results from BulkAdd(), got %d",
                bulkAddRequest.addRequestSize(), bulkAddResponse.taskResultSize()));
    }

    List<TaskHandle> tasks = new ArrayList<TaskHandle>();
    for (int i = 0; i < bulkAddResponse.taskResultSize(); ++i) {
      TaskQueueBulkAddResponse.TaskResult taskResult = bulkAddResponse.taskResults().get(i);
      TaskQueueAddRequest addRequest = bulkAddRequest.getAddRequest(i);
      TaskOptions options = taskOptionsList.get(i);

      if (taskResult.getResult() == TaskQueueServiceError.ErrorCode.OK.getValue()) {
        String taskName = options.getTaskName();
        if (taskResult.hasChosenTaskName()) {
          taskName = taskResult.getChosenTaskName();
        }
        TaskOptions taskResultOptions = new TaskOptions(options);
        taskResultOptions.taskName(taskName)
                         .etaMillis(addRequest.getEtaUsec() / 1000)
                         .payload(addRequest.getBodyAsBytes());
        tasks.add(new TaskHandle(taskResultOptions, queueName, 0));
      } else if (taskResult.getResult() != TaskQueueServiceError.ErrorCode.SKIPPED.getValue()) {
        throw QueueApiHelper.translateError(taskResult.getResult(), "");
      }
    }
    return tasks;
  }

  long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  private long determineEta(TaskOptions taskOptions) {
    Long etaMillis = taskOptions.getEtaMillis();
    Long countdownMillis = taskOptions.getCountdownMillis();
    if (etaMillis == null) {
      if (countdownMillis == null) {
        return currentTimeMillis();
      } else {
        if (countdownMillis > QueueConstants.getMaxEtaDeltaMillis()) {
          throw new IllegalArgumentException("ETA too far into the future");
        }
        if (countdownMillis < 0) {
          throw new IllegalArgumentException("Negative countdown is not allowed");
        }
        return currentTimeMillis() + countdownMillis;
      }
    } else {
      if (countdownMillis == null) {
        if (etaMillis - currentTimeMillis() > QueueConstants.getMaxEtaDeltaMillis()) {
          throw new IllegalArgumentException("ETA too far into the future");
        }
        if (etaMillis < 0) {
          throw new IllegalArgumentException("Negative ETA is invalid");
        }
        return etaMillis;
      } else {
        throw new IllegalArgumentException(
            "Only one or neither of EtaMillis and CountdownMillis may be specified");
      }
    }
  }

  byte[] encodeParamsPost(List<Param> params) {
    byte[] payload;
    try {
      payload = encodeParamsUrlEncoded(params).getBytes("UTF-8");
    } catch (UnsupportedEncodingException exception) {
      throw new UnsupportedTranslationException(exception);
    }

    return payload;
  }

  String encodeParamsUrlEncoded(List<Param> params) {
    StringBuilder result = new StringBuilder();
    try {
      String appender = "";
      for (Param param : params) {
        result.append(appender);
        appender = "&";
        result.append(param.getURLEncodedName());
        result.append("=");
        result.append(param.getURLEncodedValue());
      }
    } catch (UnsupportedEncodingException exception) {
      throw new UnsupportedTranslationException(exception);
    }
    return result.toString();
  }

  private String defaultUrl() {
    return DEFAULT_QUEUE_PATH + "/" + queueName;
  }

  /**
   * See {@link Queue#getQueueName()}.
   */
  public String getQueueName() {
    return queueName;
  }

  DatastoreService getDatastoreService() {
    return datastoreService;
  }

  /**
   * See {@link Queue#purge()}.
   */
  public void purge() {
    TaskQueuePurgeQueueRequest purgeRequest = new TaskQueuePurgeQueueRequest();
    TaskQueuePurgeQueueResponse purgeResponse = new TaskQueuePurgeQueueResponse();

    purgeRequest.setQueueName(queueName);
    apiHelper.makeSyncCall("PurgeQueue", purgeRequest, purgeResponse);
  }

  /**
   * See {@link Queue#deleteTask(String)}.
   */
  @Override
  public boolean deleteTask(String taskName) {
    TaskHandle.validateTaskName(taskName);
    return deleteTask(new TaskHandle(taskName, this.queueName, 0));
  }

  /**
   * See {@link Queue#deleteTask(TaskHandle)}.
   */
  @Override
  public boolean deleteTask(TaskHandle taskHandle) {
    List<TaskHandle> taskHandles = new ArrayList<TaskHandle>(1);
    taskHandles.add(taskHandle);
    List<Boolean> result = deleteTask(taskHandles);
    return result.get(0);
  }

  /**
   * See {@link Queue#deleteTask(List<TaskHandle>)}.
   */
  @Override
  public List<Boolean> deleteTask(List<TaskHandle> taskHandles) {

    TaskQueueDeleteRequest deleteRequest = new TaskQueueDeleteRequest();
    TaskQueueDeleteResponse deleteResponse = new TaskQueueDeleteResponse();
    deleteRequest.setQueueName(queueName);

    for (TaskHandle taskHandle : taskHandles) {
      if (taskHandle.getQueueName().equals(this.queueName)) {
        deleteRequest.addTaskName(taskHandle.getName());
      } else {
        throw new QueueNameMismatchException(
          String.format("The task %s is associated with the queue named %s "
              + "and cannot be deleted from the queue named %s.",
              taskHandle.getName(), taskHandle.getQueueName(), this.queueName));
      }
    }

    apiHelper.makeSyncCall("Delete", deleteRequest, deleteResponse);

    List<Boolean> result = new ArrayList<Boolean>(deleteResponse.resultSize());

    for (int i = 0; i < deleteResponse.resultSize(); ++i) {
      int errorCode = deleteResponse.getResult(i);
      if (errorCode != TaskQueueServiceError.ErrorCode.OK.getValue() &&
          errorCode != TaskQueueServiceError.ErrorCode.TOMBSTONED_TASK.getValue() &&
          errorCode != TaskQueueServiceError.ErrorCode.UNKNOWN_TASK.getValue()) {
        throw QueueApiHelper.translateError(errorCode, "");
      }
      result.add(errorCode == TaskQueueServiceError.ErrorCode.OK.getValue());
    }

    return result;
  }

  /**
   * See {@link Queue#leaseTasks(long, TimeUnit, long)}.
   */
  @Override
  public List<TaskHandle> leaseTasks(long lease, TimeUnit unit, long countLimit) {
    long leaseMillis = unit.toMillis(lease);
    if (leaseMillis > QueueConstants.maxLease(TimeUnit.MILLISECONDS)) {
      throw new IllegalArgumentException(
          String.format("A lease period can be no longer than %d seconds",
              QueueConstants.maxLease(TimeUnit.SECONDS)));
    }
    if (leaseMillis < 0) {
      throw new IllegalArgumentException("The lease time must not be negative");
    }
    if (countLimit > QueueConstants.maxLeaseCount()) {
      throw new IllegalArgumentException(
          String.format("No more than %d tasks can be leased in one call",
              QueueConstants.maxLeaseCount()));
    }
    if (countLimit <= 0) {
      throw new IllegalArgumentException("The number of tasks to lease must be greater than 0");
    }

    TaskQueueQueryAndOwnTasksRequest leaseRequest = new TaskQueueQueryAndOwnTasksRequest();
    TaskQueueQueryAndOwnTasksResponse leaseResponse = new TaskQueueQueryAndOwnTasksResponse();

    leaseRequest.setQueueName(queueName);
    leaseRequest.setLeaseSeconds(leaseMillis / 1000.0);
    leaseRequest.setMaxTasks(countLimit);

    apiHelper.makeSyncCall("QueryAndOwnTasks", leaseRequest, leaseResponse);

    List<TaskHandle> result = new ArrayList<TaskHandle>();
    for (TaskQueueQueryAndOwnTasksResponse.Task response : leaseResponse.tasks()) {
      TaskOptions options = TaskOptions.Builder.withTaskName(response.getTaskName())
                                               .etaMillis(response.getEtaUsec() / 1000)
                                               .payload(response.getBodyAsBytes());
      result.add(new TaskHandle(options, queueName, response.getRetryCount()));
    }

    return result;
  }
}
