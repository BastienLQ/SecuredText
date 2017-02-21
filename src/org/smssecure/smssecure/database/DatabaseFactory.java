/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smssecure.smssecure.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import org.smssecure.smssecure.DatabaseUpgradeActivity;
import org.smssecure.smssecure.contacts.ContactsDatabase;
import org.smssecure.smssecure.crypto.DecryptingPartInputStream;
import org.smssecure.smssecure.crypto.MasterCipher;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.MasterSecretUtil;
import org.smssecure.smssecure.notifications.MessageNotifier;
import org.smssecure.smssecure.util.Base64;
import org.smssecure.smssecure.util.Util;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidMessageException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import ws.com.google.android.mms.ContentType;

public class DatabaseFactory {
  private static final String TAG = DatabaseFactory.class.getSimpleName();

  private static final int INTRODUCED_IDENTITIES_VERSION                   = 2;
  private static final int INTRODUCED_INDEXES_VERSION                      = 3;
  private static final int INTRODUCED_DATE_SENT_VERSION                    = 4;
  private static final int INTRODUCED_DRAFTS_VERSION                       = 5;
  private static final int INTRODUCED_NEW_TYPES_VERSION                    = 6;
  private static final int INTRODUCED_MMS_BODY_VERSION                     = 7;
  private static final int INTRODUCED_MMS_FROM_VERSION                     = 8;
  private static final int INTRODUCED_TOFU_IDENTITY_VERSION                = 9;
  private static final int INTRODUCED_PUSH_DATABASE_VERSION                = 10;
  private static final int INTRODUCED_GROUP_DATABASE_VERSION               = 11;
  private static final int INTRODUCED_DELIVERY_RECEIPTS                    = 13;
  private static final int INTRODUCED_PART_DATA_SIZE_VERSION               = 14;
  private static final int INTRODUCED_THUMBNAILS_VERSION                   = 15;
  private static final int INTRODUCED_IDENTITY_COLUMN_VERSION              = 16;
  private static final int INTRODUCED_UNIQUE_PART_IDS_VERSION              = 17;
  private static final int INTRODUCED_RECIPIENT_PREFS_DB                   = 18;
  private static final int INTRODUCED_COLOR_PREFERENCE_VERSION             = 20;
  private static final int INTRODUCED_DELIVERY_DATE                        = 21;
  private static final int INTRODUCED_DB_OPTIMIZATIONS_VERSION             = 22;
  private static final int INTRODUCED_CONVERSATION_LIST_THUMBNAILS_VERSION = 23;
  private static final int INTRODUCED_ARCHIVE_VERSION                      = 24;
  private static final int INTRODUCED_CONVERSATION_LIST_STATUS_VERSION     = 25;
  private static final int MIGRATED_CONVERSATION_LIST_STATUS_VERSION       = 26;
  private static final int INTRODUCED_SUBSCRIPTION_ID_VERSION              = 28;
  private static final int INTRODUCED_LAST_SEEN                            = 29;
  private static final int INTRODUCED_NOTIFIED                             = 30;

  /*
   * Yes, INTRODUCED_XMPP_TRANSPORT > DATABASE_VERSION to allow database
   * downgrade when XMPP transport will be included in unstable branch.
   */
  private static final int INTRODUCED_XMPP_TRANSPORT                       = 31;
  private static final int DATABASE_VERSION                                = 30;

  private static final String DATABASE_NAME    = "messages.db";
  private static final Object lock             = new Object();

  private static DatabaseFactory instance;

  private DatabaseHelper databaseHelper;

  private final SmsDatabase sms;
  private final EncryptingSmsDatabase encryptingSms;
  private final MmsDatabase mms;
  private final AttachmentDatabase attachments;
  private final ImageDatabase image;
  private final ThreadDatabase thread;
  private final CanonicalAddressDatabase address;
  private final MmsAddressDatabase mmsAddress;
  private final MmsSmsDatabase mmsSmsDatabase;
  private final IdentityDatabase identityDatabase;
  private final DraftDatabase draftDatabase;
  private final RecipientPreferenceDatabase recipientPreferenceDatabase;
  private final ContactsDatabase contactsDatabase;

