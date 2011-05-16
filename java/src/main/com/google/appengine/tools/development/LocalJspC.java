// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import org.apache.jasper.JspC;
import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.Localizer;

public class LocalJspC {
  public static void main (String[] args) throws JasperException {
    if (args.length == 0) {
      System.out.println(Localizer.getMessage("jspc.usage"));
    } else {
      JspC jspc = new JspC();
      jspc.setArgs(args);
      jspc.setCompiler("extJavac");
      jspc.setAddWebXmlMappings(true);
      jspc.execute();
    }
  }
}
