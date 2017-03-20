/**
 * Copyright (C) 2011 Whisper Systems
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
package org.smssecure.smssecure.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.util.Log;

import org.smssecure.smssecure.util.Base64;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;

import java.io.IOException;

/**
 * Utility class for working with identity keys.
 *
 * @author Moxie Marlinspike
 */

public class IdentityKeyUtil {
  private final static String TAG = IdentityKeyUtil.class.getSimpleName();

  private static final String IDENTITY_PUBLIC_KEY_DJB_PREF  = "pref_identity_public_curve25519";
  private static final String IDENTITY_PRIVATE_KEY_DJB_PREF = "pref_identity_private_curve25519";

  public static boolean hasIdentityKey(Context context, int subscriptionId) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);

    return
        preferences.contains(getIdentityPublicKeyDjbPref(subscriptionId)) &&
        preferences.contains(getIdentityPrivateKeyDjbPref(subscriptionId));
  }

  public static IdentityKey getIdentityKey(Context context, int subscriptionId) {
    if (!hasIdentityKey(context, subscriptionId)) return null;

    try {
      byte[] publicKeyBytes = Base64.decode(retrieve(context, getIdentityPublicKeyDjbPref(subscriptionId)));
      return new IdentityKey(publicKeyBytes, 0);
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      return null;
    } catch (InvalidKeyException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  public static IdentityKeyPair getIdentityKeyPair(Context context,
                                                   MasterSecret masterSecret,
                                                   int subscriptionId)
  {
    if (!hasIdentityKey(context, subscriptionId))
      return null;

    try {
      MasterCipher masterCipher = new MasterCipher(masterSecret);
      IdentityKey  publicKey    = getIdentityKey(context, subscriptionId);
      ECPrivateKey privateKey   = masterCipher.decryptKey(Base64.decode(retrieve(context, getIdentityPrivateKeyDjbPref(subscriptionId))));

      return new IdentityKeyPair(publicKey, privateKey);
    } catch (IOException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public static void generateIdentityKeys(Context context, MasterSecret masterSecret, int subscriptionId) {
    Log.w(TAG, "Generating identity keys for subscription ID " + subscriptionId);
    ECKeyPair    djbKeyPair     = Curve.generateKeyPair();

    MasterCipher masterCipher   = new MasterCipher(masterSecret);
    IdentityKey  djbIdentityKey = new IdentityKey(djbKeyPair.getPublicKey());
    byte[]       djbPrivateKey  = masterCipher.encryptKey(djbKeyPair.getPrivateKey());

    save(context, getIdentityPublicKeyDjbPref(subscriptionId), Base64.encodeBytes(djbIdentityKey.serialize()));
    save(context, getIdentityPrivateKeyDjbPref(subscriptionId), Base64.encodeBytes(djbPrivateKey));
  }

  public static boolean hasCurve25519IdentityKeys(Context context, int subscriptionId) {
    return
        retrieve(context, getIdentityPublicKeyDjbPref(subscriptionId)) != null &&
        retrieve(context, getIdentityPrivateKeyDjbPref(subscriptionId)) != null;
  }

  public static void generateCurve25519IdentityKeys(Context context, MasterSecret masterSecret, int subscriptionId) {
    MasterCipher masterCipher    = new MasterCipher(masterSecret);
    ECKeyPair    djbKeyPair      = Curve.generateKeyPair();
    IdentityKey  djbIdentityKey  = new IdentityKey(djbKeyPair.getPublicKey());
    byte[]       djbPrivateKey   = masterCipher.encryptKey(djbKeyPair.getPrivateKey());

    save(context, getIdentityPublicKeyDjbPref(subscriptionId), Base64.encodeBytes(djbIdentityKey.serialize()));
    save(context, getIdentityPrivateKeyDjbPref(subscriptionId), Base64.encodeBytes(djbPrivateKey));
  }

  public static String retrieve(Context context, String key) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);
    return preferences.getString(key, null);
  }

  public static void save(Context context, String key, String value) {
    SharedPreferences preferences   = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);
    Editor preferencesEditor        = preferences.edit();

    preferencesEditor.putString(key, value);
    if (!preferencesEditor.commit()) throw new AssertionError("failed to save identity key/value to shared preferences");
  }

  public static void remove(Context context, String key) {
    SharedPreferences preferences   = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);
    Editor preferencesEditor        = preferences.edit();

    preferencesEditor.remove(key);
    if (!preferencesEditor.commit()) throw new AssertionError("failed to remove identity key/value to shared preferences");
  }

  public static String getIdentityPublicKeyDjbPref(int subscriptionId) {
    if (Build.VERSION.SDK_INT >= 22 && subscriptionId != -1) {
      return IDENTITY_PUBLIC_KEY_DJB_PREF + "_" + subscriptionId;
    } else {
      return IDENTITY_PUBLIC_KEY_DJB_PREF;
    }
  }

  public static String getIdentityPrivateKeyDjbPref(int subscriptionId) {
    if (Build.VERSION.SDK_INT >= 22 && subscriptionId != -1) {
      return IDENTITY_PRIVATE_KEY_DJB_PREF + "_" + subscriptionId;
    } else {
      return IDENTITY_PRIVATE_KEY_DJB_PREF;
    }
  }
}
