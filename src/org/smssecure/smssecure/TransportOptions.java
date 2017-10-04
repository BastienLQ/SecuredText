package org.smssecure.smssecure;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.smssecure.smssecure.util.CharacterCalculator;
import org.smssecure.smssecure.util.DummyCharacterCalculator;
import org.smssecure.smssecure.util.MmsCharacterCalculator;
import org.smssecure.smssecure.util.SmsCharacterCalculator;
import org.smssecure.smssecure.util.EncryptedSmsCharacterCalculator;
import org.smssecure.smssecure.util.dualsim.SubscriptionInfoCompat;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

import static org.smssecure.smssecure.TransportOption.Type;

public class TransportOptions {

  private static final String TAG = TransportOptions.class.getSimpleName();

  private final List<OnTransportChangedListener> listeners = new LinkedList<>();
  private final Context                          context;
  private final List<TransportOption>            enabledTransports;

  private Type                      defaultTransportType  = Type.INSECURE_SMS;
  private Optional<Integer>         defaultSubscriptionId = SubscriptionManagerCompat.getDefaultMessagingSubscriptionId();
  private Optional<TransportOption> selectedOption        = Optional.absent();

  public TransportOptions(Context context, boolean media) {
    this.context           = context;
    this.enabledTransports = initializeAvailableTransports(media);
  }

  public void reset(boolean media) {
    List<TransportOption> transportOptions = initializeAvailableTransports(media);

    this.enabledTransports.clear();
    this.enabledTransports.addAll(transportOptions);

    if (selectedOption.isPresent() && !isEnabled(selectedOption.get())) {
      setSelectedTransport(null);
    } else {
      this.defaultTransportType = Type.INSECURE_SMS;
      this.defaultSubscriptionId = SubscriptionManagerCompat.getDefaultMessagingSubscriptionId();

      notifyTransportChangeListeners();
    }
  }

  public void setDefaultTransport(Type type) {
    this.defaultTransportType = type;

    if (!selectedOption.isPresent()) {
      notifyTransportChangeListeners();
    }
  }

  public void setDefaultSubscriptionId(Optional<Integer> subscriptionId) {
    if(subscriptionId.isPresent() && subscriptionId.get() >= 0) {
      this.defaultSubscriptionId = subscriptionId;
    }

    if (!selectedOption.isPresent()) {
      notifyTransportChangeListeners();
    }
  }

  public void setSelectedTransport(@Nullable  TransportOption transportOption) {
    this.selectedOption = Optional.fromNullable(transportOption);
    notifyTransportChangeListeners();
  }

  public boolean isManualSelection() {
    return this.selectedOption.isPresent();
  }

  public @NonNull TransportOption getSelectedTransport() {
    if (selectedOption.isPresent()) return selectedOption.get();

    if (defaultSubscriptionId.isPresent()) {
      for (TransportOption transportOption : enabledTransports) {
        if (transportOption.getType() == defaultTransportType &&
            (int)defaultSubscriptionId.get() == transportOption.getSimSubscriptionId().or(-1))
        {
          return transportOption;
        }
      }
    }

    for (TransportOption transportOption : enabledTransports) {
      if (transportOption.getType() == defaultTransportType) {
        return transportOption;
      }
    }

    return getDefaultTransportOption();
  }

  public void disableTransport(Type type) {
    List<TransportOption> options = find(type);

    for (TransportOption option : options) {
      enabledTransports.remove(option);
      if (selectedOption.isPresent() && selectedOption.get().getType() == type) {
        setSelectedTransport(null);
      }
    }
  }

