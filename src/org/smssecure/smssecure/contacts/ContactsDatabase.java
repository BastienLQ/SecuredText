/**
 * Copyright (C) 2013 Open Whisper Systems
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
package org.smssecure.smssecure.contacts;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;

import org.smssecure.smssecure.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Database to supply all types of contacts that Silence needs to know about
 *
 * @author Jake McGinty
 */
public class ContactsDatabase {

  private static final String TAG = ContactsDatabase.class.getSimpleName();

  public static final String ID_COLUMN           = "_id";
  public static final String NAME_COLUMN         = "name";
  public static final String NUMBER_COLUMN       = "number";
  public static final String NUMBER_TYPE_COLUMN  = "number_type";
  public static final String LABEL_COLUMN        = "label";
  public static final String CONTACT_TYPE_COLUMN = "contact_type";

  public static final int NORMAL_TYPE = 0;
  public static final int PUSH_TYPE   = 1;
  public static final int NEW_TYPE    = 2;

  private final Context context;

  public ContactsDatabase(Context context) {
    this.context  = context;
  }

  public @NonNull Cursor querySystemContacts(String filter) {
    Uri uri;

    if (!TextUtils.isEmpty(filter)) {
      uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(filter));
    } else {
      uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      uri = uri.buildUpon().appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true").build();
    }

    String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone._ID,
                                       ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                       ContactsContract.CommonDataKinds.Phone.NUMBER,
                                       ContactsContract.CommonDataKinds.Phone.TYPE,
                                       ContactsContract.CommonDataKinds.Phone.LABEL};

    String sort = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

    Map<String, String> projectionMap = new HashMap<String, String>() {{
      put(ID_COLUMN, ContactsContract.CommonDataKinds.Phone._ID);
      put(NAME_COLUMN, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
      put(NUMBER_COLUMN, ContactsContract.CommonDataKinds.Phone.NUMBER);
      put(NUMBER_TYPE_COLUMN, ContactsContract.CommonDataKinds.Phone.TYPE);
      put(LABEL_COLUMN, ContactsContract.CommonDataKinds.Phone.LABEL);
    }};

    Cursor cursor = context.getContentResolver().query(uri, projection,
                                                       ContactsContract.Data.SYNC2 + " IS NULL OR " +
                                                       ContactsContract.Data.SYNC2 + " != ?",
                                                       new String[] {"__TS"},
                                                       sort);

    return new ProjectionMappingCursor(cursor, projectionMap,
                                       new Pair<String, Object>(CONTACT_TYPE_COLUMN, NORMAL_TYPE));
  }

  public @NonNull Cursor querySilenceContacts(String filter) {
    String[] projection = new String[] {ContactsContract.Data._ID,
                                        ContactsContract.Contacts.DISPLAY_NAME,
                                        ContactsContract.Data.DATA1};

    String  sort = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

    Map<String, String> projectionMap = new HashMap<String, String>(){{
      put(ID_COLUMN, ContactsContract.Data._ID);
      put(NAME_COLUMN, ContactsContract.Contacts.DISPLAY_NAME);
      put(NUMBER_COLUMN, ContactsContract.Data.DATA1);
    }};

    Cursor cursor;

    if (TextUtils.isEmpty(filter)) {
      cursor = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                                                  projection,
                                                  ContactsContract.Data.MIMETYPE + " = ?",
                                                  new String[] {"vnd.android.cursor.item/vnd.org.smssecure.smssecure.contact"},
                                                  sort);
    } else {
      cursor = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                                                  projection,
                                                  ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?",
                                                  new String[] {"vnd.android.cursor.item/vnd.org.smssecure.smssecure.contact",
                                                                "%" + filter + "%"},
                                                  sort);
    }

    return new ProjectionMappingCursor(cursor, projectionMap,
                                       new Pair<String, Object>(LABEL_COLUMN, "Silence"),
                                       new Pair<String, Object>(NUMBER_TYPE_COLUMN, 0),
                                       new Pair<String, Object>(CONTACT_TYPE_COLUMN, PUSH_TYPE));

  }

  public Cursor getNewNumberCursor(String filter) {
    MatrixCursor newNumberCursor = new MatrixCursor(new String[] {ID_COLUMN, NAME_COLUMN, NUMBER_COLUMN, NUMBER_TYPE_COLUMN, LABEL_COLUMN, CONTACT_TYPE_COLUMN}, 1);
    newNumberCursor.addRow(new Object[]{-1L, context.getString(R.string.contact_selection_list__unknown_contact),
                                        filter, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                                        "\u21e2", NEW_TYPE});

    return newNumberCursor;
  }

  private static class ProjectionMappingCursor extends CursorWrapper {

    private final Map<String, String>    projectionMap;
    private final Pair<String, Object>[] extras;

    @SafeVarargs
    public ProjectionMappingCursor(Cursor cursor,
                                   Map<String, String> projectionMap,
                                   Pair<String, Object>... extras)
    {
      super(cursor);
      this.projectionMap = projectionMap;
      this.extras        = extras;
    }

    @Override
    public int getColumnCount() {
      return super.getColumnCount() + extras.length;
    }

    @Override
    public int getColumnIndex(String columnName) {
      for (int i=0;i<extras.length;i++) {
        if (extras[i].first.equals(columnName)) {
          return super.getColumnCount() + i;
        }
      }

      return super.getColumnIndex(projectionMap.get(columnName));
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
      int index = getColumnIndex(columnName);

      if (index == -1) throw new IllegalArgumentException("Bad column name!");
      else             return index;
    }

    @Override
    public String getColumnName(int columnIndex) {
      int baseColumnCount = super.getColumnCount();

      if (columnIndex >= baseColumnCount) {
        int offset = columnIndex - baseColumnCount;
        return extras[offset].first;
      }

      return getReverseProjection(super.getColumnName(columnIndex));
    }

    @Override
    public String[] getColumnNames() {
      String[] names    = super.getColumnNames();
      String[] allNames = new String[names.length + extras.length];

      for (int i=0;i<names.length;i++) {
        allNames[i] = getReverseProjection(names[i]);
      }

      for (int i=0;i<extras.length;i++) {
        allNames[names.length + i] = extras[i].first;
      }

      return allNames;
    }

    @Override
    public int getInt(int columnIndex) {
      if (columnIndex >= super.getColumnCount()) {
        int offset = columnIndex - super.getColumnCount();
        return (Integer)extras[offset].second;
      }

      return super.getInt(columnIndex);
    }

    @Override
    public String getString(int columnIndex) {
      if (columnIndex >= super.getColumnCount()) {
        int offset = columnIndex - super.getColumnCount();
        return (String)extras[offset].second;
      }

      return super.getString(columnIndex);
    }


    private @Nullable String getReverseProjection(String columnName) {
      for (Map.Entry<String, String> entry : projectionMap.entrySet()) {
        if (entry.getValue().equals(columnName)) {
          return entry.getKey();
        }
      }

      return null;
    }
  }
}
