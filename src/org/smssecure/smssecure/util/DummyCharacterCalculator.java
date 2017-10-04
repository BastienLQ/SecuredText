package org.smssecure.smssecure.util;

public class DummyCharacterCalculator extends CharacterCalculator {

  @Override
  public CharacterState calculateCharacters(String messageBody) {
    return new CharacterState(0, 0, 0);
  }
}
