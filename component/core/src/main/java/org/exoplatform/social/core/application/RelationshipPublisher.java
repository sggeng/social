/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.core.application;

import java.util.HashMap;
import java.util.Map;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.manager.RelationshipManager;
import org.exoplatform.social.core.processor.I18NActivityUtils;
import org.exoplatform.social.core.relationship.RelationshipEvent;
import org.exoplatform.social.core.relationship.RelationshipListenerPlugin;
import org.exoplatform.social.core.relationship.model.Relationship;
import org.exoplatform.social.core.storage.api.IdentityStorage;

/**
 * Publish a status update in activity streams of 2 confirmed relations.
 * @author <a href="mailto:patrice.lamarque@exoplatform.com">Patrice Lamarque</a>
 * @version $Revision$
 */
public class RelationshipPublisher extends RelationshipListenerPlugin {

  public static enum TitleId {
    CONNECTION_REQUESTED,
    CONNECTION_CONFIRMED
  }

  public static final String SENDER_PARAM = "SENDER";
  public static final String RECEIVER_PARAM = "RECEIVER";
  public static final String RELATIONSHIP_UUID_PARAM = "RELATIONSHIP_UUID";
  public static final String RELATIONSHIP_ACTIVITY_TYPE = "exosocial:relationship";
  public static final String NUMBER_OF_CONNECTIONS = "NUMBER_OF_CONNECTIONS";
  public static final String USER_ACTIVITIES_FOR_RELATIONSHIP = "USER_ACTIVITIES_FOR_RELATIONSHIP";
  public static final String USER_COMMENTS_ACTIVITY_FOR_RELATIONSHIP = "USER_COMMENTS_ACTIVITY_FOR_RELATIONSHIP";
  public static final String USER_DISPLAY_NAME_PARAM = "USER_DISPLAY_NAME_PARAM";

  private static final Log LOG = ExoLogger.getLogger(RelationshipPublisher.class);
  private ActivityManager  activityManager;

  private IdentityManager identityManager;

  public RelationshipPublisher(InitParams params, ActivityManager activityManager, IdentityManager identityManager) {
    this.activityManager = activityManager;
    this.identityManager = identityManager;
  }

  private ExoSocialActivity createNewActivity(Identity identity, int nbOfConnections) {
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setType(USER_ACTIVITIES_FOR_RELATIONSHIP);
    activity.setTitle("I'm now connected with "+nbOfConnections+" users");
    activity.setUserId(identity.getId());
    I18NActivityUtils.addResourceKey(activity, "user_relations", ""+nbOfConnections);
    return activity;
  }
  
  private ExoSocialActivity createNewComment(Identity userIdenity, String fullName) {
    ExoSocialActivityImpl comment = new ExoSocialActivityImpl();
    comment.setType(USER_COMMENTS_ACTIVITY_FOR_RELATIONSHIP);
    String message = "I'm now connected with "+fullName;
    comment.setTitle(message);
    comment.setUserId(userIdenity.getId());
    I18NActivityUtils.addResourceKey(comment, "user_relation_confirmed", fullName);
    return comment;
  }

  /**
   * Publish an activity on both user's stream to indicate their new connection
   */
  public void confirmed(RelationshipEvent event) {
    Relationship relationship = event.getPayload();
    try {
      Identity sender = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, relationship.getSender().getRemoteId(), true); 
      Identity receiver = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, relationship.getReceiver().getRemoteId(), true);
      
      String activityIdSender = getIdentityStorage().getProfileActivityId(sender.getProfile(), Profile.AttachedActivityType.RELATIONSHIP);
      int nbOfSenderConnections = getRelationShipManager().getConnections(sender).getSize();
      String activityIdReceiver = getIdentityStorage().getProfileActivityId(receiver.getProfile(), Profile.AttachedActivityType.RELATIONSHIP);
      int nbOfReceiverConnections = getRelationShipManager().getConnections(receiver).getSize();
      
      String fullNameReceiver = receiver.getProfile().getFullName();
      ExoSocialActivity senderComment = createNewComment(sender, fullNameReceiver);
      String fullNameSender = sender.getProfile().getFullName();
      ExoSocialActivity receiverComment = createNewComment(receiver, fullNameSender);
      Map<String,String> params = new HashMap<String,String>();
      
      if (activityIdSender != null) {
        ExoSocialActivity activitySender = activityManager.getActivity(activityIdSender);
        if (activitySender != null) {
          activitySender.setTitle("I'm now connected with "+nbOfSenderConnections+" users");
          activitySender.setTemplateParams(params);
          activitySender.setTitleId(null);
          I18NActivityUtils.addResourceKey(activitySender, "user_relations", ""+nbOfSenderConnections);
          activityManager.updateActivity(activitySender);
          activityManager.saveComment(activitySender, senderComment);
        } else {
          activityIdSender = null;
        }
      }
      if (activityIdSender == null) {
        ExoSocialActivity newActivitySender = createNewActivity(sender, nbOfSenderConnections);
        activityManager.saveActivityNoReturn(sender, newActivitySender);
        getIdentityStorage().updateProfileActivityId(sender, newActivitySender.getId(), Profile.AttachedActivityType.RELATIONSHIP);
        activityManager.saveComment(newActivitySender, senderComment);
      }
      