  public static DatabaseFactory getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
        instance = new DatabaseFactory(context.getApplicationContext());

      return instance;
    }
  }

  public static MmsSmsDatabase getMmsSmsDatabase(Context context) {
    return getInstance(context).mmsSmsDatabase;
  }

  public static ThreadDatabase getThreadDatabase(Context context) {
    return getInstance(context).thread;
  }

  public static SmsDatabase getSmsDatabase(Context context) {
    return getInstance(context).sms;
  }

  public static MmsDatabase getMmsDatabase(Context context) {
    return getInstance(context).mms;
  }

  public static CanonicalAddressDatabase getAddressDatabase(Context context) {
    return getInstance(context).address;
  }

  public static EncryptingSmsDatabase getEncryptingSmsDatabase(Context context) {
    return getInstance(context).encryptingSms;
  }

  public static AttachmentDatabase getAttachmentDatabase(Context context) {
    return getInstance(context).attachments;
  }

  public static ImageDatabase getImageDatabase(Context context) {
    return getInstance(context).image;
  }

  public static MmsAddressDatabase getMmsAddressDatabase(Context context) {
    return getInstance(context).mmsAddress;
  }

  public static IdentityDatabase getIdentityDatabase(Context context) {
    return getInstance(context).identityDatabase;
  }

  public static DraftDatabase getDraftDatabase(Context context) {
    return getInstance(context).draftDatabase;
  }

  public static RecipientPreferenceDatabase getRecipientPreferenceDatabase(Context context) {
    return getInstance(context).recipientPreferenceDatabase;
  }

  public static ContactsDatabase getContactsDatabase(Context context) {
    return getInstance(context).contactsDatabase;
  }

  private DatabaseFactory(Context context) {
    this.databaseHelper              = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
    this.sms                         = new SmsDatabase(context, databaseHelper);
    this.encryptingSms               = new EncryptingSmsDatabase(context, databaseHelper);
    this.mms                         = new MmsDatabase(context, databaseHelper);
    this.attachments                 = new AttachmentDatabase(context, databaseHelper);
    this.image                       = new ImageDatabase(context, databaseHelper);
    this.thread                      = new ThreadDatabase(context, databaseHelper);
    this.address                     = CanonicalAddressDatabase.getInstance(context);
    this.mmsAddress                  = new MmsAddressDatabase(context, databaseHelper);
    this.mmsSmsDatabase              = new MmsSmsDatabase(context, databaseHelper);
    this.identityDatabase            = new IdentityDatabase(context, databaseHelper);
    this.draftDatabase               = new DraftDatabase(context, databaseHelper);
    this.recipientPreferenceDatabase = new RecipientPreferenceDatabase(context, databaseHelper);
    this.contactsDatabase            = new ContactsDatabase(context);
  }

  public void reset(Context context) {
    DatabaseHelper old = this.databaseHelper;
    this.databaseHelper = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);

    this.sms.reset(databaseHelper);
    this.encryptingSms.reset(databaseHelper);
    this.mms.reset(databaseHelper);
    this.attachments.reset(databaseHelper);
    this.thread.reset(databaseHelper);
    this.mmsAddress.reset(databaseHelper);
    this.mmsSmsDatabase.reset(databaseHelper);
    this.identityDatabase.reset(databaseHelper);
    this.draftDatabase.reset(databaseHelper);
    this.recipientPreferenceDatabase.reset(databaseHelper);
    old.close();

    this.address.reset(context);
  }

  public void onApplicationLevelUpgrade(Context context, MasterSecret masterSecret, int fromVersion,
                                        DatabaseUpgradeActivity.DatabaseUpgradeListener listener)
  {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    // Do stuff here

    db.setTransactionSuccessful();
    db.endTransaction();

//    DecryptingQueue.schedulePendingDecrypts(context, masterSecret);
    MessageNotifier.updateNotification(context, masterSecret);
  }

  private static class DatabaseHelper extends SQLiteOpenHelper {

    public DatabaseHelper(Context context, String name, CursorFactory factory, int version) {
      super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(SmsDatabase.CREATE_TABLE);
      db.execSQL(MmsDatabase.CREATE_TABLE);
      db.execSQL(AttachmentDatabase.CREATE_TABLE);
      db.execSQL(ThreadDatabase.CREATE_TABLE);
      db.execSQL(MmsAddressDatabase.CREATE_TABLE);
      db.execSQL(IdentityDatabase.CREATE_TABLE);
      db.execSQL(DraftDatabase.CREATE_TABLE);
      db.execSQL(RecipientPreferenceDatabase.CREATE_TABLE);

      executeStatements(db, SmsDatabase.CREATE_INDEXS);
      executeStatements(db, MmsDatabase.CREATE_INDEXS);
      executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
      executeStatements(db, ThreadDatabase.CREATE_INDEXS);
      executeStatements(db, MmsAddressDatabase.CREATE_INDEXS);
      executeStatements(db, DraftDatabase.CREATE_INDEXS);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      db.beginTransaction();

      if (newVersion < INTRODUCED_XMPP_TRANSPORT) {}

      db.setTransactionSuccessful();
      db.endTransaction();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      db.beginTransaction();

      if (oldVersion < INTRODUCED_IDENTITIES_VERSION) {
        db.execSQL("CREATE TABLE identities (_id INTEGER PRIMARY KEY, key TEXT UNIQUE, name TEXT UNIQUE, mac TEXT);");
      }

      if (oldVersion < INTRODUCED_INDEXES_VERSION) {
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS sms_thread_id_index ON sms (thread_id);",
            "CREATE INDEX IF NOT EXISTS sms_read_index ON sms (read);",
            "CREATE INDEX IF NOT EXISTS sms_read_and_thread_id_index ON sms (read,thread_id);",
            "CREATE INDEX IF NOT EXISTS sms_type_index ON sms (type);"
        });
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS mms_thread_id_index ON mms (thread_id);",
            "CREATE INDEX IF NOT EXISTS mms_read_index ON mms (read);",
            "CREATE INDEX IF NOT EXISTS mms_read_and_thread_id_index ON mms (read,thread_id);",
            "CREATE INDEX IF NOT EXISTS mms_message_box_index ON mms (msg_box);"
        });
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS part_mms_id_index ON part (mid);"
        });
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS thread_recipient_ids_index ON thread (recipient_ids);",
        });
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS mms_addresses_mms_id_index ON mms_addresses (mms_id);",
        });
      }

      if (oldVersion < INTRODUCED_DATE_SENT_VERSION) {
        db.execSQL("ALTER TABLE sms ADD COLUMN date_sent INTEGER;");
        db.execSQL("UPDATE sms SET date_sent = date;");

        db.execSQL("ALTER TABLE mms ADD COLUMN date_received INTEGER;");
        db.execSQL("UPDATE mms SET date_received = date;");
      }

      if (oldVersion < INTRODUCED_DRAFTS_VERSION) {
        db.execSQL("CREATE TABLE drafts (_id INTEGER PRIMARY KEY, thread_id INTEGER, type TEXT, value TEXT);");
        executeStatements(db, new String[] {
            "CREATE INDEX IF NOT EXISTS draft_thread_index ON drafts (thread_id);",
        });
      }

      if (oldVersion < INTRODUCED_NEW_TYPES_VERSION) {
        String KEY_EXCHANGE             = "?SilenceKeyExchange";
        String SYMMETRIC_ENCRYPT        = "?SilenceLocalEncrypt";
        String ASYMMETRIC_ENCRYPT       = "?SilenceAsymmetricEncrypt";
        String ASYMMETRIC_LOCAL_ENCRYPT = "?SilenceAsymmetricLocalEncrypt";
        String PROCESSED_KEY_EXCHANGE   = "?SilenceKeyExchangd";
        String STALE_KEY_EXCHANGE       = "?SilenceKeyExchangs";

        // SMS Updates
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {20L+"", 1L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {21L+"", 43L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {22L+"", 4L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {23L+"", 2L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {24L+"", 5L+""});

        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(21L | 0x800000L)+"", 42L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(23L | 0x800000L)+"", 44L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(20L | 0x800000L)+"", 45L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(20L | 0x800000L | 0x10000000L)+"", 46L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(20L)+"", 47L+""});
        db.execSQL("UPDATE sms SET type = ? WHERE type = ?", new String[] {(20L | 0x800000L | 0x08000000L)+"", 48L+""});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(SYMMETRIC_ENCRYPT.length()+1)+"",
                                  0x80000000L+"",
                                  SYMMETRIC_ENCRYPT + "%"});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(ASYMMETRIC_LOCAL_ENCRYPT.length()+1)+"",
                                  0x40000000L+"",
                                  ASYMMETRIC_LOCAL_ENCRYPT + "%"});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(ASYMMETRIC_ENCRYPT.length()+1)+"",
                                 (0x800000L | 0x20000000L)+"",
                                 ASYMMETRIC_ENCRYPT + "%"});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(KEY_EXCHANGE.length()+1)+"",
                                  0x8000L+"",
                                  KEY_EXCHANGE + "%"});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(PROCESSED_KEY_EXCHANGE.length()+1)+"",
                                  (0x8000L | 0x2000L)+"",
                                  PROCESSED_KEY_EXCHANGE + "%"});

        db.execSQL("UPDATE sms SET body = substr(body, ?), type = type | ? WHERE body LIKE ?",
                   new String[] {(STALE_KEY_EXCHANGE.length()+1)+"",
                                 (0x8000L | 0x4000L)+"",
                                 STALE_KEY_EXCHANGE + "%"});

        // MMS Updates

        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(20L | 0x80000000L)+"", 1+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(23L | 0x80000000L)+"", 2+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(21L | 0x80000000L)+"", 4+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(24L | 0x80000000L)+"", 12+""});

        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(21L | 0x80000000L | 0x800000L) +"", 5+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(23L | 0x80000000L | 0x800000L) +"", 6+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(20L | 0x20000000L | 0x800000L) +"", 7+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(20L | 0x80000000L | 0x800000L) +"", 8+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(20L | 0x08000000L | 0x800000L) +"", 9+""});
        db.execSQL("UPDATE mms SET msg_box = ? WHERE msg_box = ?", new String[] {(20L | 0x10000000L | 0x800000L) +"", 10+""});

        // Thread Updates

        db.execSQL("ALTER TABLE thread ADD COLUMN snippet_type INTEGER;");

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(SYMMETRIC_ENCRYPT.length()+1)+"",
                                 0x80000000L+"",
                                 SYMMETRIC_ENCRYPT + "%"});

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(ASYMMETRIC_LOCAL_ENCRYPT.length()+1)+"",
                                  0x40000000L+"",
                                  ASYMMETRIC_LOCAL_ENCRYPT + "%"});

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(ASYMMETRIC_ENCRYPT.length()+1)+"",
                                 (0x800000L | 0x20000000L)+"",
                                 ASYMMETRIC_ENCRYPT + "%"});

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(KEY_EXCHANGE.length()+1)+"",
                       0x8000L+"",
                       KEY_EXCHANGE + "%"});

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(STALE_KEY_EXCHANGE.length()+1)+"",
                                 (0x8000L | 0x4000L)+"",
                                 STALE_KEY_EXCHANGE + "%"});

        db.execSQL("UPDATE thread SET snippet = substr(snippet, ?), " +
                   "snippet_type = ? WHERE snippet LIKE ?",
                   new String[] {(PROCESSED_KEY_EXCHANGE.length()+1)+"",
                                 (0x8000L | 0x2000L)+"",
                                 PROCESSED_KEY_EXCHANGE + "%"});
      }

      if (oldVersion < INTRODUCED_MMS_BODY_VERSION) {
        db.execSQL("ALTER TABLE mms ADD COLUMN body TEXT");
        db.execSQL("ALTER TABLE mms ADD COLUMN part_count INTEGER");
      }

      if (oldVersion < INTRODUCED_MMS_FROM_VERSION) {
        db.execSQL("ALTER TABLE mms ADD COLUMN address TEXT");

        Cursor cursor = db.query("mms_addresses", null, "type = ?", new String[] {0x89+""},
                                 null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          long mmsId     = cursor.getLong(cursor.getColumnIndexOrThrow("mms_id"));
          String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));

          if (!TextUtils.isEmpty(address)) {
            db.execSQL("UPDATE mms SET address = ? WHERE _id = ?", new String[]{address, mmsId+""});
          }
        }

        if (cursor != null)
          cursor.close();
      }

      if (oldVersion < INTRODUCED_TOFU_IDENTITY_VERSION) {
        db.execSQL("DROP TABLE identities");
        db.execSQL("CREATE TABLE identities (_id INTEGER PRIMARY KEY, recipient INTEGER UNIQUE, key TEXT, mac TEXT);");
      }

      if (oldVersion < INTRODUCED_PUSH_DATABASE_VERSION) {
        db.execSQL("ALTER TABLE part ADD COLUMN pending_push INTEGER;");
        db.execSQL("CREATE INDEX IF NOT EXISTS pending_push_index ON part (pending_push);");
      }

      if (oldVersion < INTRODUCED_GROUP_DATABASE_VERSION) {
        db.execSQL("ALTER TABLE sms ADD COLUMN address_device_id INTEGER DEFAULT 1;");
        db.execSQL("ALTER TABLE mms ADD COLUMN address_device_id INTEGER DEFAULT 1;");
      }

      if (oldVersion < INTRODUCED_DELIVERY_RECEIPTS) {
        db.execSQL("ALTER TABLE sms ADD COLUMN delivery_receipt_count INTEGER DEFAULT 0;");
        db.execSQL("ALTER TABLE mms ADD COLUMN delivery_receipt_count INTEGER DEFAULT 0;");
        db.execSQL("CREATE INDEX IF NOT EXISTS sms_date_sent_index ON sms (date_sent);");
        db.execSQL("CREATE INDEX IF NOT EXISTS mms_date_sent_index ON mms (date);");
      }

      if (oldVersion < INTRODUCED_PART_DATA_SIZE_VERSION) {
        db.execSQL("ALTER TABLE part ADD COLUMN data_size INTEGER DEFAULT 0;");
      }

      if (oldVersion < INTRODUCED_THUMBNAILS_VERSION) {
        db.execSQL("ALTER TABLE part ADD COLUMN thumbnail TEXT;");
        db.execSQL("ALTER TABLE part ADD COLUMN aspect_ratio REAL;");
      }

      if (oldVersion < INTRODUCED_IDENTITY_COLUMN_VERSION) {
        db.execSQL("ALTER TABLE sms ADD COLUMN mismatched_identities TEXT");
        db.execSQL("ALTER TABLE mms ADD COLUMN mismatched_identities TEXT");
        db.execSQL("ALTER TABLE mms ADD COLUMN network_failures TEXT");
      }

      if (oldVersion < INTRODUCED_UNIQUE_PART_IDS_VERSION) {
        db.execSQL("ALTER TABLE part ADD COLUMN unique_id INTEGER NOT NULL DEFAULT 0");
      }

      if (oldVersion < INTRODUCED_RECIPIENT_PREFS_DB) {
        db.execSQL("CREATE TABLE recipient_preferences " +
                   "(_id INTEGER PRIMARY KEY, recipient_ids TEXT UNIQUE, block INTEGER DEFAULT 0, " +
                   "notification TEXT DEFAULT NULL, vibrate INTEGER DEFAULT 0, mute_until INTEGER DEFAULT 0)");
      }

      if (oldVersion < INTRODUCED_COLOR_PREFERENCE_VERSION) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN color TEXT DEFAULT NULL");
      }

      if (oldVersion < INTRODUCED_DELIVERY_DATE) {
        db.execSQL("ALTER TABLE sms ADD COLUMN date_delivery_received INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN date_delivery_received INTEGER DEFAULT 0");
      }

      if (oldVersion < INTRODUCED_DB_OPTIMIZATIONS_VERSION) {
        db.execSQL("UPDATE mms SET date_received = (date_received * 1000), date = (date * 1000);");
        db.execSQL("CREATE INDEX IF NOT EXISTS sms_thread_date_index ON sms (thread_id, date);");
        db.execSQL("CREATE INDEX IF NOT EXISTS mms_thread_date_index ON mms (thread_id, date_received);");
      }

      if (oldVersion < INTRODUCED_CONVERSATION_LIST_THUMBNAILS_VERSION) {
        db.execSQL("ALTER TABLE thread ADD COLUMN snippet_uri TEXT DEFAULT NULL");
      }

      if (oldVersion < INTRODUCED_ARCHIVE_VERSION) {
        db.execSQL("ALTER TABLE thread ADD COLUMN archived INTEGER DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS archived_index ON thread (archived)");
      }

      if (oldVersion < INTRODUCED_CONVERSATION_LIST_STATUS_VERSION) {
        db.execSQL("ALTER TABLE thread ADD COLUMN status INTEGER DEFAULT -1");
      }

      if (oldVersion < MIGRATED_CONVERSATION_LIST_STATUS_VERSION) {
        List<Long> threads = new ArrayList<Long>();
        Cursor threadCursor = null;

        try {
          threadCursor = db.query("thread", new String[] {"_id"}, null, null, null, null, null);

          while (threadCursor != null && threadCursor.moveToNext()) {
            long threadId = threadCursor.getLong(threadCursor.getColumnIndexOrThrow("_id"));
            threads.add(threadId);
          }
        } catch (Exception e) {
          Log.w(TAG, e);
        } finally {
          if (threadCursor != null) threadCursor.close();
        }

        for (long thread : threads) {
          Cursor cursor = null;

          try {
            cursor = db.rawQuery("SELECT DISTINCT date AS date_received, status " +
            "FROM sms WHERE (thread_id = ?1) " +
            "UNION ALL SELECT DISTINCT date_received, -1 AS status " +
            "FROM mms WHERE (thread_id = ?1) " +
            "ORDER BY date_received DESC LIMIT 1", new String[]{thread + ""});

            if (cursor != null && cursor.moveToNext()) {
              int status = cursor.getInt(cursor.getColumnIndexOrThrow("status"));

              db.execSQL("UPDATE thread SET status = ? WHERE _id = ?",
              new String[]{status + "", thread + ""});
              Log.w(TAG, "Thread " + thread + " updated!");
            }
          } catch (Exception e) {
            Log.w(TAG, e);
          } finally {
            if (cursor != null) cursor.close();
          }
        }
      }

      if (oldVersion < INTRODUCED_SUBSCRIPTION_ID_VERSION) {
        db.execSQL("ALTER TABLE recipient_preferences ADD COLUMN default_subscription_id INTEGER DEFAULT -1");
        db.execSQL("ALTER TABLE sms ADD COLUMN subscription_id INTEGER DEFAULT -1");
        db.execSQL("ALTER TABLE mms ADD COLUMN subscription_id INTEGER DEFAULT -1");
      }

      if (oldVersion < INTRODUCED_LAST_SEEN) {
        db.execSQL("ALTER TABLE thread ADD COLUMN last_seen INTEGER DEFAULT 0");
      }

      if (oldVersion < INTRODUCED_NOTIFIED) {
        db.execSQL("ALTER TABLE sms ADD COLUMN notified INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE mms ADD COLUMN notified INTEGER DEFAULT 0");

        db.execSQL("DROP INDEX sms_read_and_thread_id_index");
        db.execSQL("CREATE INDEX IF NOT EXISTS sms_read_and_notified_and_thread_id_index ON sms(read,notified,thread_id)");

        db.execSQL("DROP INDEX mms_read_and_thread_id_index");
        db.execSQL("CREATE INDEX IF NOT EXISTS mms_read_and_notified_and_thread_id_index ON mms(read,notified,thread_id)");
      }

      db.setTransactionSuccessful();
      db.endTransaction();
    }

    private void executeStatements(SQLiteDatabase db, String[] statements) {
      for (String statement : statements)
        db.execSQL(statement);
    }

  }
}
