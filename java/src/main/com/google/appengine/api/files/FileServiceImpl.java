// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.api.files;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.files.FileServicePb.AppendRequest;
import com.google.appengine.api.files.FileServicePb.AppendResponse;
import com.google.appengine.api.files.FileServicePb.CloseRequest;
import com.google.appengine.api.files.FileServicePb.CloseResponse;
import com.google.appengine.api.files.FileServicePb.CreateRequest;
import com.google.appengine.api.files.FileServicePb.CreateResponse;
import com.google.appengine.api.files.FileServicePb.DeleteRequest;
import com.google.appengine.api.files.FileServicePb.DeleteResponse;
import com.google.appengine.api.files.FileServicePb.FileContentType.ContentType;
import com.google.appengine.api.files.FileServicePb.FileServiceErrors;
import com.google.appengine.api.files.FileServicePb.OpenRequest;
import com.google.appengine.api.files.FileServicePb.OpenRequest.OpenMode;
import com.google.appengine.api.files.FileServicePb.OpenResponse;
import com.google.appengine.api.files.FileServicePb.ReadRequest;
import com.google.appengine.api.files.FileServicePb.ReadResponse;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implements {@link FileService} by using {@link ApiProxy} to make RPC calls to
 * the App Engine File API.
 *
 */
class FileServiceImpl implements FileService {

  static final String PACKAGE = "file";

  static final String FILESYSTEM_BLOBSTORE = AppEngineFile.FileSystem.BLOBSTORE.getName();
  static final String PARAMETER_MIME_TYPE = "content_type";
  static final String PARAMETER_BLOB_INFO_UPLOADED_FILE_NAME = "file_name";
  static final String DEFAULT_MIME_TYPE = "application/octet-stream";

  private static final String BLOB_INFO_CREATION_HANDLE_PROPERTY = "creation_handle";

  private static final String CREATION_HANDLE_PREFIX = "writable:";