  public void disableTransport(Type type, int subscriptionId) {
    List<TransportOption> options = find(type);

    for (TransportOption option : options) {
      if (option.getSimSubscriptionId().or(-1) == subscriptionId) enabledTransports.remove(option);
      if (selectedOption.isPresent() && selectedOption.get().getType() == type && selectedOption.get().getSimSubscriptionId().or(-1) == subscriptionId) {
        setSelectedTransport(null);
      }
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
      results.addAll(getTransportOptionsForSimCards(Type.INSECURE_SMS, R.drawable.ic_send_insecure_white_24dp,
                                                    context.getResources().getColor(R.color.grey_600),
                                                    context.getString(R.string.ConversationActivity_transport_insecure_mms),
                                                    context.getString(R.string.conversation_activity__type_message_mms_insecure),
                                                    new MmsCharacterCalculator()));
      results.addAll(getTransportOptionsForSimCards(Type.SECURE_SMS, R.drawable.ic_send_secure_white_24dp,
                                                    context.getResources().getColor(R.color.silence_primary),
                                                    context.getString(R.string.ConversationActivity_transport_secure_mms),
                                                    context.getString(R.string.conversation_activity__type_message_mms_secure),
                                                    new MmsCharacterCalculator()));
    } else {
      results.addAll(getTransportOptionsForSimCards(Type.INSECURE_SMS, R.drawable.ic_send_insecure_white_24dp,
                                                    context.getResources().getColor(R.color.grey_600),
                                                    context.getString(R.string.ConversationActivity_transport_insecure_sms),
                                                    context.getString(R.string.conversation_activity__type_message_sms_insecure),
                                                    new SmsCharacterCalculator()));
      results.addAll(getTransportOptionsForSimCards(Type.SECURE_SMS, R.drawable.ic_send_secure_white_24dp,
                                                    context.getResources().getColor(R.color.silence_primary),
                                                    context.getString(R.string.ConversationActivity_transport_secure_sms),
                                                    context.getString(R.string.conversation_activity__type_message_sms_secure),
                                                    new EncryptedSmsCharacterCalculator()));
    }

    return results;
  }

  private @NonNull List<TransportOption> getTransportOptionsForSimCards(@NonNull Type type,
                                                                        @DrawableRes int drawable,
                                                                        int backgroundColor,
                                                                        @NonNull String text,
                                                                        @NonNull String composeHint,
                                                                        @NonNull CharacterCalculator characterCalculator)
  {
    List<TransportOption>        results             = new LinkedList<>();
    SubscriptionManagerCompat    subscriptionManager = SubscriptionManagerCompat.from(context);
    List<SubscriptionInfoCompat> subscriptions       = subscriptionManager.getActiveSubscriptionInfoList();

    for (SubscriptionInfoCompat subscriptionInfo : subscriptions) {
      results.add(new TransportOption(type,
                                      drawable,
                                      backgroundColor,
                                      text,
                                      composeHint,
                                      characterCalculator,
                                      Optional.of(subscriptionInfo.getDisplayName()),
                                      Optional.of(subscriptionInfo.getSubscriptionId())));
    }

    return results;
  }

  private void notifyTransportChangeListeners() {
    for (OnTransportChangedListener listener : listeners) {
      listener.onChange(getSelectedTransport(), selectedOption.isPresent());
    }
  }

  private List<TransportOption> find(Type type) {
    List<TransportOption> options = new LinkedList<>();
    for (TransportOption option : enabledTransports) {
      if (option.isType(type)) {
        options.add(option);
      }
    }
    return options;
  }

  private boolean isEnabled(TransportOption transportOption) {
    for (TransportOption option : enabledTransports) {
      if (option.equals(transportOption)) return true;
    }

    return false;
  }

  public interface OnTransportChangedListener {
    public void onChange(TransportOption newTransport, boolean manuallySelected);
  }

  private TransportOption getDefaultTransportOption() {
    return new TransportOption(Type.DISABLED,
                               R.drawable.ic_send_insecure_white_24dp,
                               context.getResources().getColor(R.color.grey_600),
                               context.getString(R.string.TransportOptions_sms_disabled),
                               context.getString(R.string.TransportOptions_no_sim_card_found),
                               new DummyCharacterCalculator(),
                               Optional.of((CharSequence) ""),
                               Optional.of(-1));
  }
}
