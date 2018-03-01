package org.smssecure.smssecure.components.subsampling;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.mms.PartAuthority;
import org.smssecure.smssecure.service.KeyCachingService;

import java.io.InputStream;

public class AttachmentBitmapDecoder implements ImageDecoder{

  @Override
  public Bitmap decode(Context context, Uri uri) throws Exception {
    if (!PartAuthority.isLocalUri(uri)) {
      return new SkiaImageDecoder().decode(context, uri);
    }

    MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);

    if (masterSecret == null) {
      throw new IllegalStateException("Can't decode without secret");
    }

    InputStream inputStream = PartAuthority.getAttachmentStream(context, masterSecret, uri);

    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inPreferredConfig = Bitmap.Config.ARGB_8888;

      Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);

      if (bitmap == null) {
        throw new RuntimeException("Skia image region decoder returned null bitmap - image format may not be supported");
      }

      return bitmap;
    } finally {
      if (inputStream != null) inputStream.close();
    }
  }


}
