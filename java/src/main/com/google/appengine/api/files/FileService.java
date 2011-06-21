// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.api.files;

import com.google.appengine.api.blobstore.BlobKey;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * This is the interface for interacting with the Google App Engine File
 * Service. A {@code FileService} instance is obtained via
 * {@link FileServiceFactory#getFileService()}.
 *
 */
public interface FileService {

  /**
   * Creates a new empty file in the BlobStore of the specified mime-type and
   * returns an {@code AppEngineFile} representing the file. The returned
   * instance will have a {@link AppEngineFile#getFileSystem() file system} of
   * {@link com.google.appengine.api.files.AppEngineFile.FileSystem#BLOBSTORE
   * BLOBSTORE}.
   *
   * @param mimeType the mime-type of the file to be created. This parameter may
   *        be used to inform the BlobStore of the mime-type for the file. The
   *        mime-type will be returned by the BlobStore in an HTTP response if
   *        the file is requested directly from the BlobStore using the
   *        blob-key.
   * @return A {@code AppEngineFile} representing the newly created file.
   * @throws IOException If there is any problem communicating with the backend
   *         system
   */
  AppEngineFile createNewBlobFile(String mimeType) throws IOException;

  /**
   * Creates a new empty file in the BlobStore of the specified mime-type and
   * returns an {@code AppEngineFile} representing the file. The returned
   * instance will have a {@link AppEngineFile#getFileSystem() file system} of
   * {@link com.google.appengine.api.files.AppEngineFile.FileSystem#BLOBSTORE
   * BLOBSTORE}.
   *
   * @param mimeType the mime-type of the file to be created. This parameter may
   *        be used to inform the BlobStore of the mime-type for the file. The
   *        mime-type will be returned by the BlobStore in an HTTP response if
   *        the file is requested directly from the BlobStore using the
   *        blob-key.
   * @param blobInfoUploadedFileName BlobStore will store this name in the
   *        BlobInfo's fileName field. This string will <em>not</em> be
   *        the {@link AppEngineFile#getNamePart() name} of the returned
   *        {@code AppEngineFile}. It will be returned by the BlobStore in an HTTP
   *        response if the file is requested directly from the BlobStore using
   *        the blob-key.
   * @return A {@code AppEngineFile} representing the newly created file.
   * @throws IOException If there is any problem communicating with the backend
   *         system
   */
  AppEngineFile createNewBlobFile(String mimeType, String blobInfoUploadedFileName)
  throws IOException;

  /**
   * Given an {@code AppEngineFile}, returns a {@code FileWriteChannel} that may
   * be used for appending bytes to the file.
   *
   * @param file the file to which to append bytes. The file must exist and it
   *        must not yet have been finalized.
   * @param lock should the file be locked for exclusive access?
   * @throws FileNotFoundException if the file does not exist in the backend
   *         repository. The file may have been deleted by another request, or
   *         the file may have been lost due to system failure or a scheduled
   *         relocation. Each backend repository offers different guarantees
   *         regarding when this is possible.
   * @throws FinalizationException if the file has already been finalized. The
   *         file may have been finalized by another request.
   * @throws LockException if the file is locked in a different App Engine
   *         request, or if {@code lock = true} and the file is opened in a
   *         different App Engine request
   * @throws IOException if any other unexpected problem occurs
   */
  FileWriteChannel openWriteChannel(AppEngineFile file, boolean lock)
      throws FileNotFoundException, FinalizationException, LockException, IOException;

  /**
   * Given an {@code AppEngineFile}, returns a {@code FileReadChannel} that may
   * be used for reading bytes from the file.
   *
   * @param file The file from which to read bytes. The file must exist and it
   *        must have been finalized.
   * @param lock Should the file be locked for exclusive access?
   * @throws FileNotFoundException if the file does not exist in the backend
   *         repository. The file may have been deleted by another request, or
   *         the file may have been lost due to system failure or a scheduled
   *         relocation. Each backend repository offers different guarantees
   *         regarding when this is possible.
   * @throws FinalizationException if the file has not yet been finalized
   * @throws LockException if the file is locked in a different App Engine
   *         request, or if {@code lock = true} and the file is opened in a
   *         different App Engine request
   * @throws IOException if any other problem occurs contacting the backend
   *         system
   */
  FileReadChannel openReadChannel(AppEngineFile file, boolean lock)
      throws FileNotFoundException, LockException, IOException;

  /**
   * Given a
   * {@link com.google.appengine.api.files.AppEngineFile.FileSystem#BLOBSTORE
   * BLOBSTORE} file that has been finalized, returns the {@code BlobKey} for
   * the corresponding blob.
   *
   * @param file A {@link
   *        com.google.appengine.api.files.AppEngineFile.FileSystem#BLOBSTORE
   *        BLOBSTORE} file that has been finalized
   * @return The corresponding {@code BlobKey}, or {@code null} if none can be
   *         found. This can occur if the file has not been finalized, or if it
   *         does not exist.
   * @throws IllegalArgumentException if {@code file} is not a {@code BLOBSTORE}
   *         file.
   */
  BlobKey getBlobKey(AppEngineFile file);

  /**
   * Given a {@code BlobKey} returns the corresponding {@code AppEngineFile}.
   * The file has been finalized.
   *
   * @param blobKey A blobkey
   * @return The corresponding file.
   * @throws FileNotFoundException if there is no blob with the given blob key.
   */
  AppEngineFile getBlobFile(BlobKey blobKey) throws FileNotFoundException;

}
