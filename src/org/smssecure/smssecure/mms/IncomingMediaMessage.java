package org.smssecure.smssecure.mms;

import org.smssecure.smssecure.attachments.Attachment;
import org.smssecure.smssecure.database.MmsAddresses;

import java.util.LinkedList;
import java.util.List;

public class IncomingMediaMessage {

  private final String  from;
  private final String  body;
  private final String  groupId;
  private final boolean push;
  private final long    sentTimeMillis;
  private final int     subscriptionId;

  private final List<String>     to          = new LinkedList<>();
  private final List<String>     cc          = new LinkedList<>();
  private final List<Attachment> attachments = new LinkedList<>();

  public IncomingMediaMessage(String from, List<String> to, List<String> cc,
                              String body, long sentTimeMillis,
                              List<Attachment> attachments, int subscriptionId)
  {
    this.from           = from;
    this.sentTimeMillis = sentTimeMillis;
    this.body           = body;
    this.groupId        = null;
    this.push           = false;
    this.subscriptionId = subscriptionId;

    this.to.addAll(to);
    this.cc.addAll(cc);
    this.attachments.addAll(attachments);
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public String getBody() {
    return body;
  }

  public MmsAddresses getAddresses() {
    return new MmsAddresses(from, to, cc, new LinkedList<String>());
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public String getGroupId() {
    return groupId;
  }

  public boolean isPushMessage() {
    return push;
  }

  public long getSentTimeMillis() {
    return sentTimeMillis;
  }

  public boolean isGroupMessage() {
    return groupId != null || to.size() > 1 || cc.size() > 0;
  }
}
