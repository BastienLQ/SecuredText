package org.smssecure.smssecure.mms;

import android.app.Application;
import android.content.Context;

import org.smssecure.smssecure.ApplicationContext;
import org.smssecure.smssecure.util.SMSSecurePreferences;
import org.smssecure.smssecure.util.Util;


public class MmsMediaConstraints extends MediaConstraints {
  private static final int MAX_IMAGE_DIMEN_LOWMEM = 768;
  private static final int MAX_IMAGE_DIMEN        = 1024;
  public static int FALLBACK_MAX_MESSAGE_SIZE       = 220 * 1024;

  public static int getMaxMmsPref(){
    int kB = SMSSecurePreferences.getMmmMaxSize(ApplicationContext.get());
    if(kB > 0)
      return(1024*kB);
    else return FALLBACK_MAX_MESSAGE_SIZE;
  }

  @Override
  public int getImageMaxWidth(Context context) {
    boolean limitedImageSize = SMSSecurePreferences.getLimitedMmsImageDimensions(ApplicationContext.get());
    if(limitedImageSize)
      return Util.isLowMemory(context) ? MAX_IMAGE_DIMEN_LOWMEM : MAX_IMAGE_DIMEN;
    else
      return Integer.MAX_VALUE;
  }

  @Override
  public int getImageMaxHeight(Context context) {
    return getImageMaxWidth(context);
  }

  @Override
  public int getImageMaxSize() {
    return getMaxMmsPref();
  }

  @Override
  public int getGifMaxSize() {
    return getMaxMmsPref();
  }

  @Override
  public int getVideoMaxSize() {
    return getMaxMmsPref();
  }

  @Override
  public int getAudioMaxSize() {
    return getMaxMmsPref();
  }

}
