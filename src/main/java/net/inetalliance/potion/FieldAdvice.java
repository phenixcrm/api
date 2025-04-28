package net.inetalliance.potion;

import net.bytebuddy.asm.Advice;

public class FieldAdvice {

  @Advice.OnMethodEnter
  public static void enter(@Advice.FieldValue(value = "$$read", readOnly = false) Boolean read,
      @Advice.This() Object obj) {

    if (read == null) {
      read = Locator.read(obj);
    }
  }
}
