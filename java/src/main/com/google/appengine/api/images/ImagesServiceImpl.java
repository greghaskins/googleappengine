// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.images;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.images.ImagesServicePb.ImageData;
import com.google.appengine.api.images.ImagesServicePb.ImagesCompositeRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesCompositeResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesGetUrlBaseRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesGetUrlBaseResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogram;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogramRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesHistogramResponse;
import com.google.appengine.api.images.ImagesServicePb.ImagesServiceError.ErrorCode;
import com.google.appengine.api.images.ImagesServicePb.ImagesTransformRequest;
import com.google.appengine.api.images.ImagesServicePb.ImagesTransformResponse;
import com.google.appengine.api.images.ImagesServicePb.OutputSettings.MIME_TYPE;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Implementation of the ImagesService interface.
 *
 */
final class ImagesServiceImpl implements ImagesService {
  static final String PACKAGE = "images";

  /** {@inheritDoc} */
  public Image applyTransform(Transform transform, Image image) {
    return applyTransform(transform, image, OutputEncoding.PNG);
  }

  /** {@inheritDoc} */
  public Future<Image> applyTransformAsync(Transform transform, Image image) {
    return applyTransformAsync(transform, image, OutputEncoding.PNG);
  }

  /** {@inheritDoc} */
  public Image applyTransform(Transform transform, Image image,
                              OutputEncoding encoding) {
    return applyTransform(transform, image, new OutputSettings(encoding));
  }

  /** {@inheritDoc} */
  public Future<Image> applyTransformAsync(Transform transform, final Image image,
      OutputEncoding encoding) {
    return applyTransformAsync(transform, image, new OutputSettings(encoding));
  }

