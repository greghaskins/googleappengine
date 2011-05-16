// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development.agent.impl;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;

/**
 * Strips local variable information from class files. Versions of ASM <= 3.0
 * appear to trip up on (possibly buggy) local var definitions in some
 * cases (for example, from JRuby JIT).
 *
 */
public class StripLocalVariablesVisitor extends ClassAdapter {

  public StripLocalVariablesVisitor(final ClassVisitor classVisitor) {
    super(classVisitor);
  }

  @Override
  public void visit(int version, int access, String name, String signature,
      String superName, String[] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc,
      final String signature, final String[] exceptions) {
    MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

    if (mv == null) {
      return null;
    }

    return new MethodTranslator(mv);
  }

  private class MethodTranslator extends MethodAdapter {

    MethodTranslator(MethodVisitor parentVisitor) {
      super(parentVisitor);
    }

    @Override
    public void visitLocalVariable(String name, String desc, String type,
        Label start, Label end, int index) {
    }
  }
}
