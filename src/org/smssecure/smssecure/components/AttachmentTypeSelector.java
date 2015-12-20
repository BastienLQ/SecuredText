package org.smssecure.smssecure.components;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import org.smssecure.smssecure.R;
import org.smssecure.smssecure.util.ViewUtil;

public class AttachmentTypeSelector extends PopupWindow {

  public static final int ADD_IMAGE         = 1;
  public static final int ADD_VIDEO         = 2;
  public static final int ADD_SOUND         = 3;
  public static final int ADD_CONTACT_INFO  = 4;
  public static final int TAKE_PHOTO        = 5;

  private static final int ANIMATION_DURATION = 300;

  private static final String TAG = AttachmentTypeSelector.class.getSimpleName();

  private final @NonNull ImageView   cameraButton;
  private final @NonNull ImageView   imageButton;
  private final @NonNull ImageView   audioButton;
  private final @NonNull ImageView   videoButton;
  private final @NonNull ImageView   contactButton;

  private @Nullable View                      currentAnchor;
  private @Nullable AttachmentClickedListener listener;

  public AttachmentTypeSelector(@NonNull Context context, @Nullable AttachmentClickedListener listener) {
    super(context);

    LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    LinearLayout   layout   = (LinearLayout) inflater.inflate(R.layout.attachment_type_selector, null, true);

    this.listener      = listener;
    this.cameraButton   = ViewUtil.findById(layout, R.id.camera_button);
    this.imageButton   = ViewUtil.findById(layout, R.id.gallery_button);
    this.audioButton   = ViewUtil.findById(layout, R.id.audio_button);
    this.videoButton   = ViewUtil.findById(layout, R.id.video_button);
    this.contactButton = ViewUtil.findById(layout, R.id.contact_button);

    this.cameraButton.setOnClickListener(new PropagatingClickListener(TAKE_PHOTO));
    this.imageButton.setOnClickListener(new PropagatingClickListener(ADD_IMAGE));
    this.audioButton.setOnClickListener(new PropagatingClickListener(ADD_SOUND));
    this.videoButton.setOnClickListener(new PropagatingClickListener(ADD_VIDEO));
    this.contactButton.setOnClickListener(new PropagatingClickListener(ADD_CONTACT_INFO));
    layout.setOnClickListener(new CloseClickListener());

    setContentView(layout);
    setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
    setHeight(LinearLayout.LayoutParams.FILL_PARENT);
    setBackgroundDrawable(new BitmapDrawable());
    setAnimationStyle(0);
    setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    setFocusable(true);
    setTouchable(true);
  }

  public void show(@NonNull Activity activity, final @NonNull View anchor) {
    this.currentAnchor = anchor;

    int screenHeight = activity.getWindowManager().getDefaultDisplay().getHeight();
    showAtLocation(anchor, Gravity.NO_GRAVITY, 0, screenHeight - getHeight());

    getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        getContentView().getViewTreeObserver().removeGlobalOnLayoutListener(this);
        animateWindowIn();
      }
    });

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      animateButtonIn(cameraButton, ANIMATION_DURATION / 2);
      animateButtonIn(imageButton, ANIMATION_DURATION / 3);
      animateButtonIn(audioButton, ANIMATION_DURATION / 4);
      animateButtonIn(videoButton, ANIMATION_DURATION / 5);
      animateButtonIn(contactButton, 0);
    }
  }

  @Override
  public void dismiss() {
    animateWindowOut();
  }

  public void setListener(@Nullable AttachmentClickedListener listener) {
    this.listener = listener;
  }

  private void animateButtonIn(View button, int delay) {
    AnimationSet animation = new AnimationSet(true);
    Animation scale = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f,
                                         Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.0f);

    animation.addAnimation(scale);
    animation.setInterpolator(new OvershootInterpolator(1));
    animation.setDuration(ANIMATION_DURATION);
    animation.setStartOffset(delay);
    button.startAnimation(animation);
  }

  private void animateWindowIn() {
    Animation animation = new AlphaAnimation(0, 1);
    animation.setInterpolator(new DecelerateInterpolator());
    animation.setDuration(ANIMATION_DURATION);

    getContentView().startAnimation(animation);
  }

  private void animateWindowOut() {
    Animation animation = new AlphaAnimation(1, 0);
    animation.setInterpolator(new AccelerateInterpolator());
    animation.setDuration(ANIMATION_DURATION);
    animation.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {
      }

      @Override
      public void onAnimationEnd(Animation animation) {
        AttachmentTypeSelector.super.dismiss();
      }

      @Override
      public void onAnimationRepeat(Animation animation) {
      }
    });

    getContentView().startAnimation(animation);
  }

  private Pair<Integer, Integer> getClickOrigin(@Nullable View anchor, @NonNull View contentView) {
    if (anchor == null) return new Pair<>(0, 0);

    final int[] anchorCoordinates = new int[2];
    anchor.getLocationOnScreen(anchorCoordinates);
    anchorCoordinates[0] += anchor.getWidth() / 2;
    anchorCoordinates[1] += anchor.getHeight() / 2;

    final int[] contentCoordinates = new int[2];
    contentView.getLocationOnScreen(contentCoordinates);

    int x = anchorCoordinates[0] - contentCoordinates[0];
    int y = anchorCoordinates[1] - contentCoordinates[1];

    return new Pair<>(x, y);
  }

  private class PropagatingClickListener implements View.OnClickListener {

    private final int type;

    private PropagatingClickListener(int type) {
      this.type = type;
    }

    @Override
    public void onClick(View v) {
      animateWindowOut();

      if (listener != null) listener.onClick(type);
    }

  }

  private class CloseClickListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      dismiss();
    }
  }

  public interface AttachmentClickedListener {
    public void onClick(int type);
  }

}
