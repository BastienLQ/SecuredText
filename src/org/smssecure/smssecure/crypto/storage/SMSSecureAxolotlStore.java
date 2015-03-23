package org.smssecure.smssecure.crypto.storage;

import android.content.Context;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.securesms.libaxolotl.AxolotlAddress;
import org.securesms.libaxolotl.IdentityKey;
import org.securesms.libaxolotl.IdentityKeyPair;
import org.securesms.libaxolotl.InvalidKeyIdException;
import org.securesms.libaxolotl.state.AxolotlStore;
import org.securesms.libaxolotl.state.IdentityKeyStore;
import org.securesms.libaxolotl.state.PreKeyRecord;
import org.securesms.libaxolotl.state.PreKeyStore;
import org.securesms.libaxolotl.state.SessionRecord;
import org.securesms.libaxolotl.state.SessionStore;
import org.securesms.libaxolotl.state.SignedPreKeyRecord;
import org.securesms.libaxolotl.state.SignedPreKeyStore;

import java.util.List;

public class SMSSecureAxolotlStore implements AxolotlStore {

  private final PreKeyStore       preKeyStore;
  private final SignedPreKeyStore signedPreKeyStore;
  private final IdentityKeyStore  identityKeyStore;
  private final SessionStore      sessionStore;

  public SMSSecureAxolotlStore(Context context, MasterSecret masterSecret) {
    this.preKeyStore       = new SMSSecurePreKeyStore(context, masterSecret);
    this.signedPreKeyStore = new SMSSecurePreKeyStore(context, masterSecret);
    this.identityKeyStore  = new SMSSecureIdentityKeyStore(context, masterSecret);
    this.sessionStore      = new SMSSecureSessionStore(context, masterSecret);
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return identityKeyStore.getIdentityKeyPair();
  }

  @Override
  public int getLocalRegistrationId() {
    return identityKeyStore.getLocalRegistrationId();
  }

  @Override
  public void saveIdentity(String number, IdentityKey identityKey) {
    identityKeyStore.saveIdentity(number, identityKey);
  }

  @Override
  public boolean isTrustedIdentity(String number, IdentityKey identityKey) {
    return identityKeyStore.isTrustedIdentity(number, identityKey);
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    return preKeyStore.loadPreKey(preKeyId);
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    preKeyStore.storePreKey(preKeyId, record);
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    return preKeyStore.containsPreKey(preKeyId);
  }

  @Override
  public void removePreKey(int preKeyId) {
    preKeyStore.removePreKey(preKeyId);
  }

  @Override
  public SessionRecord loadSession(AxolotlAddress axolotlAddress) {
    return sessionStore.loadSession(axolotlAddress);
  }

  @Override
  public List<Integer> getSubDeviceSessions(String number) {
    return sessionStore.getSubDeviceSessions(number);
  }

  @Override
  public void storeSession(AxolotlAddress axolotlAddress, SessionRecord record) {
    sessionStore.storeSession(axolotlAddress, record);
  }

  @Override
  public boolean containsSession(AxolotlAddress axolotlAddress) {
    return sessionStore.containsSession(axolotlAddress);
  }

  @Override
  public void deleteSession(AxolotlAddress axolotlAddress) {
    sessionStore.deleteSession(axolotlAddress);
  }

  @Override
  public void deleteAllSessions(String number) {
    sessionStore.deleteAllSessions(number);
  }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    return signedPreKeyStore.loadSignedPreKey(signedPreKeyId);
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    return signedPreKeyStore.loadSignedPreKeys();
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record);
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    return signedPreKeyStore.containsSignedPreKey(signedPreKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    signedPreKeyStore.removeSignedPreKey(signedPreKeyId);
  }
}
