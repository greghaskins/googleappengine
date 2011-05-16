// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.images;

/**
 * A transform that will resize an image to fit within a bounding box.
 *
 */
final class Resize extends Transform {

  private static final long serialVersionUID = -889209644904728094L;

  private final int width;
  private final int height;

  /**
   * Creates a transform that will resize an image to fit within a rectangle
   * with the given dimensions.
   * @param width width of the bounding box
   * @param height height of the bounding box
   * @throws IllegalArgumentException If {@code width} or {@code height} are
   * negative or greater than {@code MAX_RESIZE_DIMENSIONS} or if both
   * {@code width} and {@code height} are 0.
   */
  Resize(int width, int height) {
    if (width > ImagesService.MAX_RESIZE_DIMENSIONS
        || height > ImagesService.MAX_RESIZE_DIMENSIONS) {
      throw new IllegalArgumentException("width and height must be <= "
                                         + ImagesService.MAX_RESIZE_DIMENSIONS);
    }
    if (width < 0 || height < 0) {
      throw new IllegalArgumentException("width and height must be >= 0");
    }
    if (width == 0 && height == 0) {
      throw new IllegalArgumentException("width and height must not both be == 0");
    }
    this.width = width;
    this.height = height;
  }

  /** {@inheritDoc} */
  @Override
  void apply(ImagesServicePb.ImagesTransformRequest.Builder request) {
    request.addTransform(
        ImagesServicePb.Transform.newBuilder()
        .setWidth(width)
        .setHeight(height));
  }
}