      if (activityIdReceiver != null) {
        ExoSocialActivity activityReceiver = activityManager.getActivity(activityIdReceiver);
        if (activityReceiver != null) {
          activityReceiver.setTitle("I'm now connected with "+nbOfReceiverConnections+" users");
          activityReceiver.setTemplateParams(params);
          activityReceiver.setTitleId(null);
          I18NActivityUtils.addResourceKey(activityReceiver, "user_relations", ""+nbOfReceiverConnections);
          activityManager.updateActivity(activityReceiver);
          activityManager.saveComment(activityReceiver, receiverComment);
        } else {
          activityIdSender = null;
        }
      }
      if (activityIdReceiver == null) {
        ExoSocialActivity newActivityReceiver = createNewActivity(receiver, nbOfReceiverConnections);
        activityManager.saveActivityNoReturn(receiver, newActivityReceiver);
        getIdentityStorage().updateProfileActivityId(receiver, newActivityReceiver.getId(), Profile.AttachedActivityType.RELATIONSHIP);
        activityManager.saveComment(newActivityReceiver, receiverComment);
      }
      
    } catch (Exception e) {
      LOG.warn("Failed to publish event " + event + ": " + e.getMessage());
    }
  }

  private void reloadIfNeeded(Identity identity) throws Exception {
    if (identity.getId() == null || identity.getProfile().getFullName().length() == 0) {
      identity = identityManager.getIdentity(identity.getGlobalId().toString(), true);
    }
  }

  @Override
  public void ignored(RelationshipEvent event) {
    ;// void on purpose
  }

  @Override
  public void removed(RelationshipEvent event) {
    ;// void on purpose
  }

  public void denied(RelationshipEvent event) {
    ;// void on purpose

  }

  /**
   * Publish an activity on invited member to show the invitation to connect
   */
  public void requested(RelationshipEvent event) {
    Relationship relationship = event.getPayload();
    try {
      Map<String,String> params = this.getParams(relationship);
      ExoSocialActivity activity1 = new ExoSocialActivityImpl(relationship.getSender().getId(), 
                                                              RELATIONSHIP_ACTIVITY_TYPE,
                                                              "@" + relationship.getSender().getRemoteId() + " has invited @" 
                                                              + relationship.getReceiver().getRemoteId() + " to connect", null);
      activity1.setTitleId(TitleId.CONNECTION_REQUESTED.toString());
      activity1.setTemplateParams(params);
      activityManager.saveActivityNoReturn(relationship.getSender(), activity1);

      //TODO hoatle a quick fix for activities gadget to allow deleting this activity
      ExoSocialActivity activity2 = new ExoSocialActivityImpl(relationship.getReceiver().getId(), 
                                                              RELATIONSHIP_ACTIVITY_TYPE,
                                                              "@" + relationship.getSender().getRemoteId() + " has invited @"
                                                              + relationship.getReceiver().getRemoteId() + " to connect", null);
      activity2.setTitleId(TitleId.CONNECTION_REQUESTED.toString());
      activity2.setTemplateParams(params);
      activityManager.saveActivityNoReturn(relationship.getReceiver(), activity2);

    } catch (Exception e) {
      LOG.warn("Failed to publish event " + event + ": " + e.getMessage());
    }
  }

  /**
   * Gets params (sender, receiver, relationship uuid) from a provided relationship.
   * 
   * @param relationship
   * @return
   * @since 1.2.0-GA
   */
  private Map<String,String> getParams(Relationship relationship) throws Exception {
    Identity sender = relationship.getSender();
    reloadIfNeeded(sender);
    Identity receiver = relationship.getReceiver();
    reloadIfNeeded(receiver);
    String senderRemoteId = sender.getRemoteId();
    String receiverRemoteId = receiver.getRemoteId();
    Map<String,String> params = new HashMap<String,String>();
    params.put(SENDER_PARAM, senderRemoteId);
    params.put(RECEIVER_PARAM, receiverRemoteId);
    params.put(RELATIONSHIP_UUID_PARAM, relationship.getId());
    return params;
  }
  
  private IdentityStorage getIdentityStorage() {
    return (IdentityStorage) PortalContainer.getInstance().getComponentInstanceOfType(IdentityStorage.class);
  }
  
  private RelationshipManager getRelationShipManager() {
    return (RelationshipManager) PortalContainer.getInstance().getComponentInstanceOfType(RelationshipManager.class);
  }
}