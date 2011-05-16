// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development.agent.impl;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * Visits method and field instructions, checking for references to
 * blacklisted classes.
 *
 */
public class ObjectAccessVisitor extends ClassAdapter {

  private static final String REJECT = "reject";

  private static final String REJECT_DESCRIPTOR = "(Ljava/lang/String;)V";

  public ObjectAccessVisitor(final ClassVisitor classVisitor) {
    super(classVisitor);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature,
      String[] exceptions) {
    MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
    return (mv == null) ? null : new MethodTranslator(mv, access, name, desc);
  }

  private class MethodTranslator extends GeneratorAdapter {
    MethodTranslator(MethodVisitor methodVisitor, int access, String name, String desc) {
      super(methodVisitor, access, name, desc);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
      maybeReject(owner);
      super.visitFieldInsn(opcode, owner, name, desc);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
      maybeReject(owner);
      super.visitMethodInsn(opcode, owner, name, desc);
    }

    private void maybeReject(String klass) {
      if (BlackList.getBlackList().contains(klass)) {
        super.push(klass.replace('/', '.'));
        super.visitMethodInsn(Opcodes.INVOKESTATIC, AgentImpl.AGENT_RUNTIME,
            REJECT, REJECT_DESCRIPTOR);
      }
    }
  }
}
