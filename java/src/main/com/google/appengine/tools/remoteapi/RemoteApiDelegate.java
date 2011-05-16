// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.tools.remoteapi;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApiProxyException;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiProxy.LogRecord;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles App Engine API calls by making HTTP requests to a remote server.
 *
 */
class RemoteApiDelegate implements Delegate<Environment> {
  private final static Logger logger = Logger.getLogger(RemoteDatastore.class.getName());

  private final RemoteRpc remoteRpc;
  private final String currentUserEmail; private final Delegate urlFetchDelegate; private final ExecutorService executor;
  private final RemoteDatastore remoteDatastore;

  RemoteApiDelegate(RemoteRpc rpc, RemoteApiOptions options,Delegate urlFetchDelegate) {
    this.remoteRpc = rpc;
    this.currentUserEmail = options.getUserEmail();
    this.urlFetchDelegate = urlFetchDelegate;
    if (options.isAppEngineContainer()) {
      if (urlFetchDelegate == null) {
        throw new IllegalArgumentException("Options indicate we are running in an App Engine "
            + "but App Engine services are not available.");
      }
      this.executor = null;
    } else {
      this.executor = Executors.newFixedThreadPool(options.getMaxConcurrentRequests());
    }
    this.remoteDatastore = new RemoteDatastore(remoteRpc, options);
  }

  void resetRpcCount() {
    remoteRpc.resetRpcCount();
  }

  int getRpcCount() {
    return remoteRpc.getRpcCount();
  }

  @Override
  public byte[] makeSyncCall(
      Environment env, String serviceName, String methodName, byte[] request) {

    if (!env.getEmail().equals(currentUserEmail)) {
      String message =
          String.format("remote API call: user '%s' can't use client that's logged in as '%s'",
              env.getEmail(), currentUserEmail);
      throw new ApiProxyException(message);
    }

    if (urlFetchDelegate != null && serviceName.equals("urlfetch")) {
      return urlFetchDelegate.makeSyncCall(env, serviceName, methodName, request);
    } else if (serviceName.equals(RemoteDatastore.DATASTORE_SERVICE)) {
      return remoteDatastore.handleDatastoreCall(methodName, request);
    } else {
      return remoteRpc.call(serviceName, methodName, "", request);
    }
  }

  @Override
  public Future<byte[]> makeAsyncCall(final Environment env, final String serviceName,
      final String methodName, final byte[] request, ApiProxy.ApiConfig apiConfig) {
    if (executor == null) {
      try {
        return new FakeFuture<byte[]>(makeSyncCall(env, serviceName, methodName, request));
      } catch (Exception e) {
        return new FakeFuture<byte[]>(e);
      }
    }
    return executor.submit(new Callable<byte[]>() {
      @Override
      public byte[] call() throws Exception {
        return makeSyncCall(env, serviceName, methodName, request);
      }
    });
  }

  @Override
  public void log(Environment environment, LogRecord record) {
    logger.log(toJavaLevel(record.getLevel()),
        "[" + record.getTimestamp() + "] " + record.getMessage());
  }

  @Override
  public List<Thread> getRequestThreads(Environment environment) {
    return Collections.emptyList();
  }

  @Override
  public void flushLogs(Environment environment) {
  }

  public void shutdown() {
    if (executor != null) {
      executor.shutdown();
    }
  }

  private static Level toJavaLevel(LogRecord.Level apiProxyLevel) {
    switch (apiProxyLevel) {
      case debug:
        return Level.FINE;
      case info:
        return Level.INFO;
      case warn:
        return Level.WARNING;
      case error:
        return Level.SEVERE;
      case fatal:
        return Level.SEVERE;
      default:
        return Level.WARNING;
    }
  }

  /**
   * Wraps an already-resolved result in a {@link Future}.
   * @param <T> The type of the Future.
   */
  private static class FakeFuture<T> implements Future<T> {
    private final T result;
    private final Exception exception;

    FakeFuture(T result) {
      this(result, null);
    }

    FakeFuture(Exception e) {
      this(null, e);
    }

    private FakeFuture(T result, Exception exception) {
      this.result = result;
      this.exception = exception;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public T get() throws ExecutionException {
      if (exception != null) {
        throw new ExecutionException(exception);
      }
      return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws ExecutionException {
      if (exception != null) {
        throw new ExecutionException(exception);
      }
      return result;
    }
  }
}
