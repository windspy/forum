/*
 * Copyright (C) 2003-2008 eXo Platform SAS.
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
package org.exoplatform.forum.webui.popup;

import java.util.ArrayList;
import java.util.List;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.forum.ForumUtils;
import org.exoplatform.forum.common.webui.UIPopupAction;
import org.exoplatform.forum.common.webui.UIPopupContainer;
import org.exoplatform.forum.service.Category;
import org.exoplatform.forum.service.Forum;
import org.exoplatform.forum.service.ForumService;
import org.exoplatform.forum.service.JCRPageList;
import org.exoplatform.forum.service.Post;
import org.exoplatform.forum.service.Topic;
import org.exoplatform.forum.service.UserProfile;
import org.exoplatform.forum.service.Utils;
import org.exoplatform.forum.webui.UIForumContainer;
import org.exoplatform.forum.webui.UIForumDescription;
import org.exoplatform.forum.webui.UIForumPageIterator;
import org.exoplatform.forum.webui.UIForumPortlet;
import org.exoplatform.forum.webui.UITopicDetail;
import org.exoplatform.forum.webui.UITopicDetailContainer;
import org.exoplatform.forum.webui.UITopicPoll;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;

@ComponentConfig(
    template = "app:/templates/forum/webui/popup/UIPageListPostByUser.gtmpl",
    events = {
      @EventConfig(listeners = UIPageListPostByUser.OpenPostLinkActionListener.class),
      @EventConfig(listeners = UIPageListPostByUser.OpenTopicLinkActionListener.class),
      @EventConfig(listeners = UIPageListPostByUser.SetOrderByActionListener.class),
      @EventConfig(listeners = UIPageListPostByUser.DeletePostLinkActionListener.class, confirm="UITopicDetail.confirm.DeleteThisPost")
    }
)
public class UIPageListPostByUser extends UIContainer {
  private ForumService forumService;

  private String       userName           = ForumUtils.EMPTY_STR;

  private String       strOrderBy         = Utils.EXO_CREATED_DATE.concat(Utils.DESCENDING);

  private boolean      hasEnableIPLogging = true;

  private List<Post>   posts              = new ArrayList<Post>();

  private Log          log                = ExoLogger.getLogger(UIPageListPostByUser.class);

  public UIPageListPostByUser() throws Exception {
    forumService = (ForumService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ForumService.class);
    this.userName = null;
    addChild(UIForumPageIterator.class, null, "PageListPostByUser");
  }

  public boolean getHasEnableIPLogging() {
    return hasEnableIPLogging;
  }

  private UserProfile getUserProfile() {
    UIForumPortlet forumPortlet = this.getAncestorOfType(UIForumPortlet.class);
    hasEnableIPLogging = forumPortlet.isEnableIPLogging();
    return forumPortlet.getUserProfile();
  }

  public void setUserName(String userId) {
    this.userName = userId;
    strOrderBy = Utils.EXO_CREATED_DATE.concat(Utils.DESCENDING);
  }

  @SuppressWarnings("unchecked")
  protected List<Post> getPostsByUser() throws Exception {
    UIForumPageIterator forumPageIterator = this.getChild(UIForumPageIterator.class);
    List<Post> posts = null;
    try {
      UserProfile userProfile = getUserProfile();
      boolean isMod = (userProfile.getUserRole() < 2) ? true : false;
      JCRPageList pageList = forumService.getPagePostByUser(this.userName, userProfile.getUserId(), isMod, strOrderBy);
      forumPageIterator.updatePageList(pageList);
      if (pageList != null) {
        pageList.setPageSize(10);
      }
      posts = pageList.getPage(forumPageIterator.getPageSelected());
      forumPageIterator.setSelectPage(pageList.getCurrentPage());
    } catch (Exception e) {
      log.trace("\nThe post must exist: " + e.getMessage() + "\n" + e.getCause());
    }
    if (posts == null) {
      posts = new ArrayList<Post>();
    }
    this.posts = posts;
    return posts;
  }

  private Post getPostById(String postId) throws Exception {
    for (Post post : this.posts) {
      if (post.getId().equals(postId))
        return post;
    }
    Post post = (Post) forumService.getObjectNameById(postId, Utils.POST);
    return post;
  }

  static public class OpenPostLinkActionListener extends EventListener<UIPageListPostByUser> {
    public void execute(Event<UIPageListPostByUser> event) throws Exception {
      UIPageListPostByUser uiForm = event.getSource();
      String postId = event.getRequestContext().getRequestParameter(OBJECTID);
      Post post = uiForm.getPostById(postId);
      if (post == null) {
        event.getRequestContext().getUIApplication().addMessage(new ApplicationMessage("UIShowBookMarkForm.msg.link-not-found",
                                                                                       null,
                                                                                       ApplicationMessage.WARNING));
        return;
      }
      boolean isRead = true;
      String[] id = post.getPath().split(ForumUtils.SLASH);
      int l = id.length;
      String categoryId = id[l - 4], forumId = id[l - 3], topicId = id[l - 2];
      UIForumPortlet forumPortlet = uiForm.getAncestorOfType(UIForumPortlet.class);
      Forum forum = uiForm.forumService.getForum(categoryId, forumId);
      Topic topic = uiForm.forumService.getTopic(categoryId, forumId, topicId, ForumUtils.EMPTY_STR);
      if (forumPortlet.getUserProfile().getUserRole() > 0) {
        Category cate = uiForm.forumService.getCategory(categoryId);
        isRead = forumPortlet.checkCanView(cate, forum, topic);
      }
      if (isRead) {
        UIPopupContainer popupContainer = uiForm.getAncestorOfType(UIPopupContainer.class);
        UIPopupAction popupAction = popupContainer.getChild(UIPopupAction.class).setRendered(true);
        UIViewPost viewPost = popupAction.activate(UIViewPost.class, 700);
        viewPost.setPostView(post);
        viewPost.setViewUserInfo(false);
        viewPost.setActionForm(new String[] { "Close", "OpenTopicLink" });
        event.getRequestContext().addUIComponentToUpdateByAjax(popupAction);
      } else {
        event.getRequestContext().getUIApplication().addMessage(new ApplicationMessage("UIForumPortlet.msg.do-not-permission",
                                                                                       null,
                                                                                       ApplicationMessage.WARNING));
        return;
      }
    }
  }

  static public class OpenTopicLinkActionListener extends EventListener<UIPageListPostByUser> {
    public void execute(Event<UIPageListPostByUser> event) throws Exception {
      UIPageListPostByUser uiForm = event.getSource();
      String postId = event.getRequestContext().getRequestParameter(OBJECTID);
      Post post = uiForm.getPostById(postId);
      if (post == null) {
        event.getRequestContext().getUIApplication().addMessage(new ApplicationMessage("UIShowBookMarkForm.msg.link-not-found",
                                                                                       null,
                                                                                       ApplicationMessage.WARNING));
        return;
      }
      boolean isRead = true;
      String[] id = post.getPath().split(ForumUtils.SLASH);
      int l = id.length;
      String categoryId = id[l - 4], forumId = id[l - 3], topicId = id[l - 2];
      UIForumPortlet forumPortlet = uiForm.getAncestorOfType(UIForumPortlet.class);
      Forum forum = uiForm.forumService.getForum(categoryId, forumId);
      Topic topic = uiForm.forumService.getTopic(categoryId, forumId, topicId, ForumUtils.EMPTY_STR);
      if (forumPortlet.getUserProfile().getUserRole() > 0) {
        Category cate = uiForm.forumService.getCategory(categoryId);
        isRead = forumPortlet.checkCanView(cate, forum, topic);
      }
      if (isRead) {
        forumPortlet.updateIsRendered(ForumUtils.FORUM);
        UIForumContainer uiForumContainer = forumPortlet.getChild(UIForumContainer.class);
        UITopicDetailContainer uiTopicDetailContainer = uiForumContainer.getChild(UITopicDetailContainer.class);
        uiForumContainer.setIsRenderChild(false);
        UITopicDetail uiTopicDetail = uiTopicDetailContainer.getChild(UITopicDetail.class);
        uiForumContainer.getChild(UIForumDescription.class).setForum(forum);
        uiTopicDetail.setUpdateForum(forum);
        uiTopicDetail.setUpdateTopic(categoryId, forumId, topicId);
        uiTopicDetail.setIdPostView(postId);
        uiTopicDetail.setLastPostId(postId);
        uiTopicDetailContainer.getChild(UITopicPoll.class).updateFormPoll(categoryId, forumId, topicId);
        forumPortlet.cancelAction();
        event.getRequestContext().addUIComponentToUpdateByAjax(forumPortlet);
      } else {
        String[] s = new String[] {};
        event.getRequestContext().getUIApplication().addMessage(new ApplicationMessage("UIForumPortlet.msg.do-not-permission",
                                                                                       s,
                                                                                       ApplicationMessage.WARNING));
        return;
      }
    }
  }

  static public class DeletePostLinkActionListener extends EventListener<UIPageListPostByUser> {
    public void execute(Event<UIPageListPostByUser> event) throws Exception {
      UIPageListPostByUser uiForm = event.getSource();
      String postId = event.getRequestContext().getRequestParameter(OBJECTID);
      Post post = uiForm.getPostById(postId);
      String[] path = post.getPath().split(ForumUtils.SLASH);
      int length = path.length;
      String topicId = path[length - 2];
      String forumId = path[length - 3];
      String categoryId = path[length - 4];
      uiForm.forumService.removePost(categoryId, forumId, topicId, postId);
      event.getRequestContext().addUIComponentToUpdateByAjax(uiForm);
    }
  }

  static public class SetOrderByActionListener extends EventListener<UIPageListPostByUser> {
    public void execute(Event<UIPageListPostByUser> event) throws Exception {
      UIPageListPostByUser uiContainer = event.getSource();
      String path = event.getRequestContext().getRequestParameter(OBJECTID);
      uiContainer.strOrderBy = ForumUtils.getOrderBy(uiContainer.strOrderBy, path);
      event.getRequestContext().addUIComponentToUpdateByAjax(uiContainer);
    }
  }

}
