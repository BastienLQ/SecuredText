package org.smssecure.smssecure.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import org.smssecure.smssecure.TransportOption;
import org.smssecure.smssecure.TransportOptions;
import org.smssecure.smssecure.TransportOptions.OnTransportChangedListener;
import org.smssecure.smssecure.TransportOptionsPopup;
import org.smssecure.smssecure.util.ViewUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;

public class SendButton extends ImageButton
    implements TransportOptions.OnTransportChangedListener,
               TransportOptionsPopup.SelectedListener,
               View.OnLongClickListener
{

  private final TransportOptions transportOptions;

  private Optional<TransportOptionsPopup> transportOptionsPopup = Optional.absent();

  @SuppressWarnings("unused")
  public SendButton(Context context) {
    super(context);
    this.transportOptions = initializeTransportOptions(false);
    ViewUtil.mirrorIfRtl(this, getContext());
  }

  @SuppressWarnings("unused")
  public SendButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.transportOptions = initializeTransportOptions(false);
    ViewUtil.mirrorIfRtl(this, getContext());
  }

  @SuppressWarnings("unused")
  public SendButton(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    this.transportOptions = initializeTransportOptions(false);
    ViewUtil.mirrorIfRtl(this, getContext());
  }

  private TransportOptions initializeTransportOptions(boolean media) {
    TransportOptions transportOptions = new TransportOptions(getContext(), media);
    transportOptions.addOnTransportChangedListener(this);

    setOnLongClickListener(this);

    return transportOptions;
  }

  private TransportOptionsPopup getTransportOptionsPopup() {
    if (!transportOptionsPopup.isPresent()) {
      transportOptionsPopup = Optional.of(new TransportOptionsPopup(getContext(), this, this));
    }
    return transportOptionsPopup.get();
  }

  public boolean isManualSelection() {
    return transportOptions.isManualSelection();
  }

  public void addOnTransportChangedListener(OnTransportChangedListener listener) {
    transportOptions.addOnTransportChangedListener(listener);
  }

  public TransportOption getSelectedTransport() {
    return transportOptions.getSelectedTransport();
  }

  public void resetAvailableTransports(boolean isMediaMessage) {
    transportOptions.reset(isMediaMessage);
  }

  public void disableTransport(TransportOption.Type type) {
    transportOptions.disableTransport(type);
  }

  public void disableTransport(TransportOption.Type type, int subscriptionId) {
    transportOptions.disableTransport(type, subscriptionId);
  }

  public void disableTransport(TransportOption.Type type, List<Integer> subscriptionIds) {
    for (int subscriptionId : subscriptionIds) {
      transportOptions.disableTransport(type, subscriptionId);
    }
  }

  public void setDefaultTransport(TransportOption.Type type) {
    transportOptions.setDefaultTransport(type);
  }

  public void setDefaultSubscriptionId(Optional<Integer> subscriptionId) {
    transportOptions.setDefaultSubscriptionId(subscriptionId);
  }

  @Override
  public void onSelected(TransportOption option) {
    transportOptions.setSelectedTransport(option);
    getTransportOptionsPopup().dismiss();
  }

  @Override
  public void onChange(TransportOption newTransport, boolean isManualSelection) {
    setImageResource(newTransport.getDrawable());
    setContentDescription(newTransport.getDescription());
  }

  @Override
  public boolean onLongClick(View v) {
    if (transportOptions.getEnabledTransports().size() > 1) {
      getTransportOptionsPopup().display(transportOptions.getEnabledTransports());
      return true;
    }

    return false;
  }
}
