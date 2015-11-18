package org.smssecure.smssecure;

import android.content.Context;
import android.content.res.TypedArray;

import org.smssecure.smssecure.util.MmsCharacterCalculator;
import org.smssecure.smssecure.util.SmsCharacterCalculator;
import org.smssecure.smssecure.util.EncryptedSmsCharacterCalculator;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

import static org.smssecure.smssecure.TransportOption.Type;

public class TransportOptions {

  private static final String TAG = TransportOptions.class.getSimpleName();

  private final List<OnTransportChangedListener> listeners = new LinkedList<>();
  private final Context                          context;
  private final List<TransportOption>            enabledTransports;

  private Type    selectedType;
  private boolean manuallySelected;

  public TransportOptions(Context context, boolean media) {
    this.context           = context;
    this.enabledTransports = initializeAvailableTransports(media);

    setDefaultTransport(Type.INSECURE_SMS);
  }

  public void reset(boolean media) {
    List<TransportOption> transportOptions = initializeAvailableTransports(media);
    this.enabledTransports.clear();
    this.enabledTransports.addAll(transportOptions);

    if (!find(selectedType).isPresent()) {
      this.manuallySelected = false;
      setTransport(Type.INSECURE_SMS);
    } else {
      notifyTransportChangeListeners();
    }
  }

  public void setDefaultTransport(Type type) {
    if (!this.manuallySelected) {
      setTransport(type);
    }
  }

  public void setSelectedTransport(Type type) {
    this.manuallySelected= true;
    setTransport(type);
  }

  public boolean isManualSelection() {
    return manuallySelected;
  }

  public TransportOption getSelectedTransport() {
    Optional<TransportOption> option =  find(selectedType);

    if (option.isPresent()) return option.get();
    else                    throw new AssertionError("Selected type isn't present!");
  }

  public void disableTransport(Type type) {
    Optional<TransportOption> option = find(type);
    if (option.isPresent()) {
      enabledTransports.remove(option.get());
    }
  }

  public List<TransportOption> getEnabledTransports() {
    return enabledTransports;
  }

  public void addOnTransportChangedListener(OnTransportChangedListener listener) {
    this.listeners.add(listener);
  }

  private List<TransportOption> initializeAvailableTransports(boolean isMediaMessage) {
    List<TransportOption> results = new LinkedList<>();

    if (isMediaMessage) {
      results.add(new TransportOption(Type.INSECURE_SMS, R.drawable.ic_send_insecure_white_24dp,
                                      context.getResources().getColor(R.color.grey_600),
                                      context.getString(R.string.ConversationActivity_transport_insecure_mms),
                                      context.getString(R.string.conversation_activity__type_message_mms_insecure),
                                      new MmsCharacterCalculator()));
      results.add(new TransportOption(Type.SECURE_SMS, R.drawable.ic_send_secure_white_24dp,
                                      context.getResources().getColor(R.color.smssecure_primary),
                                      context.getString(R.string.ConversationActivity_transport_secure_mms),
                                      context.getString(R.string.conversation_activity__type_message_mms_secure),
                                      new MmsCharacterCalculator()));
    } else {
      results.add(new TransportOption(Type.INSECURE_SMS, R.drawable.ic_send_insecure_white_24dp,
                                      context.getResources().getColor(R.color.grey_600),
                                      context.getString(R.string.ConversationActivity_transport_insecure_sms),
                                      context.getString(R.string.conversation_activity__type_message_sms_insecure),
                                      new SmsCharacterCalculator()));
      results.add(new TransportOption(Type.SECURE_SMS, R.drawable.ic_send_secure_white_24dp,
                                      context.getResources().getColor(R.color.smssecure_primary),
                                      context.getString(R.string.ConversationActivity_transport_secure_sms),
                                      context.getString(R.string.conversation_activity__type_message_sms_secure),
                                      new EncryptedSmsCharacterCalculator()));
    }

    return results;
  }

  private void setTransport(Type type) {
    this.selectedType = type;

    notifyTransportChangeListeners();
  }

  private void notifyTransportChangeListeners() {
    for (OnTransportChangedListener listener : listeners) {
      listener.onChange(getSelectedTransport());
    }
  }

  private Optional<TransportOption> find(Type type) {
    for (TransportOption option : enabledTransports) {
      if (option.isType(type)) {
        return Optional.of(option);
      }
    }

    return Optional.absent();
  }

  public interface OnTransportChangedListener {
    public void onChange(TransportOption newTransport);
  }
}