  /** {@inheritDoc} */
  public Image applyTransform(Transform transform, Image image,
                              OutputSettings settings) {
    ImagesTransformRequest.Builder request =
      generateImagesTransformRequest(transform, image, settings);

    ImagesTransformResponse.Builder response = ImagesTransformResponse.newBuilder();
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "Transform",
                                                   request.build().toByteArray());
      response.mergeFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      throw new ImagesServiceFailureException("Invalid protocol buffer:", ex);
    } catch (ApiProxy.ApplicationException ex) {
      throw convertApplicationException(request, ex);
    }
    image.setImageData(response.getImage().getContent().toByteArray());
    return image;
  }

  /** {@inheritDoc} */
  public Future<Image> applyTransformAsync(Transform transform, final Image image,
      OutputSettings settings) {
    final ImagesTransformRequest.Builder request =
      generateImagesTransformRequest(transform, image, settings);

    Future<byte[]> responseBytes = ApiProxy.makeAsyncCall(PACKAGE, "Transform",
        request.build().toByteArray());
    return new FutureWrapper<byte[], Image>(responseBytes){
      @Override
      protected Image wrap(byte[] responseBytes) throws IOException {
        ImagesTransformResponse.Builder response =
          ImagesTransformResponse.newBuilder()
          .mergeFrom(responseBytes);

        image.setImageData(response.getImage().getContent().toByteArray());
        return image;
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        if (cause instanceof ApiProxy.ApplicationException) {
          return convertApplicationException(request, (ApiProxy.ApplicationException) cause);
        }
        return cause;
      }
    };
  }

  /** {@inheritDoc} */
  public Image composite(Collection<Composite> composites, int width,
                         int height, long color) {
    return composite(composites, width, height, color, OutputEncoding.PNG);
  }

  public Image composite(Collection<Composite> composites, int width,
      int height, long color, OutputEncoding encoding) {
    return composite(composites, width, height, color, new OutputSettings(encoding));
  }

  /** {@inheritDoc} */
  public Image composite(Collection<Composite> composites, int width,
                         int height, long color, OutputSettings settings) {
    ImagesCompositeRequest.Builder request = ImagesCompositeRequest.newBuilder();
    ImagesCompositeResponse.Builder response = ImagesCompositeResponse.newBuilder();
    if (composites.size() > MAX_COMPOSITES_PER_REQUEST) {
      throw new IllegalArgumentException(
          "A maximum of " + MAX_COMPOSITES_PER_REQUEST
          + " composites can be applied in a single request");
    }
    if (width > MAX_RESIZE_DIMENSIONS || width <= 0
        || height > MAX_RESIZE_DIMENSIONS || height <= 0) {
      throw new IllegalArgumentException(
          "Width and height must <= " + MAX_RESIZE_DIMENSIONS + " and > 0");
    }
    if (color > 0xffffffffL || color < 0L) {
      throw new IllegalArgumentException(
          "Color must be in the range [0, 0xffffffff]");
    }
    if (color >= 0x80000000) {
      color -= 0x100000000L;
    }
    int fixedColor = (int) color;
    ImagesServicePb.ImagesCanvas.Builder canvas = ImagesServicePb.ImagesCanvas.newBuilder();
    canvas.setWidth(width);
    canvas.setHeight(height);
    canvas.setColor(fixedColor);
    canvas.setOutput(convertOutputSettings(settings));
    request.setCanvas(canvas);

    Map<Image, Integer> imageIdMap = new HashMap<Image, Integer>();
    for (Composite composite : composites) {
      composite.apply(request, imageIdMap);
    }

    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "Composite",
                                                   request.build().toByteArray());
      response.mergeFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      throw new ImagesServiceFailureException("Invalid protocol buffer:", ex);
    } catch (ApiProxy.ApplicationException ex) {
      ErrorCode code = ErrorCode.valueOf(ex.getApplicationError());
      if (code != null && code != ErrorCode.UNSPECIFIED_ERROR) {
        throw new IllegalArgumentException(ex.getErrorDetail());
      } else {
        throw new ImagesServiceFailureException(ex.getErrorDetail());
      }

    }
    return ImagesServiceFactory.makeImage(response.getImage().getContent().toByteArray());
  }

  /** {@inheritDoc} */
  public int[][] histogram(Image image) {
    ImagesHistogramRequest.Builder request = ImagesHistogramRequest.newBuilder();
    ImagesHistogramResponse.Builder response = ImagesHistogramResponse.newBuilder();
    request.setImage(convertImageData(image));
    try {
      byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "Histogram",
                                                   request.build().toByteArray());
      response.mergeFrom(responseBytes);
    } catch (InvalidProtocolBufferException ex) {
      throw new ImagesServiceFailureException("Invalid protocol buffer:", ex);
    } catch (ApiProxy.ApplicationException ex) {
      ErrorCode code = ErrorCode.valueOf(ex.getApplicationError());
      if (code != null && code != ErrorCode.UNSPECIFIED_ERROR) {
        throw new IllegalArgumentException(ex.getErrorDetail());
      } else {
        throw new ImagesServiceFailureException(ex.getErrorDetail());
      }
    }
    ImagesHistogram histogram = response.getHistogram();
    int[][] result = new int[3][];
    for (int i = 0; i < 3; i++) {
      result[i] = new int[256];
    }
    for (int i = 0; i < 256; i++) {
      result[0][i] = histogram.getRed(i);
      result[1][i] = histogram.getGreen(i);
      result[2][i] = histogram.getBlue(i);
    }
    return result;
  }

  /** {@inheritDoc} */
  public String getServingUrl(BlobKey blobKey) {
     ImagesGetUrlBaseRequest.Builder request = ImagesGetUrlBaseRequest.newBuilder();
     ImagesGetUrlBaseResponse.Builder response = ImagesGetUrlBaseResponse.newBuilder();
     if (blobKey == null) {
       throw new NullPointerException();
     }
     request.setBlobKey(blobKey.getKeyString());
     try {
       byte[] responseBytes = ApiProxy.makeSyncCall(PACKAGE, "GetUrlBase",
                                                    request.build().toByteArray());
       response.mergeFrom(responseBytes);
     } catch (InvalidProtocolBufferException ex) {
       throw new ImagesServiceFailureException("Invalid protocol buffer:", ex);
     } catch (ApiProxy.ApplicationException ex) {
       ErrorCode code = ErrorCode.valueOf(ex.getApplicationError());
       if (code != null && code != ErrorCode.UNSPECIFIED_ERROR) {
         throw new IllegalArgumentException(ex.getErrorDetail());
       } else {
         throw new ImagesServiceFailureException(ex.getErrorDetail());
       }
     }
     return response.getUrl();
  }

  /** {@inheritDoc} */
  @Override
  public String getServingUrl(BlobKey blobKey, int imageSize, boolean crop) {
    if (imageSize > SERVING_SIZES_LIMIT || imageSize < 0) {
      throw new IllegalArgumentException("Unsupported size: " + imageSize +
          ". Valid sizes must be between 0 and  " + SERVING_SIZES_LIMIT);
    }

    StringBuilder url = new StringBuilder(getServingUrl(blobKey));
    url.append("=s");
    url.append(imageSize);
    if (crop) {
      url.append("-c");
    }
    return url.toString();
  }

  static ImageData convertImageData(Image image) {
    ImageData.Builder builder = ImageData.newBuilder();
    BlobKey blobKey = image.getBlobKey();
    if (blobKey != null) {
      builder.setBlobKey(image.getBlobKey().getKeyString());
      builder.setContent(ByteString.EMPTY);
    } else {
      builder.setContent(ByteString.copyFrom(image.getImageData()));
    }
    return builder.build();
  }

  private ImagesTransformRequest.Builder generateImagesTransformRequest(
      Transform transform, Image image, OutputSettings settings)
      throws IllegalArgumentException{
    ImagesTransformRequest.Builder request =
      ImagesTransformRequest.newBuilder()
      .setImage(convertImageData(image))
      .setOutput(convertOutputSettings(settings));
    transform.apply(request);

    if (request.getTransformCount() > MAX_TRANSFORMS_PER_REQUEST) {
      throw new IllegalArgumentException(
          "A maximum of " + MAX_TRANSFORMS_PER_REQUEST + " basic transforms "
          + "can be requested in a single transform request");
    }
    return request;
  }

  private ImagesServicePb.OutputSettings convertOutputSettings(OutputSettings settings) {
    ImagesServicePb.OutputSettings.Builder pbSettings =
        ImagesServicePb.OutputSettings.newBuilder();
    switch(settings.getOutputEncoding()) {
      case PNG:
        pbSettings.setMimeType(MIME_TYPE.PNG);
        break;
      case JPEG:
        pbSettings.setMimeType(MIME_TYPE.JPEG);
        if (settings.hasQuality()) {
          pbSettings.setQuality(settings.getQuality());
        }
        break;
      case WEBP:
        pbSettings.setMimeType(MIME_TYPE.WEBP);
        if (settings.hasQuality()) {
          pbSettings.setQuality(settings.getQuality());
        }
        break;
      default:
        throw new IllegalArgumentException(
            "Invalid output encoding requested");
    }
    return pbSettings.build();
  }

  private RuntimeException convertApplicationException(ImagesTransformRequest.Builder request,
      ApiProxy.ApplicationException ex) {
    ErrorCode errorCode = ErrorCode.valueOf(ex.getApplicationError());
    if (errorCode != null && errorCode != ErrorCode.UNSPECIFIED_ERROR) {
      return new IllegalArgumentException(ex.getErrorDetail());
    } else {
      return new ImagesServiceFailureException(ex.getErrorDetail());
    }
  }
}
