package org.smssecure.smssecure.sms;

public class MultipartSmsTransportMessageFragments {

  private static final long VALID_TIME = 60 * 60 * 1000; // 1 Hour

  private final byte[][] fragments;
  private final long initializedTime;

  public MultipartSmsTransportMessageFragments(int count) {
    this.fragments       = new byte[count][];
    this.initializedTime = System.currentTimeMillis();
  }

  public void add(MultipartSmsTransportMessage fragment) {
    this.fragments[fragment.getMultipartIndex()] = fragment.getStrippedMessage();
  }

  public int getSize() {
    return this.fragments.length;
  }

  public boolean isExpired() {
    return (System.currentTimeMillis() - initializedTime) >= VALID_TIME;
  }

  public boolean isComplete() {
    for (byte[] fragment : fragments) if (fragment == null) return false;

    return true;
  }

  public byte[] getJoined() {
    int totalMessageLength = 0;

    for (byte[] fragment1 : fragments) {
      totalMessageLength += fragment1.length;
    }

    byte[] totalMessage    = new byte[totalMessageLength];
    int totalMessageOffset = 0;

    for (byte[] fragment : fragments) {
      System.arraycopy(fragment, 0, totalMessage, totalMessageOffset, fragment.length);
      totalMessageOffset += fragment.length;
    }

    return totalMessage;
  }

  @Override
  public String toString() {
    return String.format("[Size: %d, Initialized: %d, Exipired: %s, Complete: %s]",
                         fragments.length, initializedTime, isExpired()+"", isComplete()+"");
  }
}
