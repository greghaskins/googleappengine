// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.google.appengine.demos.mandelbrot;

import java.io.IOException;

/**
 * An {@code ImageWriter} is capable of converting a {@link}
 * PixelSource} to a specific stream of bytes in some specific image
 * format.
 *
 * @author schwardo@google.com (Don Schwarz)
 */
public interface ImageWriter {
  /**
   * Returns the content type for the image format that is used.
   */
  String getContentType();

  /**
   * Generate the image produced by {@code source}.
   */
  byte[] generateImage(PixelSource source) throws IOException;
}