  /**
   * {@inheritDoc}
   */
  @Override
  public AppEngineFile createNewBlobFile(String mimeType) throws IOException {
    return createNewBlobFile(mimeType, "");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AppEngineFile createNewBlobFile(String mimeType, String blobInfoUploadedFileName)
      throws IOException {
    if (mimeType == null || mimeType.trim().isEmpty()) {
      mimeType = DEFAULT_MIME_TYPE;
    }

    Map<String, String> params = new TreeMap<String, String>();
    params.put(PARAMETER_MIME_TYPE, mimeType);
    if (blobInfoUploadedFileName != null && !blobInfoUploadedFileName.isEmpty()) {
      params.put(PARAMETER_BLOB_INFO_UPLOADED_FILE_NAME, blobInfoUploadedFileName);
    }
    String filePath = create(FILESYSTEM_BLOBSTORE, null, ContentType.RAW, params);
    AppEngineFile file = new AppEngineFile(filePath);
    if (!file.getNamePart().startsWith(CREATION_HANDLE_PREFIX)) {
      throw new RuntimeException("Expected creation handle: " + file.getFullPath());
    }
    return file;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public FileWriteChannel openWriteChannel(AppEngineFile file, boolean lock)
      throws FileNotFoundException, FinalizationException, LockException, IOException {
    FileWriteChannel channel = new FileWriteChannelImpl(file, lock, this);
    openForAppend(file, lock);
    return channel;
  }

  /**
   * Open the given file for append and optionally lock it.
   *
   * @param file the file to open
   * @param lock should the file be locked for exclusive access?
   * @throws FileNotFoundException if the file does not exist in the File Proxy
   * @throws FinalizationException if the file has already been finalized. The
   *         file may have been finalized by another request.
   * @throws LockException if the file is locked in a different App Engine
   *         request, or if {@code lock = true} and the file is opened in a
   *         different App Engine request
   * @throws IOException if any other unexpected problem occurs
   */
  void openForAppend(AppEngineFile file, boolean lock)
      throws FileNotFoundException, FinalizationException, LockException, IOException {
    openForAppend(file.getFullPath(), ContentType.RAW, lock);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public FileReadChannel openReadChannel(AppEngineFile file, boolean lock)
      throws FileNotFoundException, LockException, IOException {
    FileReadChannel channel = new FileReadChannelImpl(file, this);
    openForRead(file, lock);
    return channel;
  }

  public void delete(AppEngineFile file) throws IOException {
    delete(file.getFullPath());
  }

  /**
   * Appends bytes from the given buffer to the end of the given file.
   *
   * @param file the file to which to append bytes. Must be opened for append in
   *        the current request
   * @param buffer The buffer from which bytes are to be retrieved
   * @param sequenceKey the sequence key. See the explanation of the {@code
   *        sequenceKey} paramater at
   *        {@link FileWriteChannel#write(ByteBuffer, String)}
   * @throws IllegalArgumentException if {@code file} is not writable
   * @throws KeyOrderingException if {@code sequenceKey} is not {@code null} and
   *         the backend system already has recorded a last good sequence key
   *         for this file and {@code sequenceKey} is not strictly
   *         lexicographically greater than the last good sequence key
   * @throws IOException if the file is not opened for append in the current App
   *         Engine request or any other unexpected problem occurs
   */
  int append(AppEngineFile file, ByteBuffer buffer, String sequenceKey) throws IOException {
    if (null == buffer) {
      throw new NullPointerException("buffer is null");
    }
    if (null == file) {
      throw new NullPointerException("file is null");
    }
    ByteString data = ByteString.copyFrom(buffer);
    append(file.getFullPath(), data, sequenceKey);
    return data.size();
  }

  private static final String BLOB_FILE_INDEX_KIND = "__BlobFileIndex__";

  private static final String BLOB_KEY_PROPERTY_NAME = "blob_key";

  /**
   * {@inheritDoc}
   */
  @Override
  public BlobKey getBlobKey(AppEngineFile file) {
    if (null == file) {
      throw new NullPointerException("file is null");
    }
    if (file.getFileSystem() != AppEngineFile.FileSystem.BLOBSTORE) {
      throw new IllegalArgumentException("file is not of type BLOBSTORE");
    }
    BlobKey cached = file.getCachedBlobKey();
    if (null != cached) {
      return cached;
    }
    String namePart = file.getNamePart();
    String creationHandle = (namePart.startsWith(CREATION_HANDLE_PREFIX) ? namePart : null);

    if (null == creationHandle) {
      return new BlobKey(namePart);
    }

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    String origNamespace = NamespaceManager.get();
    Query query;
    Entity blobInfoEntity;
    try {
      NamespaceManager.set("");
      try {
        Entity blobFileIndexEntity =
            datastore.get(KeyFactory.createKey(BLOB_FILE_INDEX_KIND, creationHandle));
        String blobKey = (String) blobFileIndexEntity.getProperty("blob_key");
        blobInfoEntity = datastore.get(KeyFactory.createKey(BlobInfoFactory.KIND, blobKey));
      } catch (EntityNotFoundException ex) {
        query = new Query(BlobInfoFactory.KIND);
        query.addFilter(BLOB_INFO_CREATION_HANDLE_PROPERTY, Query.FilterOperator.EQUAL,
            creationHandle);
        blobInfoEntity = datastore.prepare(query).asSingleEntity();
      }
    } finally {
      NamespaceManager.set(origNamespace);
    }

    if (null == blobInfoEntity) {
      return null;
    }
    BlobInfo blobInfo = new BlobInfoFactory().createBlobInfo(blobInfoEntity);
    return blobInfo.getBlobKey();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AppEngineFile getBlobFile(BlobKey blobKey) throws FileNotFoundException {
    if (null == blobKey) {
      throw new NullPointerException("blobKey is null");
    }
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    Entity entity;
    try {
      entity = datastore.get(getMetadataKeyForBlobKey(blobKey));
    } catch (EntityNotFoundException ex) {
      throw new FileNotFoundException();
    }
    String creationHandle = (String) entity.getProperty(BLOB_INFO_CREATION_HANDLE_PROPERTY);
    String namePart = (null == creationHandle ? blobKey.getKeyString() : creationHandle);
    AppEngineFile file = new AppEngineFile(AppEngineFile.FileSystem.BLOBSTORE, namePart);
    file.setCachedBlobKey(blobKey);
    return file;
  }

  private Key getMetadataKeyForBlobKey(BlobKey blobKey) {
    String origNamespace = NamespaceManager.get();
    try {
      NamespaceManager.set("");
      return KeyFactory.createKey(null, BlobInfoFactory.KIND, blobKey.getKeyString());
    } finally {
      NamespaceManager.set(origNamespace);
    }
  }

  /**
   * Reads bytes from {@code file} starting from {@code startingPos} and puts
   * the bytes into the {@code buffer}. Returns the number of bytes read. The
   * number of bytes read will be the minumum of the number of bytes available
   * in the file and the buffer's {@link ByteBuffer#remaining() free bytes}.
   *
   * @param file the file from which to read bytes. Must be opened for read in
   *        the current request
   * @param buffer the destination buffer
   * @return the number of bytes read
   * @throws IOException if the file is not opened for read in the current App
   *         Engine request or any other unexpected problem occurs
   */
  int read(AppEngineFile file, ByteBuffer buffer, long startingPos) throws IOException {
    if (startingPos < 0) {
      throw new IllegalArgumentException("startingPos is negative: " + startingPos);
    }
    if (buffer == null) {
      throw new NullPointerException("buffer is null");
    }
    long remaining = buffer.remaining();
    if (buffer.remaining() < 1) {
      return 0;
    }
    ByteString byteString = read(file.getFullPath(), startingPos, remaining);
    byteString.copyTo(buffer);
    int numBytesRead = byteString.size();
    if (numBytesRead <= 0) {
      numBytesRead = -1;
    }
    return numBytesRead;
  }

  /**
   * Change the state of the given file to closed and optionally finalize the
   * file. After the file is finalized it may be read, and it may no longer be
   * written.
   *
   * @param file the file to close and optionally finalize. The file must be
   *        opened in the current request.
   * @param finalize should the file be finalized? The file may only be
   *        finalized if the current request holds the lock for the file
   * @throws IllegalStateException if {@code finalize = true} and the current
   *         request does not hold the exclusive lock on {@code file}
   * @throws IOException if the file is not opened in the current request, if
   *         {@code finalize = true} and the file is already finalized or if any
   *         other unexpected problem occurs
   */
  void close(AppEngineFile file, boolean finalize) throws IOException {
    try {
      close(file.getFullPath(), finalize);
    } catch (LockException e) {
      if (finalize) {
        throw new IllegalStateException("The current request does not hold the exclusive lock.");
      }
      throw e;
    }
  }

  /**
   * Opens a file for appending by making the "Open" RPC call with mode=APPEND.
   */
  private void openForAppend(String fileName, ContentType contentType, boolean lock)
      throws IOException {
    open(fileName, contentType, OpenMode.APPEND, lock);
  }

  private void openForRead(AppEngineFile file, boolean lock)
      throws FileNotFoundException, LockException, IOException {
    if (null == file) {
      throw new NullPointerException("file is null");
    }
    openForRead(file.getFullPath(), ContentType.RAW, lock);
  }

  /**
   * Opens a file for reading by making the "Open" RPC call with mode=READ
   */
  private void openForRead(String fileName, ContentType contentType, boolean lock)
      throws IOException {
    open(fileName, contentType, OpenMode.READ, lock);
  }

  /**
   * Makes the "Create" RPC call.
   *
   * @return created file name.
   */
  private String create(
      String fileSystem, String fileName, ContentType contentType, Map<String, String> parameters)
      throws IOException {
    CreateRequest.Builder request = CreateRequest.newBuilder();
    request.setFilesystem(fileSystem);
    if (fileName != null && !fileName.isEmpty()) {
      request.setFilename(fileName);
    }
    request.setContentType(contentType);
    if (parameters != null) {
      for (Map.Entry<String, String> e : parameters.entrySet()) {
        CreateRequest.Parameter.Builder parameter = request.addParametersBuilder();
        parameter.setName(e.getKey());
        parameter.setValue(e.getValue());
      }
    }
    CreateResponse.Builder response = CreateResponse.newBuilder();
    makeSyncCall("Create", request, response);
    return response.build().getFilename();
  }

  /**
   * Makes the "Open" RPC call
   */
  private void open(String fileName, ContentType contentType, OpenMode openMode, boolean lock)
      throws IOException {
    OpenRequest.Builder openRequest = OpenRequest.newBuilder();
    openRequest.setFilename(fileName);
    openRequest.setContentType(contentType);
    openRequest.setOpenMode(openMode);
    openRequest.setExclusiveLock(lock);
    OpenResponse.Builder openResponse = OpenResponse.newBuilder();
    makeSyncCall("Open", openRequest, openResponse);
  }

  /**
   * Makes the 'Append' RPC call
   */
  private void append(String fileName, ByteString data, String sequenceKey) throws IOException {
    AppendRequest.Builder appendRequest = AppendRequest.newBuilder();
    appendRequest.setFilename(fileName);
    appendRequest.setData(data);
    if (null != sequenceKey) {
      appendRequest.setSequenceKey(sequenceKey);
    }
    AppendResponse.Builder appendResponse = AppendResponse.newBuilder();
    makeSyncCall("Append", appendRequest, appendResponse);
  }

  /**
   * Makes the "Read" RPC call
   */
  private ByteString read(String fileName, long pos, long maxBytes) throws IOException {
    ReadRequest.Builder readRequest = ReadRequest.newBuilder();
    readRequest.setFilename(fileName);
    readRequest.setMaxBytes(maxBytes);
    readRequest.setPos(pos);
    ReadResponse.Builder readResponse = ReadResponse.newBuilder();
    makeSyncCall("Read", readRequest, readResponse);
    return readResponse.build().getData();
  }

  /**
   * Makes the "Close" RPC call
   */
  private void close(String fileName, boolean finalize) throws IOException {
    CloseRequest.Builder closeRequest = CloseRequest.newBuilder();
    closeRequest.setFilename(fileName);
    closeRequest.setFinalize(finalize);
    CloseResponse.Builder closeResponse = CloseResponse.newBuilder();
    makeSyncCall("Close", closeRequest, closeResponse);
  }

  /**
   * Makes the "Delete" RPC call
   */
  private void delete(String fileName) throws IOException {
    DeleteRequest.Builder deleteRequest = DeleteRequest.newBuilder();
    deleteRequest.setFilename(fileName);
    DeleteResponse.Builder deleteResponse = DeleteResponse.newBuilder();
    makeSyncCall("Delete", deleteRequest, deleteResponse);
  }

  /**
   * Makes a synchronous RPC call to the app server
   *
   * @param callName
   * @param request
   * @param response
   * @throws IOException
   */
  private void makeSyncCall(String callName, Message.Builder request, Message.Builder response)
      throws IOException {
    try {
      byte[] responseBytes =
          ApiProxy.makeSyncCall(PACKAGE, callName, request.build().toByteArray());
      response.mergeFrom(responseBytes);
    } catch (ApiProxy.ApplicationException ex) {
      throw translateException(ex, null);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException("Internal logic error: Response PB could not be parsed.", e);
    }
  }

  /**
   * Translates from an internal to a public exception
   */
  private static IOException translateException(ApiProxy.ApplicationException ex, String message) {
    int errorCode = ex.getApplicationError();
    FileServiceErrors.ErrorCode errorCodeEnum = FileServiceErrors.ErrorCode.valueOf(errorCode);
    switch (errorCodeEnum) {
      case EXCLUSIVE_LOCK_FAILED:
        return new LockException(message, ex);
      case EXISTENCE_ERROR:
        return new FileNotFoundException();
      case FINALIZATION_ERROR:
        return new FinalizationException(message, ex);
      case WRONG_KEY_ORDER:
      case SEQUENCE_KEY_OUT_OF_ORDER:
        return new KeyOrderingException(message, ex);
      default:
        return new IOException(message, ex);
    }
  }

}
