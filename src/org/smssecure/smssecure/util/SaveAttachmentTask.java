package org.smssecure.smssecure.util;

import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.smssecure.smssecure.R;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.mms.PartAuthority;
import org.whispersystems.textsecure.internal.push.TextSecureProtos;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;

import ws.com.google.android.mms.ContentType;

public class SaveAttachmentTask extends ProgressDialogAsyncTask<SaveAttachmentTask.Attachment, Void, Pair<Integer, String>> {
  private static final String TAG = SaveAttachmentTask.class.getSimpleName();

  private static final int SUCCESS              = 0;
  private static final int FAILURE              = 1;
  private static final int WRITE_ACCESS_FAILURE = 2;

  private final WeakReference<Context> contextReference;
  private final WeakReference<MasterSecret> masterSecretReference;

  public SaveAttachmentTask(Context context, MasterSecret masterSecret) {
    super(context, R.string.ConversationFragment_saving_attachment, R.string.ConversationFragment_saving_attachment_to_sd_card);
    this.contextReference      = new WeakReference<Context>(context);
    this.masterSecretReference = new WeakReference<MasterSecret>(masterSecret);
  }

  @Override
  protected Pair<Integer, String> doInBackground(SaveAttachmentTask.Attachment... attachments) {
    if (attachments == null || attachments.length != 1 || attachments[0] == null) {
      throw new AssertionError("must pass in exactly one attachment");
    }
    Attachment attachment = attachments[0];

    try {
      Context context           = contextReference.get();
      MasterSecret masterSecret = masterSecretReference.get();

      if (!Environment.getExternalStorageDirectory().canWrite()) {
        return new Pair<Integer, String>(WRITE_ACCESS_FAILURE, null);
      }

      if (context == null) {
        return new Pair<Integer, String>(WRITE_ACCESS_FAILURE, null);
      }

      File        mediaFile   = constructOutputFile(attachment);
      InputStream inputStream = PartAuthority.getPartStream(context, masterSecret, attachment.uri);

      if (inputStream == null) {
        return new Pair<Integer, String>(WRITE_ACCESS_FAILURE, null);
      }

      OutputStream outputStream = new FileOutputStream(mediaFile);
      Util.copy(inputStream, outputStream);

      MediaScannerConnection.scanFile(context, new String[]{mediaFile.getAbsolutePath()},
                                      new String[]{attachment.contentType}, null);

      return new Pair<Integer, String>(SUCCESS, mediaFile.getAbsolutePath());
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      return new Pair<Integer, String>(WRITE_ACCESS_FAILURE, null);
    }
  }

  @Override
  protected void onPostExecute(Pair<Integer,String> result) {
    super.onPostExecute(result);
    Context context = contextReference.get();
    if (context == null) return;

    switch (result.first) {
      case FAILURE:
        Toast.makeText(context, R.string.ConversationFragment_error_while_saving_attachment_to_sd_card,
            Toast.LENGTH_LONG).show();
        break;
      case SUCCESS:
        Toast.makeText(context, context.getResources().getText(R.string.ConversationFragment_success_exclamation) + (null != result.second ? " [" + result.second + "]" : ""),
            Toast.LENGTH_LONG).show();
        break;
      case WRITE_ACCESS_FAILURE:
        Toast.makeText(context, R.string.ConversationFragment_unable_to_write_to_sd_card_exclamation,
            Toast.LENGTH_LONG).show();
        break;
    }
  }

  private File constructOutputFile(Attachment attachement) throws IOException {
    File sdCard = Environment.getExternalStorageDirectory();
    File outputDirectory;

    if (attachement.contentType.startsWith("video/")) {
      outputDirectory = new File(sdCard.getAbsoluteFile() + File.separator + Environment.DIRECTORY_MOVIES);
    } else if (attachement.contentType.startsWith("audio/")) {
      outputDirectory = new File(sdCard.getAbsolutePath() + File.separator + Environment.DIRECTORY_MUSIC);
    } else if (attachement.contentType.startsWith("image/")) {
      outputDirectory = new File(sdCard.getAbsolutePath() + File.separator + Environment.DIRECTORY_PICTURES);
    } else {
      outputDirectory = new File(sdCard.getAbsolutePath() + File.separator + Environment.DIRECTORY_DOWNLOADS);
    }

    if (!outputDirectory.mkdirs()) Log.w(TAG, "mkdirs() returned false, attempting to continue");

    File file;
    int i = 0;
    if(ContentType.isDrmType(attachement.contentType) && attachement.fileName != null){
      String receivedFileName = new File(attachement.fileName).getName();
      file = new File(outputDirectory, receivedFileName);
      while(file.exists()){
        file = new File(outputDirectory, receivedFileName + (++i));
      }
    }else {
      MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
      String extension = mimeTypeMap.getExtensionFromMimeType(attachement.contentType);
      SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
      String base = "smssecure-" + dateFormatter.format(attachement.date);

      if (extension == null)
        extension = "attach";


      file = new File(outputDirectory, base + "." + extension);
      while (file.exists()) {
        file = new File(outputDirectory, base + "-" + (++i) + "." + extension);
      }
    }
    return file;
  }

  public static class Attachment {
    public Uri    uri;
    public String contentType;
    public long   date;
    public String    fileName;

    public Attachment(Uri uri, String contentType, long date, String fileName) {
      if (uri == null || contentType == null || date < 0) {
        throw new AssertionError("uri, content type, and date must all be specified");
      }
      this.uri         = uri;
      this.contentType = contentType;
      this.date        = date;
      this.fileName     = fileName;
    }
  }

  public static void showWarningDialog(Context context, OnClickListener onAcceptListener) {
    AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(context);
    builder.setTitle(R.string.ConversationFragment_save_to_sd_card);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationFragment_this_media_has_been_stored_in_an_encrypted_database_warning);
    builder.setPositiveButton(R.string.yes, onAcceptListener);
    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }
}

