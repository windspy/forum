/*
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.forum.service;

import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class CalculateModeratorEventListener implements EventListener {
  private String path_;

  private String workspace_;

  private String repository_;

  private Log    log = ExoLogger.getLogger(CalculateModeratorEventListener.class);

  public CalculateModeratorEventListener() throws Exception {
  }

  public String getSrcWorkspace() {
    return workspace_;
  }

  public String getRepository() {
    return repository_;
  }

  public String getPath() {
    return path_;
  }

  public void setPath(String path) {
    path_ = path;
  }

  public void onEvent(EventIterator evIter) {

    try {
      ExoContainer container = ExoContainerContext.getCurrentContainer();
      ForumService forumService = (ForumService) container.getComponentInstanceOfType(ForumService.class);
      while (evIter.hasNext()) {
        Event ev = evIter.nextEvent();
        if (ev.getType() == Event.PROPERTY_ADDED) {
          String evPath = ev.getPath();
          if (evPath.substring(evPath.lastIndexOf("/") + 1).equals("exo:moderators")) {
            forumService.calculateModerator(path_, true);
          }
          // exo:moderators
        } else if (ev.getType() == Event.PROPERTY_CHANGED) {
          String evPath = ev.getPath();
          if (evPath.substring(evPath.lastIndexOf("/") + 1).equals("exo:moderators")) {
            forumService.calculateModerator(path_, false);
          }
          // exo:moderators, tempModerators
        } else if (ev.getType() == Event.PROPERTY_REMOVED) {
          String evPath = ev.getPath();
          if (evPath.substring(evPath.lastIndexOf("/") + 1).equals("exo:moderators")) {
            forumService.calculateModerator(path_, false);
          }
          // exo:moderators, tempModerators
        }
      }
    } catch (Exception e) {
      log.warn("Failed to calculate moderators");
    }
  }
}
