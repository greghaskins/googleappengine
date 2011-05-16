// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.api.images;

import com.google.appengine.api.blobstore.BlobKey;

import java.util.Collection;

/**
 * Factory for creating an {@link ImagesService}, {@link Image}s and
 * {@link Transform}s.
 *
 */
public final class ImagesServiceFactory {

  /**
   * Creates an implementation of the ImagesService.
   * @return an images service
   */
  public static ImagesService getImagesService() {
    return new ImagesServiceImpl();
  }

  /**
   * Creates an image from the provided {@code imageData}.
   * @param imageData image data to store in the image
   * @return an Image containing the supplied image data
   * @throws IllegalArgumentException If {@code imageData} is null or empty.
   */
  public static Image makeImage(byte[] imageData) {
    return new ImageImpl(imageData);
  }

  /**
   * Create an image backed by the specified {@code blobKey}.  Note
   * that the returned {@link Image} object can be used with all
   * {@link ImagesService} methods, but most of the methods on the
   * Image itself will currently throw {@link
   * UnsupportedOperationException}.
   *
   * @param blobKey referencing the image
   * @return an Image that references the specified blob data
   */
  public static Image makeImageFromBlob(BlobKey blobKey) {
    return new ImageImpl(blobKey);
  }

  /**
   * Creates a transform that will resize an image to fit within a box with
   * width {@code width} and height {@code height}.
   * @param width width of the bounding box
   * @param height height of the bounding box
   * @return a resize transform
   * @throws IllegalArgumentException If {@code width} or {@code height} are
   * negative or greater than {@code MAX_RESIZE_DIMENSIONS} or if both
   * {@code width} and {@code height} are 0.
   */
  public static Transform makeResize(int width, int height) {
    return new Resize(width, height);
  }

  /**
   * Creates a transform that will crop an image to fit within the bounding
   * box specified.
   *
   * The arguments define the top left and bottom right corners of the
   * bounding box used to crop the image as a percentage of the total image
   * size. Each argument should be in the range 0.0 to 1.0 inclusive.
   * @param leftX X coordinate of the top left corner of the bounding box
   * @param topY Y coordinate of the top left corner of the bounding box
   * @param rightX X coordinate of the bottom right corner of the bounding box
   * @param bottomY Y coordinate of the bottom right corner of the bounding box
   * @return a crop transform
   * @throws IllegalArgumentException If any of the arguments are outside the
   * range 0.0 to 1.0 or if {@code leftX >= rightX} or {@code topY >= bottomY}.
   */
  public static Transform makeCrop(float leftX, float topY, float rightX,
                                   float bottomY) {
    return new Crop(leftX, topY, rightX, bottomY);
  }

  /**
   * Creates a transform that will crop an image to fit within the bounding
   * box specified.
   *
   * The arguments define the top left and bottom right corners of the
   * bounding box used to crop the image as a percentage of the total image
   * size. Each argument should be in the range 0.0 to 1.0 inclusive.
   * @param leftX X coordinate of the top left corner of the bounding box
   * @param topY Y coordinate of the top left corner of the bounding box
   * @param rightX X coordinate of the bottom right corner of the bounding box
   * @param bottomY Y coordinate of the bottom right corner of the bounding box
   * @return a crop transform
   * @throws IllegalArgumentException If any of the arguments are outside the
   * range 0.0 to 1.0 or if {@code leftX >= rightX} or {@code topY >= bottomY}.
   */
  public static Transform makeCrop(double leftX, double topY,
                                   double rightX, double bottomY) {
    return makeCrop((float) leftX, (float) topY, (float) rightX, (float) bottomY);
  }

  /**
   * Creates a transform that will vertically flip an image.
   * @return a vertical flip transform
   */
  public static Transform makeVerticalFlip() {
    return new VerticalFlip();
  }

  /**
   * Creates a transform that will horizontally flip an image.
   * @return a horizontal flip transform
   */
  public static Transform makeHorizontalFlip() {
    return new HorizontalFlip();
  }

  /**
   * Creates a transform that rotates an image by {@code degrees} degrees
   * clockwise.
   *
   * @param degrees The number of degrees by which to rotate. Must be a
   *                multiple of 90.
   * @return a rotation transform
   * @throws IllegalArgumentException If {@code degrees} is not divisible by 90
   */
  public static Transform makeRotate(int degrees) {
    return new Rotate(degrees);
  }

  /**
   * Creates a transform that automatically adjust contrast and color levels.
   *
   * This is similar to the "I'm Feeling Lucky" button in Picasa.
   * @return an ImFeelingLucky autolevel transform
   */
  public static Transform makeImFeelingLucky() {
    return new ImFeelingLucky();
  }

  /**
   * Creates a composite transform that can represent multiple transforms
   * applied in series.
   *
   * @param transforms Transforms for this composite transform to apply.
   * @return a composite transform containing the provided transforms
   */
  public static CompositeTransform makeCompositeTransform(
      Collection<Transform> transforms) {
    return new CompositeTransform(transforms);
  }

  /**
   * Creates a composite transform that can represent multiple transforms
   * applied in series.
   * @return an empty composite transform
   */
  public static CompositeTransform makeCompositeTransform() {
    return new CompositeTransform();
  }

  /**
   * Creates an image composition operation.
   * @param image The image to be composited.
   * @param xOffset Offset in the x axis from the anchor point.
   * @param yOffset Offset in the y axis from the anchor point.
   * @param opacity Opacity to be used for the image in range [0.0, 1.0].
   * @param anchor Anchor position from the enum {@link Composite.Anchor}.
   * The anchor position of the image is aligned with the anchor position of
   * the canvas and then the offsets are applied.
   * @return A composition operation.
   * @throws IllegalArgumentException If {@code image} is null or empty,
   * {@code xOffset} or {@code yOffset} is outside the range
   * [-{@value
   * com.google.appengine.api.images.ImagesService#MAX_RESIZE_DIMENSIONS},
   * {@value
   * com.google.appengine.api.images.ImagesService#MAX_RESIZE_DIMENSIONS}],
   * {@code opacity} is outside the range [0.0, 1.0] or {@code anchor} is null.
   */
  public static Composite makeComposite(Image image, int xOffset, int yOffset,
                                        float opacity,
                                        Composite.Anchor anchor) {
    return new CompositeImpl(image, xOffset, yOffset, opacity, anchor);
  }

  private ImagesServiceFactory() {
  }
}
